package com.slenergy.gateway.api.server.device;

import com.slenergy.gateway.api.server.device.chargingPile.ChargingPile;
import com.slenergy.gateway.api.server.device.heatPump.HeatPump;
import com.slenergy.gateway.api.server.device.inverter.Battery;
import com.slenergy.gateway.api.server.device.inverter.Inverter;
import com.slenergy.gateway.api.server.device.inverter.SolintegInverter;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import com.slenergy.gateway.docker.DockerExecutor;
import com.slenergy.gateway.docker.config.ContainerStartInfo;
import com.slenergy.gateway.ems.Power;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * class RealTimeDevice description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-03-20
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class RealTimeDevice extends Device implements ChangeAttribute {

    private long timestamp;
    protected String sqlAddress;
    protected SQLiteConnection conn;
    protected final static double THRESHOLD = 0.00000001;

    public RealTimeDevice(String sqlAddress) {
        this.sqlAddress = sqlAddress;
    }

    public RealTimeDevice(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip, serialNumber, deviceName, deviceType);
        this.sqlAddress = sqlAddress;
    }

    public void initialize() throws Exception {
        // 初始化数据库
        conn = new SQLiteConnection(sqlAddress);
        conn.initialize();

        if (deviceType.compareTo("battery") == 0)
            return;

        // 开启容器
        DockerExecutor docker = DockerExecutor.getInstance();
        String name = String.format("%s-%s", deviceType, serialNumber);
        // 容器存在且不在运行状态则重启容器
        // 容器不存在则跑容器
        if (docker.exist(name) && !docker.isRunning(name))
            docker.remove(name);
        else
            runContainer(docker, name);
    }

    public abstract JsonObject toDynamicData();
    public abstract JsonObject toStaticData();
    public abstract JsonObject command(JsonObject param) throws Exception;
    public abstract JsonObject sendPower(double power) throws Exception;
    public abstract void initializeParameters() throws Exception;
    public abstract void sendEmsCommand(String string) throws Exception;

    private void runContainer(DockerExecutor docker, String name) throws SQLException, InterruptedException {
        Map<String, Object> imgInfo = conn.queryOne("select name, configHostPath, configContainerPath, configPermission, schemaHostPath, schemaContainerPath, schemaPermission from image where deviceType = ? and deviceName = ?;", List.of(deviceType, deviceName));
        Map<String, Object> apiInfo = conn.queryOne("select networkMode, ip, port from containerAddress where name = ?;", List.of("gateway-api"));
        Map<String, Object> serialPortNameInfo = conn.queryOne("select serialPortName from modbusDevice where deviceType = ? and deviceName = ?;", List.of(deviceType, deviceName));
        Map<String, Object> sampleRate = conn.queryOne("select delay, period from deviceSampleRate limit 1;", null);

        ContainerStartInfo config = new ContainerStartInfo((String) imgInfo.get("name"), name);
        if (serialPortNameInfo != null)
            config.setDevices(new ContainerStartInfo.DeviceMapping[]{new ContainerStartInfo.DeviceMapping((String) serialPortNameInfo.get("serialPortName"), "/dev/ttyS0", "rmw")});

        config.setPathBinds(new ContainerStartInfo.PathBind[]{
                new ContainerStartInfo.PathBind((String) imgInfo.get("configHostPath"), (String) imgInfo.get("configContainerPath"), (String) imgInfo.get("configPermission")),
                new ContainerStartInfo.PathBind((String) imgInfo.get("schemaHostPath"), (String) imgInfo.get("schemaContainerPath"), (String) imgInfo.get("schemaPermission"))
        });
//        config.setNetwork(new ContainerStartInfo.ContainerNetwork(String.format("%s_%s", System.getenv("JELINA_APP_ID"), apiInfo.get("networkMode")), ip));
        config.setNetwork(new ContainerStartInfo.ContainerNetwork("host", null));
        config.setCommands(new String[]{
                "-d", String.valueOf((int) sampleRate.get("delay")),
                "-p", String.valueOf((int) sampleRate.get("period")),
                "-a", ip,
                "-ca", (String) apiInfo.get("ip"),
                "-cp", String.valueOf((int) apiInfo.get("port")),
                "-sn", serialNumber,
                "-dn", deviceName
        });

        if (!docker.findImage((String) imgInfo.get("name")))
            docker.pullImage((String) imgInfo.get("name"), 300, TimeUnit.SECONDS, System.getenv("DOCKER_USERNAME"), System.getenv("DOCKER_TOKEN"));
        String resp = docker.create(config);
        docker.start(resp);
    }

    public void setAttribute(String key, Object value) {
        if (key.compareTo("timestamp") == 0)
            timestamp = (long) value;
    }

    public long tsToSecond() {
        return timestamp / 1000;
    }

    protected String getDeviceSpecificKeySql(String key) {
        return String.format("update deviceSpecificKey set value = ? where key = '%s' and deviceId = (select id from device where serialNumber = ?);", key);
    }

    public static RealTimeDevice initializeRealTimeDevice(String deviceType, String sql, Map<String, Object> info) throws Exception {
        RealTimeDevice device = null;
        switch (deviceType) {
            case "inverter" -> device = ((String) info.get("deviceName")).compareTo("solinteg") == 0 ? new SolintegInverter(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql) : new Inverter(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql);
            case "battery" -> device = new Battery(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql);
            case "heatPump" -> device = new HeatPump(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql);
            case "chargingPile" -> device = new ChargingPile(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql);
        }

        if (device == null)
            throw new NullPointerException("设备无法初始化");

        device.initialize();
        device.initializeParameters();

        return device;
    }

}
