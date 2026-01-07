package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

public class SenderSlidingWindow {
    private Client client;  // 客户端
    private int size = 16;  // 窗口大小
    private int base = 0;  // 窗口左值
    private int nextIndex = 0;  // 下一个包的存放位置

    public int cwnd = 1;  // 拥塞窗口，初始值为1
    private volatile int ssthresh = 16;  // 慢开始门限，初始值为16
    private int CongestionAvoidanceCount = 0;  // 记录进入拥塞避免状态时收到的ACK数
    //窗口与计时器
    private Hashtable<Integer, TCP_PACKET> packets = new Hashtable<>();  // 存储发方窗口的包
    private Timer timer;  //唯一的重传计时器（对应窗口最左边的包）
    // 重复ACK检测（用于快重传）
    private int lastACKSequence = -1;  // 上一次收到的ACK的包的seq
    private int lastACKSequenceCount = 0;  // 收到重复ACK的次数

    private int RENO_FLAG = 1;
    //这三个数组用于记录每次ACK到达时cwnd和ssthresh的变化，最后打印出来用于做实验报告的图表
    private int[] cwndChangeList = new int[1000];  // 用于记录 cwnd 变化
    private int cwndChangeListIndex = 0; // 记录数组下标
    private int[] ssthreshChangeList = new int[1000];  // 用于记录 ssthresh 变化
    private int ssthreshChangeListIndex = 0;  // 记录数组下标
    private int[] ackChangeList = new int[1000]; // 用于记录收到ack的序列号的变化
    private int ackChangeListIndex = 0;  // 记录数组下标
    private int lastCwnd = 1;  // 用于记录上一次的cwnd
    private int lastSsthresh = 16;  // y用于记录上一次的ssthresh

    public int getSsthresh() {return ssthresh;}

    public void setSsthresh(int ssthresh) {this.ssthresh = ssthresh;}

    public int getCwnd() {return cwnd;}

    public void setCwnd(int cwnd) {this.cwnd = cwnd;}

    public Hashtable<Integer, TCP_PACKET> getPackets() {return packets;}

    public int getLastACKSequence() {return lastACKSequence;}

    public void setCongestionAvoidanceCount(int CongestionAvoidanceCount) {this.CongestionAvoidanceCount = CongestionAvoidanceCount;}

   /*构造函数*/
    public SenderSlidingWindow(Client client) {
        this.client = client;
    }

    /*判断窗口是否已满*/
    public boolean isFull() {
        // 核心流控逻辑：如果当前未确认的包数量 >= 拥塞窗口大小，则停止发送
        return cwnd <= packets.size();
    }

    /*向窗口中加入包*/
    public void putPacket(TCP_PACKET packet) {
        // 计算当前包的简化序号（例如把 101, 201 变成 1, 2）
        int currentSequence = ((packet.getTcpH().getTh_seq() - 1) / 100);
        // 计时器逻辑：如果窗口是空的，说明这是当前窗口的第一个包，启动计时器
        // TCP一般只对窗口最左沿（Base）的包计时
        if (packets.isEmpty()) {
            // 窗口左沿则开始计时器
            timer = new Timer();
            //1秒后超时，执行 RetransmitTask
            timer.schedule(new RetransmitTask(client, this), 1000, 1000);
        }
        packets.put(currentSequence, packet);

        // 如果是最后一个分组，则输出记录数组
        if (currentSequence == 999) {
            System.out.println("***** Change List Show *****");
            System.out.println(Arrays.toString(cwndChangeList));
            System.out.println(Arrays.toString(ssthreshChangeList));
            System.out.println(Arrays.toString(ackChangeList));
            System.out.println("***** Show End *****");
        } else if (currentSequence == 0) {
            cwndChangeList[cwndChangeListIndex ++] = cwnd;
            ssthreshChangeList[ssthreshChangeListIndex ++] = ssthresh;
            ackChangeList[ackChangeListIndex ++] = currentSequence;
        }
    }

    /*在 cwnd ssthresh 和 ack 变化列表中加入新值*/
    public void appendChange(int currentSequence) {
        cwndChangeList[cwndChangeListIndex ++] = cwnd;
        ssthreshChangeList[ssthreshChangeListIndex ++] = ssthresh;
        ackChangeList[ackChangeListIndex ++] = currentSequence;
    }

