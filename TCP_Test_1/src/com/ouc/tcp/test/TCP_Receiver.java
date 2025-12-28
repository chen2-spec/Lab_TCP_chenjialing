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
		
	/*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
		//检查校验码（重新计算数据包的校验和==获取数据包中存储的校验和），生成ACK
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			//生成ACK报文段（设置确认号）
            //设置ACK号为接收到的数据包序号
			tcpH.setTh_ack(recvPack.getTcpH().getTh_seq());
            //创建ACK数据包，目标地址为数据包源地址
			ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
            //为ACK包计算校验和
			tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
			//回复ACK报文段
			reply(ackPack);			
			
			//将接收到的正确有序的数据插入data队列，准备交付
			dataQueue.add(recvPack.getTcpS().getData());				
			sequence++;
		}else{//校验和错误，发送NACK
            //计算出的校验和
			System.out.println("Recieve Computed: "+CheckSum.computeChkSum(recvPack));
			//接收的校验和
            System.out.println("Recieved Packet:"+recvPack.getTcpH().getTh_sum());
			//打印出问题的包序号和当前期望序号
            System.out.println("Problem: Packet Number: "+recvPack.getTcpH().getTh_seq()+
                    " + InnerSeq:  "+sequence);
			tcpH.setTh_ack(-1);//设置ACK号为-1，表示NACK（否定确认）
            //创建并发送NACK包
			ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
			tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
			//回复ACK报文段
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
		tcpH.setTh_eflag((byte)1);	//eFlag=1，信道只出错

        //发送数据报
		client.send(replyPack);
	}
	
}
