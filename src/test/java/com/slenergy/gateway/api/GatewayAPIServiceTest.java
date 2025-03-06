package com.slenergy.gateway.api;

import com.slenergy.gateway.api.server.DeviceMessage;
import com.slenergy.gateway.api.server.util.HexUtils;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;

/**
 * class GatewayAPIServiceTest description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-20
 * @since 1.0
 */
public class GatewayAPIServiceTest {

    @Test
    public void testLoacalIPAddress() throws Exception {
        InetAddress addr = Inet4Address.getLocalHost();
        String addrStr = addr.getHostAddress();
        System.out.println(addrStr);
        int index = addrStr.lastIndexOf(".") + 1;
        int subnet = Integer.parseInt(addrStr.substring(addrStr.lastIndexOf(".") + 1));
        System.out.println(subnet);

        StringBuilder sb = new StringBuilder(addrStr);
        sb.replace(index, addrStr.length(), String.valueOf(2));
        System.out.println(sb);
    }

    @Test
    public void testDataPack() {
        // 示例数据
        Map<String, String> data = new HashMap<>();
        data.put("10008", "573E");
        data.put("40003", "0");
        data.put("40002", "0");
        data.put("40001", "0");
        data.put("40000", "1");
        data.put("10000", "65");
        data.put("10001", "64");
        data.put("10002", "835");

        // 转换并打印结果
        String result = convertData(data);
        System.out.println(result);
    }

    public static String convertData(Map<String, String> data) {
        // 获取数据采集器序列号和设备序列号（假设长度为30字节）
        String dataCollectorSN = padLeft("67D97B", 30*2); // 使用实际值替换
        String deviceSN = padLeft("8937BF", 30*2);

        // 获取数据时间戳（假设长度为6字节）
        String currentTimestampInHex = getCurrentTimestampInHex();
        System.out.println("时间戳：" + currentTimestampInHex);

        // 构建数据区段内容
        List<String> segments = new ArrayList<>();
        TreeMap<String, String> sortedData = new TreeMap<>(data);
        String startAddress = null;
        StringBuilder segmentData = new StringBuilder();
        int segmentCount = 0;

        for (Map.Entry<String, String> entry : sortedData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 检查键是否是数字
            if (key.matches("\\d+")) {
                int intKey = Integer.parseInt(key);
                if (startAddress == null) {
                    startAddress = Integer.toHexString(intKey).toUpperCase();
                }
                segmentData.append(value);

                // Check if the next key is not consecutive or if this is the last entry
                String nextKey = sortedData.higherKey(key);
                if (nextKey == null || Integer.parseInt(nextKey) != intKey + 1) {
                    String endAddress = Integer.toHexString(intKey).toUpperCase();
                    segments.add(startAddress + endAddress + segmentData.toString());
                    segmentCount++;
                    startAddress = null;
                    segmentData.setLength(0);
                }
            }
        }

        // 构建完整的输出
        StringBuilder output = new StringBuilder();
        output.append(dataCollectorSN);
        output.append(deviceSN);
        output.append(currentTimestampInHex);
        output.append(String.format("%02X", segmentCount));
        for (String segment : segments) {
            output.append(segment);
        }

        return output.toString();
    }

    // 补齐字符串到指定长度，右侧填充空格
    private static String padRight(String str, int length) {
        return String.format("%1$-" + length + "s", str);
    }

    // 补齐字符串到指定长度，左侧填充0
    private static String padLeft(String str, int length) {
        return String.format("%1$" + length + "s", str).replace(' ', '0');
    }

    public static String getCurrentTimestampInHex() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 提取年月日时分秒
        int year = now.getYear() % 100; // 只取年份的后两位
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();

        // 将每个部分转换为2位十六进制
        String yearHex = String.format("%02X", year);
        String monthHex = String.format("%02X", month);
        String dayHex = String.format("%02X", day);
        String hourHex = String.format("%02X", hour);
        String minuteHex = String.format("%02X", minute);
        String secondHex = String.format("%02X", second);

