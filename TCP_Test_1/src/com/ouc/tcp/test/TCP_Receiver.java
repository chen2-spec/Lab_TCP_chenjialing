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

            // 2. 检查序号：是新包还是重复包？
            if (currentSeq == expectedSeq) {
                // --- 情况 A: 是我想要的新包 ---

                // 交付数据
                dataQueue.add(recvPack.getTcpS().getData());
                // 更新期望序号 (当前序号 + 数据长度)
                // 注意：你的实验中 appData 固定 100 个 int，虽然是 int 但这里模拟字节流步长
                // 根据日志，步长是 100 (1 -> 101 -> 201)
                // 计算当前包的数据长度（以防止变长）
                int dataLen = recvPack.getTcpS().getData().length;
                if (dataLen == 0) dataLen = 1; // 防止空包死循环，但在本实验中通常是 100
                expectedSeq += dataLen; // 移动窗口，准备收下一个

                System.out.println("RDT 2.1 Receiver: Accepted new packet seq " + currentSeq);

                // 累积写入文件
                if (dataQueue.size() >= 20) deliver_data();

            } else {
                // --- 情况 B: 是重复包 (Duplicate) ---
                // 说明刚才我发的 ACK 丢了或坏了，Sender 又发了一遍
                System.out.println("RDT 2.1 Receiver: Detected Duplicate packet seq " + currentSeq + ", expected " + expectedSeq);
                // 动作：丢弃数据（不 add 到 dataQueue），但必须重发 ACK
            }

            // 3. 无论新包还是旧包，只要校验通过，都要回复 ACK
            // 注意：ACK 的确认号应该是“我收到的这个包的序号” (或者 expectedSeq，取决于协议细节，这里建议回 currentSeq 以确认收到)
            tcpH.setTh_ack(currentSeq);
            ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
            reply(ackPack);

        } else {
            // --- 情况 C: 包坏了 (校验失败) ---
            System.out.println("RDT 2.1 Receiver: Checksum Error! Sending NAK.");

            tcpH.setTh_ack(-1); // NAK
            ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
            reply(ackPack);
        }

        System.out.println();


		
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	//回复ACK报文段
	public void reply(TCP_PACKET replyPack) {
		//设置错误控制标志
		tcpH.setTh_eflag((byte)0);	//eFlag=0，信道无错误
				
		//发送数据报
		client.send(replyPack);
	}
	
}
