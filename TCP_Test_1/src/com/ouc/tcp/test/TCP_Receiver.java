/***************************2.1: ACK/NACK*****************/
/***** Feng Hong; 2015-12-09******************************/
package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {
	
	private TCP_PACKET ackPack;	//回复的ACK报文段
    // RDT 2.1: 记录期望收到的序列号，初始为 1
    private int expectedSeq = 1;
		
	/*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
        // 1. 检查校验和
        if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
            // 校验通过，包是完好的
            int currentSeq = recvPack.getTcpH().getTh_seq();
            // 2. 检查序号是不是想要的
            if (currentSeq == expectedSeq) { // 是想要的
                System.out.println("RDT 3.0 Receiver: Accepted packet " + currentSeq);
                // 交付数据
                dataQueue.add(recvPack.getTcpS().getData());
                // 发送当前包的 ACK
                tcpH.setTh_ack(currentSeq);
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack)); // 给 ACK 加校验和
                reply(ackPack);
                // 移动窗口，期待下一个包
                expectedSeq += 100;
                //交付数据
                if(dataQueue.size() >= 20) deliver_data();

            } else {// 收到重复包，不是想要的序号
                System.out.println("RDT 3.0 Receiver: Duplicate packet " + currentSeq + ". Resending ACK.");
                // 触发 Sender 的 Clear 逻辑
                tcpH.setTh_ack(currentSeq);
                ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
                tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
                reply(ackPack);
            }
        } else { //校验和出错 (包损坏) ---

            System.out.println("RDT 3.0 Receiver: Corrupted packet. Ignored.");

        }

		//交付数据（每20组数据交付一次）
		if(dataQueue.size() == 20)
		deliver_data();
	}

	@Override
	//交付数据（将数据写入文件）；不需要修改
	public void deliver_data() {
		//检查dataQueue，将数据写入文件
		File fw = new File("recvData.txt");
		BufferedWriter writer;
		
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			
			//循环检查data队列中是否有新交付数据
			while(!dataQueue.isEmpty()) {
				int[] data = dataQueue.poll();
				
				//将数据写入文件
				for(int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				
				writer.flush();		//清空输出缓存
			}
			writer.close();
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	@Override
	//回复ACK报文段
	public void reply(TCP_PACKET replyPack) {
		//设置错误控制标志
		tcpH.setTh_eflag((byte)2);
				
		//发送数据报
		client.send(replyPack);
	}
	
}