        // 拼接结果
        return yearHex + monthHex + dayHex + hourHex + minuteHex + secondHex;
    }

    /**
     * 测试把ibox数据存入数据字典
     */
//    @Test
//    public void testStoreIboxDic() throws SQLException {
//        // 初始化数据库
//        SQLiteConnection conn = new SQLiteConnection("D:\\file\\ems\\Application_Code\\Gateway-API\\slenergy-gw.db");
//        try {
//            conn.initialize();
//        } catch (SQLException | ClassNotFoundException e) {
//            System.exit(-3);
//        }
//
//        //存在数据字典数据库
//        Map<String, Object> values = new HashMap<>();
//        values.put("protocolType", 0);
//        values.put("protocolVersions", "1.1");
//        values.put("dataInterval", 1);
//        values.put("serialNumber", "123456");
//        values.put("localIp", "111");
//        values.put("localPort", "111");
//        values.put("remoteIp", "111");
//        values.put("remotePort", "111");
//        values.put("model", "");
//        values.put("firmwareVersion", "1.0.0.0");
//        values.put("wirelessType", 1);
//        values.put("timeZone", "111");
//        values.put("restart", 0);
//        values.put("gprs", -75);
//
//        List<String> updateQueries = new ArrayList<>();
//        List<List<Object>> params = new ArrayList<>();// 使用嵌套列表存储每条语句的参数
//
//        values.forEach((name, value) -> {
//            updateQueries.add("UPDATE iboxDic SET value = ? WHERE name = ?;");
//            params.add(List.of(value, name)); // 每条语句的参数组合成一个列表
//        });
//
//        conn.commitTransaction(updateQueries, params); // 确保 commitTransaction 支持 List<List<Object>>
//
//        conn.close();
//    }

    /**
     * ibox数据从数据库存入内存，map
     */
