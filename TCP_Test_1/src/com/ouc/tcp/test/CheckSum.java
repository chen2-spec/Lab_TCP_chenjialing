package com.ouc.tcp.test;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {
    /* 计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段 */
    public static short computeChkSum(TCP_PACKET tcpPack) {
        int checkSum = 0;
        TCP_HEADER header = tcpPack.getTcpH();
        int[] data = tcpPack.getTcpS().getData();

        // 累加序列号和确认号
        checkSum += header.getTh_seq();
        checkSum += header.getTh_ack();

        // 累加数据字段
        if (data != null) {
            for (int d : data) {
                checkSum += d;
            }
        }

        // 模拟 16 位累加取反（Java中用short表示，需注意溢出）
        while ((checkSum >> 16) > 0) {
            checkSum = (checkSum & 0xFFFF) + (checkSum >> 16);
        }

        return (short) (~checkSum);
    }
}