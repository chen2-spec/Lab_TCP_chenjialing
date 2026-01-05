package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.TimerTask;

public class TaskPacketsRetransmit extends TimerTask {

    private Client senderClient;  // 客户端对象引用，用于调用发送方法
    private TCP_PACKET[] packets;  // 存储需要重传的数据包数组

    /*构造函数*/
    public TaskPacketsRetransmit(Client client, TCP_PACKET[] packets) {
        super();
        this.senderClient = client;
        this.packets = packets;
    }


    @Override
    public void run() {//计时器到期时自动调用
        for (int i = 0; i < packets.length; i ++ )
        {
            if (packets[i] == null) {  // 如果没有包则跳出循环
                break;
            } else {  // 逐一递交各个包
                senderClient.send(packets[i]);// 调用客户端的send方法重传该包
            }
        }
    }
}