//    @Test
//    public void iboxDicStoreMap() throws SQLException {
//        // 初始化数据库
//        SQLiteConnection conn = new SQLiteConnection("D:\\file\\ems\\Application_Code\\Gateway-API\\slenergy-gw.db");
//        try {
//            conn.initialize();
//        } catch (SQLException | ClassNotFoundException e) {
//            System.exit(-3);
//        }
//        List<Map<String, Object>> querys = conn.query("select type, value from iboxDic;", null);
//        Map<Integer, Object> resultMap = querys.stream().collect(Collectors.toMap(
//                row -> (Integer) row.get("type"),
//                row -> (String) row.get("value")
//        ));
//        DeviceMessage dm = DeviceMessage.getInstance();
//        dm.setEmsBoxDic(resultMap);
////        System.out.println(dm.getEmsBoxDicList());
//        conn.close();
//    }

    /**
     * 测试HexUtil
     */
    @Test
    public void testHexByteToAscii() {
        byte[] bytes = {0x61, 0x30, 0x33, 0x63};//a03c
        byte[] serialNumberByte = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x45, 0x4d, 0x53, 0x31, 0x32, 0x33, 0x34, 0x36, 0x37};//EMS1234567
        byte[] test = {0x01, 0x03, 0x14, 0x00, 0x42, 0x00, 0x04d, 0x00, 0x53, 0x00, 0x31, 0x00, 0x32, 0x00, 0x33, 0x00, 0x34, 0x00, 0x36, 0x00, 0x37};
        String expectedBytes = "a03c";
        String expectedSerialNumber = "EMS123467";

        String actualByte = HexUtils.hexByteToAscii(bytes);
        String actualSerualNumber = HexUtils.hexByteToAscii(serialNumberByte);
        assertEquals(expectedBytes, actualByte);
        assertEquals(expectedSerialNumber, actualSerualNumber);

        System.out.println(actualByte);
        System.out.println(actualSerualNumber);
        System.out.println(HexUtils.hexByteToAscii(test));
    }
    @Test
    public void testHexStringToByteArray() {
        String s = "0102ab";
        String serialNumber = "000000000000000000000000000000000000000000454d5331323334353637";
        byte[] bytes = HexUtils.hexStringToByteArray(serialNumber);
        String ascii = HexUtils.hexByteToAscii(bytes);
        System.out.println(ascii);
    }
    @Test
    public void testTimeZone() {
        String strTZ = new SimpleDateFormat("Z").format(new Date());
        System.out.println(strTZ);
        String tz = String.format("GMT%s", strTZ.substring(0, strTZ.length() - 2));
        System.out.println(tz);
    }

    @Test
    public void testExtractByte() {
        byte[] bytes = {1, 2, 3, 6, 8, 9, 7, 4, 5, 6, 1, 2};
        byte[] aByte = HexUtils.extractByte(bytes, 2, 3);
        System.out.println("ok");
    }
   @Test
    public void testStrUtil() {
        String url = "1#type03#123456";
        System.out.println(url.substring(2, 8));

       System.out.println("bms".equals("bms_monomer"));
   }

   @Test
   public void testEmsParamter() {
       HexFormat hexFormat = HexFormat.of();
       String s = hexFormat.formatHex("a7536b5fcb7e0c3b".getBytes());
       System.out.println("a7536b5fcb7e0c3b :" + s);
   }

   //测试固件升级
    @Test
   public void test() {
//        byte[] dataByte = {0, 3, 3, -24, 0, -126, 1, 24, 69, 77, 83, 49, 50, 51, 52, 54, 55, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 94, 0, 80, 0, 90, 49, 35, 116, 121, 112, 101, 48, 51, 35, 104, 116, 116, 112, 58, 47, 47, 49, 46, 57, 53, 46, 49, 50, 56, 46, 56, 56, 58, 57, 57, 57, 57, 47, 109, 105, 110, 105, 111, 47, 115, 121, 115, 47, 102, 105, 114, 109, 119, 97, 114, 101, 47, 66, 67, 85, 48, 51, 95, 65, 95, 86, 49, 46, 53, 95, 50, 48, 50, 53, 48, 49, 49, 48, 45, 50, 95, 49, 55, 51, 55, 49, 57, 49, 53, 51, 57, 46, 98, 105, 110};
        byte[] dataByte = {0,2,3,-24,0,59,1,24,69,77,83,49,50,51,52,54,55,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,23,0,31,0,19,50,48,50,53,45,48,49,45,50,48,32,49,48,58,48,56,58,52,54};
        System.out.println(HexUtils.hexBytesToHexString(dataByte));
        //编号个数
        byte[] parameterNumByte = HexUtils.extractByte(dataByte, 38, 2);
        int paraNum = HexUtils.hexByteToInt(parameterNumByte[0], parameterNumByte[1]);
        //设置数据的长度
        byte[] parameterBytes = HexUtils.extractByte(dataByte, 40, 2);
        int parameterLen = HexUtils.hexByteToInt(parameterBytes[0], parameterBytes[1]);
        byte[] settingBytes = HexUtils.extractByte(dataByte, 42, parameterLen);

        HashMap<Integer, String> settingMap = new HashMap<>();
        for (int i = 0; i < parameterLen; ) {
            int num = HexUtils.hexByteToInt(settingBytes[i], settingBytes[i + 1]);
            int len = HexUtils.hexByteToInt(settingBytes[i + 2], settingBytes[i + 3]);
            byte[] contentBytes = HexUtils.extractByte(settingBytes, 4, len);
            String content = HexUtils.hexByteToAscii(contentBytes);
            settingMap.put(num, content);
            i = i + 4 + len;
        }

        Set<Integer> keySet = settingMap.keySet();
        if (keySet.contains(80)) {
            String str = settingMap.get(80);
            String updateCommand = str.substring(3, str.length());
            System.out.println(str);
        }
    }

}
