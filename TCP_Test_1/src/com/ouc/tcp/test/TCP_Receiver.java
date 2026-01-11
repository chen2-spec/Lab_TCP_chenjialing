package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress; // 导入InetAddress
import java.util.Timer;      // 导入Timer
import java.util.TimerTask;  // 导入TimerTask

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack; // 回复的ACK报文段
    private int expectedSequence = 0; // 用于记录期望收到的seq

    // 【新增】用于保存发送方地址，以便Timer任务使用
    private InetAddress lastSenderAddr;
    // 【新增】延迟确认计时器
    private Timer ackTimer;

    /*构造函数*/
    public TCP_Receiver() {
        super();
        super.initTCP_Receiver(this);
    }

    @Override
    public void rdt_recv(TCP_PACKET recvPack) {
        // 检查校验和
        if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {

            // 更新发送方地址
            lastSenderAddr = recvPack.getSourceAddr();

            int currentSequence = (recvPack.getTcpH().getTh_seq() - 1) / 100; // 计算当前包的seq

            // 1. 收到期望的有序包
            if (expectedSequence == currentSequence) {
                // 存入数据
                dataQueue.add(recvPack.getTcpS().getData());
                expectedSequence++; // 期望序号加1
                // 实现累计确认（延迟发送）
                // 如果当前没有计时器在运行，则启动一个500ms的计时器
                // 如果已有计时器，说明正在等待合并ACK，不做操作，继续积累
                if (ackTimer == null) {
                    ackTimer = new Timer();
                    ackTimer.schedule(new AckTask(), 500); // 延迟500ms发送
                }

            }
            // 2. 收到乱序包（失序）
            else {
                // 发生乱序，通常意味着丢包或网络乱序
                // 这样发送方才能通过快速重传（Fast Retransmit）机制尽快重传
                if (ackTimer != null) {
                    ackTimer.cancel(); // 取消正在等待的累计确认，立刻响应当前状态
                    ackTimer = null;
                }
                // 立即发送 ACK：确认号为 expectedSequence - 1 (即最后一个按序到达的包)
                sendACK((expectedSequence - 1) * 100 + 1);
            }
        }

        // 交付数据策略（保持不变）
        if (dataQueue.size() >= 20)
            deliver_data();
    }

    // 【新增】封装发送ACK的辅助方法
    public void sendACK(int ackSeq) {
        if (lastSenderAddr == null) return;

        tcpH.setTh_ack(ackSeq);
        // 使用保存的地址 lastSenderAddr
        ackPack = new TCP_PACKET(tcpH, tcpS, lastSenderAddr);
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
        reply(ackPack);
    }

    // 【新增】计时器任务：超时发送累计ACK
    class AckTask extends TimerTask {
        @Override
        public void run() {
            // 计时器超时，说明500ms内没有新包到达或时间到了
            // 发送累计确认 ACK，确认号为当前期望的 expectedSequence - 1
            // (注意：ACK确认的是收到的最后一个字节流号)
            sendACK((expectedSequence - 1) * 100 + 1);

            // 任务执行完后，清空计时器引用
            ackTimer = null;
        }
    }

    @Override
    public void deliver_data() {
        File fw = new File("recvData.txt");
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(fw, true));
            while (!dataQueue.isEmpty()) {
                int[] data = dataQueue.poll();
                for (int i = 0; i < data.length; i++) {
                    writer.write(data[i] + "\n");
                }
                writer.flush();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reply(TCP_PACKET replyPack) {
        tcpH.setTh_eflag((byte) 7);
        client.send(replyPack);
    }
}