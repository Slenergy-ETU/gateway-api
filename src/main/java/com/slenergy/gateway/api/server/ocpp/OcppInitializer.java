package com.slenergy.gateway.api.server.ocpp;

import com.slenergy.gateway.api.server.MessageQueue;
import com.slenergy.gateway.api.server.device.chargingPile.ChargingPile;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.enums.ReadyState;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.slenergy.gateway.api.server.device.RealTimeDevice.initializeRealTimeDevice;

/**
 * class OcppInitializer description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-22
 * @since 1.0
 */
public class OcppInitializer implements Runnable {

    private final String addr;
    private final SQLiteConnection connection;
    private final static Logger LOGGER = LogManager.getLogger(OcppInitializer.class);

    public OcppInitializer(String addr) {
        this.addr = addr;
        connection = new SQLiteConnection(addr);
    }

    public void initialize() throws SQLException, ClassNotFoundException {
        connection.initialize();
    }

    @Override
    public void run() {
        Map<String, Object> addrInfo = null;
        try {
            addrInfo = connection.queryOne("select ip, port from containerAddress where name = ? limit 1;", List.of("ocpp-server"));
        } catch (SQLException e) {
            LOGGER.warn("查找ocpp server地址失效: {}", e.getMessage());
            return;
        }

        if (addrInfo == null) {
            LOGGER.warn("无法从数据库中获取ocpp server地址");
            return;
        }

        String uri = String.format("ws://%s:%d/ocpp/connector", addrInfo.get("ip"), (Integer) addrInfo.get("port"));
        OcppClient client = null;
        try {
            client = new OcppClient(new URI(uri), connection);
        } catch (URISyntaxException e) {
            LOGGER.warn("uri解析失败: {}", e.getMessage());
            return;
        }

        client.connect();
        LOGGER.info("正在连接到ocpp server...");
        while (!client.getReadyState().equals(ReadyState.OPEN)) {
            LOGGER.info("正在等待连接...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.warn("等待连接ocpp server中, 但该线程无法睡眠: {}", e.getMessage());
            }
        }
        LOGGER.info("已经连接上ocpp server...");

        while (!client.isBoot()) {
            LOGGER.info("正在尝试获取充电桩序列号等基本信息...");
            if (!client.isMessaging())
                client.send(new JsonObject().put("func", 4).put("param", "boot").encode());

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER.warn("已经连接上ocpp server获取序列号信息，但该线程无法睡眠: {}", e.getMessage());
            }
        }
        while (!client.isConfig()) {
            LOGGER.info("正在尝试获取充电桩最大限制电流信息...");
            if (!client.isConfig())
                client.send(new JsonObject().put("func", 4).put("param", "config").encode());

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOGGER.warn("已经连接上ocpp server获取最大限制电流，但该线程无法睡眠: {}", e.getMessage());
            }
        }

        client.close();
        client = null;
        LOGGER.info("关闭ocpp客户端");

        // 开始初始化充电桩
        Map<String, Object> cpInfo = null;
        try {
            cpInfo = connection.queryOne("select serialNumber, deviceName, deviceType, ip, port, min, max, interval, pvPriority, batteryPowerLimited, utilityPowerLimited from device join address a on device.id = a.deviceId join power p on device.id = p.deviceId join load l on device.id = l.deviceId where deviceType = ?;", List.of("chargingPile"));
        } catch (SQLException e) {
            LOGGER.warn("无法获取充电桩设备信息: {}", e.getMessage());
            return;
        }

        ChargingPile chargingPile = null;
        try {
            chargingPile = (ChargingPile) initializeRealTimeDevice("chargingPile", addr, cpInfo);
        } catch (Exception e) {
            LOGGER.warn("无法初始化充电桩: {}", e.getMessage());
        }
        if (chargingPile != null)
            MessageQueue.getInstance().addChargingPile(chargingPile.getSerialNumber(), chargingPile);
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.warn("关闭静态数据库连接失败: {}", e.getMessage());
        }
        LOGGER.info("充电桩初始化完毕，关闭线程");
    }

}
