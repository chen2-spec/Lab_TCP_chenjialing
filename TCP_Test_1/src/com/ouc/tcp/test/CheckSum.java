package com.ouc.tcp.test;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;

public class CheckSum {

    /*计算TCP报文段校验和：只需校验TCP首部中的seq、ack和sum，以及TCP数据字段*/
    public static short computeChkSum(TCP_PACKET tcpPack) {
        // 使用 CRC32 算法进行校验
        CRC32 crc = new CRC32();

        // 1. 校验首部关键字段
        crc.update(tcpPack.getTcpH().getTh_seq());
        crc.update(tcpPack.getTcpH().getTh_ack());

        // 2. 校验数据部分
        // 注意：Data是int[]，需要遍历加入校验
        int[] data = tcpPack.getTcpS().getData();
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                crc.update(data[i]);
            }
        }

        // 3. 返回校验值 (强制转换为short)
        return (short) crc.getValue();
    }
	
}
