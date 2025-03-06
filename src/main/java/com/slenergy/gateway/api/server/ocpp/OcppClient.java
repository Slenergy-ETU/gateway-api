package com.slenergy.gateway.api.server.ocpp;

import com.slenergy.gateway.api.server.util.Pair;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.slenergy.gateway.api.server.util.Util.generateIPaddress;

/**
 * class OcppClient description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-22
 * @since 1.0
 */
public class OcppClient extends WebSocketClient {

    @Getter
    private boolean boot;
    @Getter
    private boolean config;
    @Getter
    private boolean messaging;

    private String serialNumber;
    private final SQLiteConnection connection;
    private final static Logger LOGGER = LogManager.getLogger(OcppClient.class);

    public OcppClient(URI serverUri, SQLiteConnection connection) {
        super(serverUri);
        boot = false;
        config = false;
        serialNumber = null;
        this.connection = connection;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        LOGGER.info("已经连接上ocpp server");
    }

    @Override
    public void onMessage(String s) {
        messaging = true;
        LOGGER.info("收到消息: {}", s);
        JsonObject obj = new JsonObject(s);
        if (obj.containsKey("BootNotification")) {
            JsonObject bootNotification = obj.getJsonObject("BootNotification");
            serialNumber = bootNotification.getString("chargeBoxSerialNumber");
            String deviceName = bootNotification.getString("chargePointVendor").toLowerCase();

            try {
                connection.execute("insert into device (serialNumber, deviceName, deviceType) values (?, ?, ?);", List.of(
                        serialNumber,
                        deviceName,
                        "chargingPile"
                ));
            } catch (SQLException e) {
                LOGGER.warn("无法写入充电桩设备信息: {}", e.getMessage());
                return;
            }

            // 生成地址
            Pair<String, Integer> addr = null;
            try {
                addr = generateIPaddress(connection);
            } catch (UnknownHostException | SQLException e) {
                LOGGER.warn("无法生成充电桩地址信息: {}", e.getMessage());
                return;
            }

            try {
                connection.execute("insert into address (deviceId, ip, subnet, port) values ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                        serialNumber,
                        addr.getLeft(),
                        addr.getRight(),
                        8080
                ));
            } catch (SQLException e) {
                LOGGER.warn("无法插入充电桩地址信息: {}", e.getMessage());
                return;
            }

            try {
                connection.commitTransaction(List.of(
                        "insert into deviceSpecificKey (deviceId, keyType, key, value) values ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?)",
                        "insert into lastDayTotalEnergy (deviceId, value) values ((select id from device where serialNumber = ?), ?);"
                ), List.of(
                        List.of(
                            serialNumber, "dynamic", "chargePointModel", bootNotification.getString("chargePointModel"),
                            serialNumber, "dynamic", "firmwareVersion", bootNotification.getString("firmwareVersion"),
                            serialNumber, "static", "chargePointVendor", bootNotification.getString("chargePointVendor"),
                            serialNumber, "static", "chargingAvailability", "Operative"
                        ),
                        List.of(
                                serialNumber,
                                0.0
                        )
                ));
            } catch (SQLException e) {
                LOGGER.warn("无法写入充电桩特定key的信息: {}", e.getMessage());
                return;
            }

            boot = true;
        }

        if (obj.containsKey("configurationKey")) {
            JsonArray array = obj.getJsonArray("configurationKey");
            JsonObject configKey = array.getJsonObject(0);
            int userCurrentLimit = Integer.parseInt(configKey.getString("value"));
            // 保存最大功率
            try {
                connection.execute("insert into power (deviceId, min, max, interval) values ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                        serialNumber,
                        0.0,
                        230.0 * userCurrentLimit,
                        230.0
                ));
            } catch (SQLException e) {
                LOGGER.warn("无法存储充电桩设备功率: {}", e.getMessage());
                return;
            }

            // 保存ems算法需要的参数
            try {
                connection.commitTransaction(List.of(
                        "insert into load (deviceId, pvPriority, batteryPowerLimited, utilityPowerLimited) values ((select id from device where serialNumber = ?), (select number + 1 from (select ifnull(max(pvPriority), 0) as number from load join device d on load.deviceId = d.id where deviceType != ? and deviceType != ?)), ?, ?);",
                        "insert into energyMode (deviceId, key, strategy, name) values ((select id from device where serialNumber = ?), ?, ?, ?);"
                ), List.of(
                        List.of(
                                serialNumber,
                                "inverter",
                                "battery",
                                1.0,
                                0.0
                        ),
                        List.of(
                                serialNumber,
                                "chargingMode",
                                3,
                                "PvFirstAndBattery"
                        )
                ));
            } catch (SQLException e) {
                LOGGER.warn("无法存储充电桩ems算法信息: {}", e.getMessage());
                return;
            }

            config = true;
        }

        messaging = false;
    }

    @Override
    public void onClose(int i, String s, boolean b) {
        LOGGER.info("连接准备关闭, {}, {}, {}", i, s, b);
    }

    @Override
    public void onError(Exception e) {
        LOGGER.warn("连接发生错误: {}", e.getMessage());
    }

    private String findValueFromKey(JsonObject obj, String key) {
        Map<String, Object> m = obj.getMap();
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            if (entry.getKey().contains(key))
                return (String) entry.getValue();
        }

        return null;
    }

}