    /*接收到ACK*/
    public void receiveACK(int currentSequence) {
        // 收到重复ACK（快重传/快恢复）
        if (currentSequence == lastACKSequence) {
            lastACKSequenceCount++;
            if (lastACKSequenceCount == 4) {  // 三个重复ACK，执行快重传
                if (packets.containsKey(currentSequence + 1)) {
                    // 快重传
                    System.out.println("***** Fast Retransmit *****");
                    TCP_PACKET packet = packets.get(currentSequence + 1);
                    client.send(packet);
                    // 重置计时器
                    timer.cancel();
                    timer = new Timer();
                    timer.schedule(new RetransmitTask(client, this), 1000, 1000);
                }

                // 快恢复
                if (RENO_FLAG == 1) {
                    /*TCP Reno版本*/
                    System.out.println("***** Fast Recovery *****");
                    // 乘法减小：门限 ssthresh 降为当前窗口的一半
                    if (cwnd / 2 < 2) {
                        System.out.println("ssthresh: " + ssthresh + " ---> 2");
                        ssthresh = 2;  // ssthresh 不得小于2
                    } else {
                        System.out.println("ssthresh: " + ssthresh + " ---> " + cwnd / 2);
                        ssthresh = cwnd / 2;  // 慢开始门限变为 cwnd

                    }
                    // 关键点：Reno将窗口 cwnd 设置为新的 ssthresh（即减半），而不是置1
                    // 这意味着它跳过了慢启动，直接进入拥塞避免
                    System.out.println("cwnd: " + cwnd + " ---> " + ssthresh);
                    cwnd = ssthresh;  // cwnd 置为ssthresh
                    CongestionAvoidanceCount = 0;
                } else {
                    /*TCP Tahoe版本*/
                    // 乘法减小：ssthresh 依然减半
                    if (cwnd / 2 < 2) {
                        System.out.println("ssthresh: " + ssthresh + " ---> 2");
                        ssthresh = 2;  // ssthresh 不得小于2
                    } else {
                        System.out.println("ssthresh: " + ssthresh + " ---> " + cwnd / 2);
                        ssthresh = cwnd / 2;  // 慢开始门限变为 cwnd 的一半

                    }
                    System.out.println("cwnd: " + cwnd + " ---> 1");
                    cwnd = 1;  // cwnd 置为1
                    CongestionAvoidanceCount = 0;
                }
                appendChange(currentSequence);
            }
        } else {//收到新的ACK（不是重复的）
            // 清空计时器
            timer.cancel();

            // 收到新的ACK， 滑动窗口：清除缓冲区里已经被确认的包
            for (int i = lastACKSequence + 1; i <= currentSequence; i++) {  // 清除前面的包
                packets.remove(i);
            }
            // 更新 ACK 记录
            lastACKSequence = currentSequence;  // 重置lastACKSequence为当前收到ACK的包的seq
            lastACKSequenceCount = 1;  // 重置lastACKCount为1

            // 如果窗口中仍有分组，则重开计时器
            if (!packets.isEmpty()) {
                timer = new Timer();
                timer.schedule(new RetransmitTask(client, this), 1000, 1000);
            }
            // 3. 拥塞窗口调整
            if (cwnd < ssthresh) {
                // 慢启动，条件：当前窗口 < 门限
                System.out.println("***** Slow Start *****");
                System.out.println("cwnd: " + cwnd + " ---> " + (cwnd + 1));
                // 行为：指数增长。每收到一个ACK，窗口+1。
                // (原理：发送N个包，回来N个ACK，窗口就变成 N+N = 2N，翻倍)
                cwnd ++;  // 每收到一个ACK， cwnd加一
                appendChange(currentSequence);
            } else {
                // 拥塞避免
                // 行为：线性增长。也就是加法增大
                // 每经过一个 RTT（往返时间），窗口才 +1。
                // 代码实现：必须攒够 cwnd 个 ACK，窗口才 +1。
                CongestionAvoidanceCount ++;
                System.out.println("***** Congestion Avoidance *****");
                System.out.println("cwnd: " + cwnd + " NO. " + CongestionAvoidanceCount);
                if (CongestionAvoidanceCount == cwnd) {  // 收到ACK数量达到 cwnd 大小时
                    CongestionAvoidanceCount = 0;  // 重置计数器
                    System.out.println("cwnd: " + cwnd + " ---> " + (cwnd + 1));
                    cwnd ++;  // cwnd 加一
                    appendChange(currentSequence);
                }
            }


        }


    }
}
