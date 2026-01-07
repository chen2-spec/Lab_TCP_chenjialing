package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

public class TCP_Receiver extends TCP_Receiver_ADT {

    private TCP_PACKET ackPack;	//回复的ACK报文段
    // private int sequence=1; // 这个变量似乎没用，建议注释掉
    // private int last_sequence = -1; // 这个变量似乎也没用
    // 【新增】用于保存发送方的地址
    private InetAddress senderAddr;
    private int expectedSequence = 0;  // 用于记录期望收到的seq
    private Hashtable<Integer, TCP_PACKET> storagePackets = new Hashtable<>(); // 用于缓存失序分组

    // 【新增】延迟确认计时器
    private Timer ackTimer;

    /*构造函数*/
    public TCP_Receiver() {
        super();
        super.initTCP_Receiver(this);
    }

    @Override
    //接收到数据报：检查校验和，设置回复的ACK报文段
    public void rdt_recv(TCP_PACKET recvPack) {
        //检查校验码
        if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
            // 【新增】保存发送方地址！重要！
            this.senderAddr = recvPack.getSourceAddr();
            int currentSequence = (recvPack.getTcpH().getTh_seq() - 1) / 100;

            // --- 分支1：收到期望的有序包 ---
            if (expectedSequence == currentSequence) {
                // 1. 存入数据
                dataQueue.add(recvPack.getTcpS().getData());
                expectedSequence += 1 ;

                // 2. 处理缓存中能接上的包
                while (storagePackets.containsKey(expectedSequence)) {
                    dataQueue.add(storagePackets.get(expectedSequence).getTcpS().getData());
                    storagePackets.remove(expectedSequence); // 别忘了移除
                    expectedSequence += 1;
                }

                // 3. 交付数据
                if(dataQueue.size() >= 20 || (currentSequence >= 899 && currentSequence <= 999))
                    deliver_data();

                // 【核心修改】延迟确认逻辑
                // 如果是正常有序的包，不立即回复，而是启动计时器等待
                if (ackTimer == null) {
                    ackTimer = new Timer();
                    // 500ms 后发送确认
                    ackTimer.schedule(new AckTask(), 500);
                }
                // 如果 ackTimer 已经在跑了，就什么都不做，让它继续跑（累积确认）

            }
            // --- 分支2：收到乱序包（说明中间丢包了） ---
            else {
                // 缓存失序分组
                if (!storagePackets.containsKey(currentSequence) && currentSequence > expectedSequence) {
                    storagePackets.put(currentSequence, recvPack);
                }

                // 【核心修改】快重传触发逻辑
                // 遇到乱序包，必须 *立刻* 发送重复 ACK，不能延迟！
                // 否则发送方无法及时收到3个重复ACK来触发快重传
                System.out.println("乱序到达，立即发送重复ACK: " + expectedSequence);
                sendACK();
            }

        } else {
            // 校验和错误，可以选择忽略，或者立即发一个当前的ACK（通常忽略即可）
        }
    }

    // 【新增】将发送ACK的逻辑提取出来
    public void sendACK() {
        // 如果有正在运行的延迟计时器，既然我们要立即发ACK了，就把它取消掉
        if (ackTimer != null) {
            ackTimer.cancel();
            ackTimer = null;
        }

        // 生成ACK报文段（设置确认号）
        tcpH.setTh_ack((expectedSequence - 1) * 100 + 1);
        ackPack = new TCP_PACKET(tcpH, tcpS, this.senderAddr); // 目的地址设为null或者源地址，ADT会自动处理
        tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));

        // 调用父类方法发送
        reply(ackPack);
    }

    // 【新增】计时器任务类
    class AckTask extends TimerTask {
        @Override
        public void run() {
            System.out.println("延迟确认计时器超时，发送累积ACK: " + expectedSequence);
            sendACK();
        }
    }

    @Override
    //交付数据（将数据写入文件）；不需要修改
    public void deliver_data() {
        File fw = new File("recvData.txt");
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(fw, true));
            while(!dataQueue.isEmpty()) {
                int[] data = dataQueue.poll();
                for(int i = 0; i < data.length; i++) {
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
    //回复ACK报文段
    public void reply(TCP_PACKET replyPack) {
        tcpH.setTh_eflag((byte)7);
        client.send(replyPack);
    }
}