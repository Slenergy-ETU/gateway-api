package com.slenergy.gateway.api.server;

import com.slenergy.gateway.api.server.device.CommandDevice;
import com.slenergy.gateway.api.server.device.Dehumidifier;
import com.slenergy.gateway.api.server.device.EmsBox;
import com.slenergy.gateway.api.server.device.LiquidCooling;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 记录连接的设备信息
 */
public final class DeviceMessage {
    @Setter
    @Getter
    private EmsBox emsBox;
    @Setter
    private Map<Integer, Object> emsBoxDic;
    private Map<String, CommandDevice> devices;
    private Map<String, Dehumidifier> dehumidifiers;
    private Map<String, LiquidCooling> liquidCoolings;
    private Map<String, String> canDevices;
    private final static Logger LOGGER = LogManager.getLogger(DeviceMessage.class);
    private static DeviceMessage INSTANCE = null;
    public static DeviceMessage getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DeviceMessage();
        }

        return INSTANCE;
    }

    private DeviceMessage() {
        dehumidifiers = new HashMap<>();
        liquidCoolings = new HashMap<>();
        devices = new HashMap<>();
    }

    public void addDehumidifier(String sn, Dehumidifier dhf) {
        dehumidifiers.put(sn, dhf);
        devices.put(sn, dhf);
    }

    public void addLiquidCooling(String sn, LiquidCooling lc) {
        liquidCoolings.put(sn, lc);
        devices.put(sn, lc);
    }


    public List<Dehumidifier> getDehumidifiers() {
        return dehumidifiers.values().stream().toList();
    }


//    public String getCanDevices(String deviceType) {
//        String canDeviceSerialNumber = canDevices.get(deviceType);
//        if (canDeviceSerialNumber == null) {
//            LOGGER.info("找不到" + deviceType + "设备");
//            return null;
//        }
//        return canDeviceSerialNumber;
//    }

    public CommandDevice getCommandDevice(String sn) {
        if (!devices.containsKey(sn))
            return null;
        return devices.get(sn);
    }

    /**
     * 获取数据采集器的属性
     */
    public String getEmsBoxDicList(int[] searchParamBytes) {
        if (searchParamBytes == null) {
            // 如果入参为null，将所有键提取出来排序
            searchParamBytes = new int[emsBoxDic.size()];
            int i = 0;
            for (Integer key : emsBoxDic.keySet()) {
                searchParamBytes[i++] = key;
            }
            Arrays.sort(searchParamBytes); // 对键排序
        }

        // 对 entrySet 按键进行排序
        List<Map.Entry<Integer, Object>> sortedEntries = new ArrayList<>(emsBoxDic.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey()); // 按键从小到大排序

        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Integer, Object> entry : sortedEntries) {
            // 判断键是否包含在查询的数组里
            if (isContain(searchParamBytes, entry.getKey())) {
                HexFormat hexFormat = HexFormat.of();

                // 获取键名，转成十六进制数
                String hexKey = String.format("%4s", Integer.toHexString(entry.getKey())).replace(' ', '0');

                // 获取值，转成十六进制数
                String value = (String) entry.getValue();
                String valueHex = hexFormat.formatHex(value.getBytes());
//                String regex = "^-?\\d+$";
//                String valueHex;
//                if (value.matches(regex)) {
//                    valueHex = Integer.toHexString(Integer.parseInt(value));
//                } else {
//                    valueHex = hexFormat.formatHex(value.getBytes());
//                }

                // 计算值的长度
                int valueLen = valueHex.length();
                if (valueLen % 2 != 0) {
                    valueHex = "0" + valueHex;
                }
                String lenHex = String.format("%4s", Integer.toHexString((valueLen + 1) / 2)).replace(' ', '0');

                // 拼接结果
                stringBuilder.append(hexKey).append(lenHex).append(valueHex);
            }
        }
        return stringBuilder.toString();
    }

    public int getEmsBoxNum() {
        return emsBoxDic.size();
    }


    public Object getEmsParameter(byte index) {
        int paramKey = index & 0xff;
        HexFormat hexFormat = HexFormat.of();
        if (emsBoxDic.get(paramKey) != null) {
            //获取值，转成十六进制数
            String value = (String) emsBoxDic.get(paramKey);
            String regex = "^-?\\d+$";
            String valueHex;
            if (value.matches(regex)) {
                valueHex = Integer.toHexString(Integer.parseInt((String) emsBoxDic.get(paramKey)));
            }else {
                valueHex = hexFormat.formatHex(((String) emsBoxDic.get(paramKey)).getBytes());
            }

            //计算值的长度
            int valueLen = valueHex.length();
            if (valueLen % 2 != 0) {
                valueHex = "0" + valueHex;
            }
            return valueHex;
        }
        return null;
    }

    /**
     * 判断一个整数是否在另一个整数数组里面
     * @param array
     * @param value
     * @return
     */
    private boolean isContain(int[] array, int value) {
        HashSet<Object> set = new HashSet<>();
        for (int num : array)
            set.add(num);
        return set.contains(value);
    }

}
