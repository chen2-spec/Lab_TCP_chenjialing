package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ReceiverSlidingWindow {
    private Client client;
    // 使用 LinkedList 来存储还没交付的 TCP_PACKET
    private LinkedList<TCP_PACKET> packets = new LinkedList<>();
    private int expectedSequence = 0;
    Queue<int[]> dataQueue = new LinkedBlockingQueue();
    // 交付队列：存放已经排序好、准备写入文件的数据
    public ReceiverSlidingWindow(Client client) {
        this.client = client;
    }

    public int receivePacket(TCP_PACKET packet) {
        // TCP header 里的 seq 是字节流序号 (1, 101, 201...)，这里除以100并减1转为包的索引。
        int currentSequence = (packet.getTcpH().getTh_seq() - 1) / 100;
        // 判断：这个包是不是需要的？
        if (currentSequence >= this.expectedSequence) {
            // 如果 currentSequence >= expectedSequence，说明是新包（可能是期望的，也可能是乱序的），需要处理。
            putPacket(packet);
        }
        // 如果 currentSequence < expectedSequence，说明是旧包（重复的），直接忽略不存。
        slid();

        return this.expectedSequence - 1;
    }
    /* 将包插入到缓存链表的正确位置 */
    private void putPacket(TCP_PACKET packet) {
        int currentSequence = (packet.getTcpH().getTh_seq() - 1) / 100;
        // 寻找插入位置：遍历链表
        int index = 0;
        // 循环条件：
        // 1. index 没越界
        // 2. 当前要插的包序号 > 链表里该位置包的序号
        // 如果手里的包比当前位置的包大，就继续往后找
        while (index < this.packets.size() && currentSequence > (this.packets.get(index).getTcpH().getTh_seq() - 1) / 100) {
            index++;
        }
        // 插入逻辑（包含去重）：
        // 情况A (index == size): 找了一圈发现最大，插在末尾。
        // 情况B (currentSequence != ...): 找到了位置，并且该位置的包序号不等于我（说明没有重复收到）。
        // 如果等于了，说明包重复了，就不执行 add，直接丢弃
        if (index == this.packets.size() || currentSequence != (this.packets.get(index).getTcpH().getTh_seq() - 1) / 100) {
            this.packets.add(index, packet);
        }
    }
    /* 滑动窗口：将连续的包移入交付队列 */
    private void slid() {
        // 循环检查链表头部（最老的那个包）
        // 条件1: 链表不为空
        // 条件2: 链表头部的包序号 == 我期望的序号 (expectedSequence)
        while (!this.packets.isEmpty() && (this.packets.getFirst().getTcpH().getTh_seq() - 1) / 100 == this.expectedSequence) {
            // 1. 取出链表头部的包的数据 (tcpS.getData())
            // 2. 放入 dataQueue 等待写入文件
            // 3. poll() 会把这个包从 packets 链表中移除
            this.dataQueue.add(this.packets.poll().getTcpS().getData());
            this.expectedSequence++;
        }

        if (this.dataQueue.size() >= 20 || this.expectedSequence == 1000) {
            this.deliver_data();
        }
    }

    /*交付数据: 将数据写入文件*/
    public void deliver_data() {
        // 检查 this.dataQueue，将数据写入文件
        try {
            File file = new File("recvData.txt");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

            while (!this.dataQueue.isEmpty()) {
                int[] data = this.dataQueue.poll();

                // 将数据写入文件
                for (int i = 0; i < data.length; i++) {
                    writer.write(data[i] + "\n");
                }

                writer.flush();  // 清空输出缓存
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
