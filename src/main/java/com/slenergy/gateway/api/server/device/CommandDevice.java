package com.slenergy.gateway.api.server.device;

import com.slenergy.gateway.docker.DockerExecutor;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import com.slenergy.gateway.docker.config.ContainerStartInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.nio.file.LinkOption;
import java.sql.SQLException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class CommandDevice extends Device {

    private long timstamp;
    protected String sqlAddress;
    protected SQLiteConnection conn;
    protected final static double THRESHOLD = 0.00000001;

    public CommandDevice(String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(serialNumber, deviceName, deviceType);
        this.sqlAddress = sqlAddress;
    }
    public abstract String sendEmsCommand(String commandStr) throws IOException;
    public abstract void initializeParameters() throws Exception;

    public void initialize() throws Exception {
        // 初始化数据库
        conn = new SQLiteConnection(sqlAddress);
        conn.initialize();

        if (deviceType.compareTo("canDevice") == 0)
            return;

        //todo attention 开启容器
        DockerExecutor docker = DockerExecutor.getInstance();
        String name = String.format("%s-%s", deviceType, serialNumber);

        // 容器存在且不在运行状态则重启容器
        // 容器不存在则跑容器
        if (docker.exist(name) && !docker.isRunning(name))
            docker.restart(name);
        else
            runContainer(docker, name);
    }

    private void runContainer(DockerExecutor docker, String name) throws SQLException, InterruptedException {
        Map<String, Object> imgInfo = conn.queryOne("select name, configHostPath, configContainerPath, configPermission, schemaHostPath, schemaContainerPath, schemaPermission from image where deviceType = ? and deviceName = ?;", List.of(deviceType, deviceName));
        Map<String, Object> apiInfo = conn.queryOne("select networkMode, ip, port from containerAddress where name = ?;", List.of("gateway-api"));
        Map<String, Object> serialPortNameInfo = conn.queryOne("select serialPortName from modbusDevice where deviceType = ? and deviceName = ?;", List.of(deviceType, deviceName));
        Map<String, Object> sampleRate = conn.queryOne("select delay, period from deviceSampleRate limit 1;", null);

        ContainerStartInfo config = new ContainerStartInfo((String) imgInfo.get("name"), name);
        if (serialPortNameInfo != null) {
//            config.setDevices(new ContainerStartInfo.DeviceMapping[]{new ContainerStartInfo.DeviceMapping((String) serialPortNameInfo.get("serialPortName"), "/dev/ttyS0", "rmw")});
            String usb = null;
            if (name.contains("dehumidifier")) usb = "/dev/ttyUSB3";
            if (name.contains("liquidCooling")) usb = "/dev/ttyUSB1";
            config.setDevices(new ContainerStartInfo.DeviceMapping[]{new ContainerStartInfo.DeviceMapping(usb, "/dev/ttyUSB0", "rmw")});
        }
        config.setPathBinds(new ContainerStartInfo.PathBind[]{
                new ContainerStartInfo.PathBind((String) imgInfo.get("configHostPath"), (String) imgInfo.get("configContainerPath"), (String) imgInfo.get("configPermission")),
                new ContainerStartInfo.PathBind((String) imgInfo.get("schemaHostPath"), (String) imgInfo.get("schemaContainerPath"), (String) imgInfo.get("schemaPermission"))
        });
//        config.setNetwork(new ContainerStartInfo.ContainerNetwork(String.format("%s_%s", System.getenv("JELINA_APP_ID"), apiInfo.get("networkMode")), ip));
        config.setNetwork(new ContainerStartInfo.ContainerNetwork("host", null));
        if (name.contains("dehumidifier")) {
            config.setEntrypoints(new String[]{
                    "java", "-jar", "/data/gateway-connector-modbus-1.0-SNAPSHOT-jar-with-dependencies.jar", "-P", "8082", "-cfp", "/data/config.json", "-sp", "/data/config_schema.json"
            });
        }
        if (name.contains("liquidCooling")) {
            config.setEntrypoints(new String[]{
                    "java", "-jar", "/data/gateway-connector-modbus-1.0-SNAPSHOT-jar-with-dependencies.jar", "-P", "8083", "-cfp", "/data/config.json", "-sp", "/data/config_schema.json"
            });
        }
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

    public static CommandDevice initializeCommandDevice(String deviceType, String sql, Map<String, Object> info) throws Exception {
        CommandDevice device = null;
        switch (deviceType) {
            case "dehumidifier" -> device = new Dehumidifier((String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql, (String) info.get("ip"), (int) info.get("port"));
            case "liquidCooling" -> device = new LiquidCooling((String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql, (String) info.get("ip"), (int) info.get("port"));
//            case "inverter" -> device = ((String) info.get("deviceName")).compareTo("solinteg") == 0 ? new SolintegInverter(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql) : new Inverter(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql);
//            case "battery" -> device = new Battery(new Power((double) info.get("min"), (double) info.get("max"), (double) info.get("interval")), (int) info.get("pvPriority"), (double) info.get("batteryPowerLimited"), (double) info.get("utilityPowerLimited"), 1024, 5000, (int) info.get("port"), (String) info.get("ip"), (String) info.get("serialNumber"), (String) info.get("deviceName"), (String) info.get("deviceType"), sql);
//            default -> device = new CanDevice(sql, 5533);
        }

        if (device == null)
            throw new NullPointerException("设备无法初始化");

        device.initialize();
//        device.initializeParameters();

        return device;
    }
}
