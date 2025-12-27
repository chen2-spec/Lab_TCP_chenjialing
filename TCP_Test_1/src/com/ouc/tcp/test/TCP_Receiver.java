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
	int sequence=1;//用于记录当前待接收的包序号，注意包序号不完全是
    //int last_sequence = -1; // 用于记录上一次收到包的序号
    private int expectedSeq = 1;//// 用于区分“新数据包”和“重传的重复包”

    /*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
		//第一层检查：校验和
        //检查校验码（重新计算数据包的校验和==获取数据包中存储的校验和），生成ACK
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
            int currentSeq = recvPack.getTcpH().getTh_seq() ; // 当前包的seq
            //第二层检查：序列号(检查这是新包还是发送方因为没收到 ACK 而重发的旧包)
            if (currentSeq == expectedSeq) {// 是想要的新包
                System.out.println("RDT 2.1 Receiver: Accepted new packet seq " + currentSeq);
                // 1. 将数据加入接收队列
                dataQueue.add(recvPack.getTcpS().getData());
                // 2. 更新期望序号 (窗口滑动)
                // 数据固定长度为 100，所以期望下一个是 当前+100
                expectedSeq += 100;
                // 3. 累计满20组数据写入一次文件
                if(dataQueue.size() >= 20) deliver_data();
                // 4. 准备回复 ACK，确认号为当前收到的序号
                tcpH.setTh_ack(currentSeq);
            } else {//是重复包
                // 原因：刚才回复的 ACK 丢了或坏了，发送方误以为失败于是重传了
                // 动作：丢弃数据（绝对不能再次 add 到 dataQueue，否则文件内容会重复）
                // 补救：必须重发 ACK，告诉发送方“我确实收到了，请发下一个”
                System.out.println("RDT 2.1 Receiver: Duplicate packet detected seq "
                        + currentSeq + ", resending ACK.");
                // 设置确认号为当前收到的这个重复包的序号
                tcpH.setTh_ack(currentSeq);
            }
            // 发送 ACK 包
            ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            tcpH.setTh_sum(CheckSum.computeChkSum(ackPack)); // 计算 ACK 包的校验和
            reply(ackPack);
            //将接收到的正确有序的数据插入data队列，准备交付
			//dataQueue.add(recvPack.getTcpS().getData());
			//sequence++;
		}else{//校验和错误，说明数据损坏
            System.out.println("RDT 2.1 Receiver: Checksum Error! Sending NAK.");
			tcpH.setTh_ack(-1);//设置ACK号为-1，表示NACK（否定确认）
            //创建并发送NACK包
			ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
			tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
			//回复ACK报文段
			reply(ackPack);
		}
		System.out.println();
		//交付数据（每20组数据交付一次）
		//if(dataQueue.size() == 20)
		//	deliver_data();
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
		tcpH.setTh_eflag((byte)1);	//eFlag=1，信道只出错

        //发送数据报
		client.send(replyPack);
	}
	
}
