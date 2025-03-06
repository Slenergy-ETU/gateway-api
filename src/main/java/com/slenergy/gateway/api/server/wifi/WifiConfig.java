package com.slenergy.gateway.api.server.wifi;

import com.influxdb.client.domain.Run;
import com.slenergy.gateway.api.server.GWapi;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.List;

/**
 * 获取wifi配置信息
 */
public class WifiConfig {

    private final static Logger LOGGER = LogManager.getLogger(WifiConfig.class);

    public static void storeWifiStrength(SQLiteConnection connection) throws SQLException {
        String wifiStrength = getWifiStrength();

        connection.commitTransaction(List.of(
                "update ibox set gprs = ? where id = 1",
                "update iboxDic set value = ? where name = gprs"),
                List.of(
                        List.of(wifiStrength),
                        List.of(wifiStrength)));
    }


    public static String getWifiStrength() {
        String command = "iwconfig wlan0 | grep 'Signal level'";
        String signalStrength = null;
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;

            while ((line = reader.readLine()) != null) {
                LOGGER.info("命令输出: {}", line);
                //提取信号强度
                signalStrength = parseSignalLevel(line);
                if (signalStrength != null) {
                    LOGGER.info("wifi信号强度: {}", signalStrength);
                } else {
                    LOGGER.info("无法解析信号强度");
                }
            }
            reader.close();
        }catch (Exception e) {
            e.printStackTrace();
        }

        return signalStrength;
    }

    private static String parseSignalLevel(String line) {
        if (line.contains("Signal level")) {
            // 使用正则或分割提取信号值
            String[] parts = line.split("Signal level=");
            if (parts.length > 1) {
                return parts[1].split(" ")[0]; // 获取信号强度部分
            }
        }
        return null;
    }
}
