package com.slenergy.gateway.api.server.util;

public final class HexUtils {

    /**
     * 把十六进制的字符转成ascii字符串
     * @param data
     * @return
     */
    public static String hexByteToAscii(byte[] data) {
        if (data == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0)
                continue;
            //将byte转成unsigned int（0-255范围）
            int unsignedValue = data[i] & 0xff;
            stringBuilder.append((char) unsignedValue);
        }
        return stringBuilder.toString().trim();
    }

    /**
     * 复制字节组里面的连续字节，形成新的字节组
     * @param data 原始字节
     * @param start 开始的字节
     * @param len 字节长度
     * @return
     */
    public static byte[] extractByte(byte[] data, int start, int len) {
        if (start < 0 || len < 0 || start + len > data.length) {
            return null;
        }
        byte[] bytes = new byte[len];
        System.arraycopy(data, start, bytes, 0, len);
        return bytes;
    }

    /**
     * 把两个十六进制的字节转成一个整数
     * @param highByte
     * @param lowByte
     * @return
     */
    public static int hexByteToInt(byte highByte, byte lowByte) {
        return ((highByte & 0xff) << 8) | (lowByte & 0xff);
    }

    /**
     * 将整数拆分成两个十六进制的字节。
     *
     * @param value 整数。
     * @return      包含两个十六进制字节的字节组，第一个字节是高位，第二个字节是低位。
     */
    public static byte[] intToHexBytes(int value) {
        // 将整数拆分为两个字节
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((value >> 8) & 0xff); // 高位字节
        bytes[1] = (byte) (value & 0xff);        // 低位字节

        return bytes;
    }

    /**
     * 将十六进制字符串转换为字节数组
     * @return
     */
    public static byte[] hexStringToByteArray(String hexString) {
        int length = hexString.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) Integer.parseInt(hexString.substring(i, i + 2), 16);
        }
        return data;
    }

    /**
     * 将十六进制数组转成字符串
     * @param hexBytes
     * @return
     */
    public static String hexBytesToHexString(byte[] hexBytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < hexBytes.length; i++) {
            int valInt = hexBytes[i] & 0xff;
            String hexString = Integer.toHexString(valInt);
            stringBuilder.append(String.format("%2s", hexString).replace(' ', '0'));
        }
        return stringBuilder.toString();
    }

    /**
     * 把字节组转成整数组
     * @param hexBytes
     * @return
     */
    public static int[] hexBytesToInt(byte[] hexBytes) {
        int[] intArray = new int[hexBytes.length];
        for (int i = 0; i < hexBytes.length; i++) {
            intArray[i] = hexBytes[i] & 0xff;
        }
        return intArray;
    }

    /**
     * 替换数组中一段连续的字节组。
     *
     * @param original 原始数组。
     * @param replacement 要替换的字节数组。
     * @param startIndex 替换开始的索引（包含）。
     * @return 替换后的数组。
     */
    public static byte[] replaceBytes(byte[] original, byte[] replacement, int startIndex) {
        if (startIndex < 0 || startIndex + replacement.length > original.length) {
            return null;
        }

        byte[] result = new byte[original.length];
        System.arraycopy(original, 0, result, 0, startIndex); // Copy the beginning part
        System.arraycopy(replacement, 0, result, startIndex, replacement.length); // Copy the replacement part
        System.arraycopy(original, startIndex + replacement.length, result, startIndex + replacement.length, original.length - (startIndex + replacement.length)); // Copy the remaining part

        return result;
    }

    /**
     * 合并字节组
     * @param arrays
     * @return
     */
    public static byte[] mergeBytes(byte[]... arrays) {
        // 计算总长度
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        // 创建一个新的字节数组，长度为总长度
        byte[] mergedArray = new byte[totalLength];

        // 合并数组
        int currentOffset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, mergedArray, currentOffset, array.length);
            currentOffset += array.length;
        }

        return mergedArray;
    }
}
