package com.slenergy.gateway.api.server.util;

public class CRC16 {

    public static byte[] calculateCRC(byte[] data) {
        int crc = 0xFFFF; // 初始值为 0xFFFF

        for (byte b : data) {
            crc ^= (b & 0xFF); // 按字节处理数据

            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001; // 多项式 0xA001
                } else {
                    crc >>= 1;
                }
            }
        }

        // 低位在前，高位在后
        return new byte[] { (byte) (crc & 0xFF), (byte) ((crc >> 8) & 0xFF) };
    }

    public static byte[] crc16CCITT(byte[] data) {
        int crc = 0xFFFF;  // 初始值
        int polynomial = 0xA001;  // Modbus CRC-16 多项式

        for (byte b : data) {
            crc ^= b & 0xFF;  // 将字节异或到CRC的低字节
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {  // 如果最低位为1
                    crc = (crc >> 1) ^ polynomial;
                } else {
                    crc >>= 1;
                }
            }
        }

        // 将结果转换为高字节在前，低字节在后的格式
        byte[] result = new byte[2];
        result[0] = (byte) ((crc >> 8) & 0xFF);  // 高字节
        result[1] = (byte) (crc & 0xFF);         // 低字节
        return result;
    }

    public static byte[] crc16CCITTHighLow(byte[] data) {
        int crc = 0xFFFF;  // 初始值
        int polynomial = 0xA001;  // Modbus CRC-16 多项式

        for (byte b : data) {
            crc ^= b & 0xFF;  // 将字节异或到CRC的低字节
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {  // 如果最低位为1
                    crc = (crc >> 1) ^ polynomial;
                } else {
                    crc >>= 1;
                }
            }
        }

        // 将结果转换为高字节在前，低字节在后的格式
        byte[] result = new byte[2];
        result[1] = (byte) ((crc >> 8) & 0xFF);  // 高字节
        result[0] = (byte) (crc & 0xFF);         // 低字节
        return result;
    }

    public static boolean verifyCrc16CCITT(byte[] data, byte[] receivedCrc) {
        // 计算数据的 CRC
        byte[] computedCrc = crc16CCITT(data);

        // 比较计算出的 CRC 与接收到的 CRC
        return (computedCrc[0] == receivedCrc[0]) && (computedCrc[1] == receivedCrc[1]);
    }

}
