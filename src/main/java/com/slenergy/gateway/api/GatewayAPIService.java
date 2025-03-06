package com.slenergy.gateway.api;

import com.slenergy.gateway.api.server.DeviceMessage;
import com.slenergy.gateway.api.server.GWapi;
import com.slenergy.gateway.api.server.can.CanServer;
import com.slenergy.gateway.api.server.device.Dehumidifier;
import com.slenergy.gateway.api.server.device.EmsBox;
import com.slenergy.gateway.api.server.device.IBox;
import com.slenergy.gateway.api.server.device.LiquidCooling;
import com.slenergy.gateway.api.server.schema.config.PathConfig;
import com.slenergy.gateway.api.server.util.Pair;
import com.slenergy.gateway.api.server.wifi.WifiConfig;
import com.slenergy.gateway.api.server.wifi.WifiConnectorServer;
import com.slenergy.gateway.connector.modbus.scanner.*;
import com.slenergy.gateway.database.sqlite.SQLiteConnection;
import com.slenergy.gateway.docker.DockerExecutor;
import com.slenergy.gateway.docker.config.ContainerStartInfo;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.slenergy.gateway.api.server.device.CommandDevice.initializeCommandDevice;
import static com.slenergy.gateway.api.server.schema.config.ConfigReader.check;
import static com.slenergy.gateway.api.server.util.Util.generateIPaddress;
import static com.slenergy.gateway.api.server.util.Util.getLocalIp;
import static com.slenergy.gateway.connector.modbus.scanner.DeviceScanner.scanDevice;

/**
 * {@code class} {@code GatewayAPIService} description
 * 程序运行主体
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-12-11
 * @since 1.0-SNAPSHOT
 */
public class GatewayAPIService {

    private static final Logger LOGGER = LogManager.getLogger(GatewayAPIService.class);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static final class ModbusDeviceInfo {

        private String serialNumber;
        private String deviceType;
        private String deviceName;
        private int slaveId;
        private CommunicationTime communicationTime;
        private ConnectionParameter connectionParameter;
        private String serialPortName;
        private int model;
        private int rated;

    }

    private static Options getOptions() {
        Option configPath = new Option("cfp", "config-path", true, "配置文件路径");
        configPath.setRequired(true);
        Option schemaPath = new Option("sp", "schema-path", true, "schema文件路径");
        schemaPath.setRequired(true);

        Options options = new Options();
        options.addOption(configPath);
        options.addOption(schemaPath);
        return options;
    }

    private static PathConfig parseArguments(CommandLine cli) {
        return new PathConfig(cli.getOptionValue("config-path"), cli.getOptionValue("schema-path"));
    }

    private static Date getNextDate() {
        Calendar time = Calendar.getInstance();
        time.add(Calendar.DATE, 1);
        time.set(Calendar.HOUR_OF_DAY, 0);
        time.set(Calendar.MINUTE, 0);
        time.set(Calendar.SECOND, 0);
        time.set(Calendar.MILLISECOND, 0);
        return time.getTime();
    }

    private static String getSystemSerialNumber(String name) throws IOException {
        FileInputStream fis = new FileInputStream(name);
        byte[] data = new byte[fis.available()];
        int n = fis.read(data);
        fis.close();
        if (n <= 0)
            throw new IOException("无法获取序列号");

        StringBuilder sb = new StringBuilder();
        for (byte c : data) {
            if (c != 0)
                sb.append((char) c);
        }

        return sb.toString().trim();
    }

    private static ModbusDevice findModbusDevice(Map<Integer, ModbusDevice> devices, String deviceType, String deviceName) {
        for (ModbusDevice device : devices.values()) {
            if (device.getDeviceType().compareTo(deviceType) == 0 && device.getDeviceName().compareTo(deviceName) == 0)
                return device;
        }

        return null;
    }

    private static Map<String, ModbusDeviceInfo> combineModbusDeviceInfo(Map<Integer, ModbusDevice> deviceMap, List<DeviceScannerResult> deviceScannerResults, String serialNumber) {
        Map<String, ModbusDeviceInfo> results = new HashMap<>();
        for (DeviceScannerResult result : deviceScannerResults) {
            ModbusDevice modbusDevice = findModbusDevice(deviceMap, result.getDeviceType(), result.getDeviceName());
            if (modbusDevice != null) {
                ModbusDeviceInfo info = new ModbusDeviceInfo(
                        result.getSerialNumber(),
                        modbusDevice.getDeviceType(),
                        modbusDevice.getDeviceName(),
                        modbusDevice.getSlaveId(),
                        modbusDevice.getCommunicationTime(),
                        modbusDevice.getConnectionParameter(),
                        result.getSerialPortName(),
                        result.getModel(),
                        result.getRated()
                );
                if (info.getSerialNumber() == null) {
                    DeviceMessage dm = DeviceMessage.getInstance();
                    String datalogSn = dm.getEmsBox().getSerialNumber();
//                    if (serialNumber.length() > 7)
//                        serialNumber = serialNumber.substring(0, 7);
                    switch (info.getDeviceType()) {
                        case "dehumidifier" -> info.setSerialNumber("DH" + datalogSn);
                        case "liquidCooling" -> info.setSerialNumber("LC" + datalogSn);
                        default -> info.setSerialNumber(String.format("%s-%s", result.getDeviceType(), result.getDeviceName()));
                    }
                }

                results.put(info.getDeviceType(), info);
            }
        }

        return results;
    }

    /**
     * 生成除湿机的序列号
     * @return
     */
    private static String generateSerialNum() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        // 添加固定前缀 "DH" 并转换为 ASCII 码的十六进制
        char dChar = 'D';
        char hChar = 'H';
        sb.append(dChar).append(hChar);

        // 生成 7 个随机数字字符，转换为十六进制 ASCII 码并追加到序列号
        for (int i = 0; i < 7; i++) {
            int randomDigit = random.nextInt(10);      // 随机生成 0 到 9 的数字
            sb.append(randomDigit + "");
        }

        String serialNumber = sb.toString();
        LOGGER.info("生成的随机序列号: {}", serialNumber);

        return serialNumber;
    }

    /**
     * 保存ems的信息
     * @param conn
     * @param snFile
     * @param ipFile
     * @param port
     * @return 返回序列号
     * @throws IOException
     * @throws InterruptedException
     * @throws SQLException
     */
    private static String storeIBox(SQLiteConnection conn, String snFile, String ipFile, int port) throws IOException, InterruptedException, SQLException {
        // 获取序列号
        String sn = getSystemSerialNumber(snFile);
        LOGGER.info("获取到的序列号, {}", sn);
//        String sn = "EMS123467";
        // 获取时区
        String strTZ = new SimpleDateFormat("Z").format(new Date());
        String tz = String.format("GMT%s", strTZ.substring(0, strTZ.length() - 2));
        // 获取ip地址
        String localIp = getLocalIp();

        // 获取板子ip
//        String remoteIp = "10.21.8.48";
        String remoteIp = null;
        {
            // 一直等待存有ip地址的文件生成，直到找到该文件才继续初始化
            boolean isSuccess = false;
            while (!isSuccess) {
                String line = null;
                try {
                    BufferedReader in = new BufferedReader(new FileReader(ipFile));
                    line = in.readLine();
                } catch (FileNotFoundException e) {
                    LOGGER.warn("找不到ip存有ip地址的文件: {}", e.getMessage());
                } catch (IOException e) {
                    LOGGER.warn("读取文件失败: {}", e.getMessage());
                }

                if (line != null && line.compareTo("") != 0) {
                    remoteIp = line;
                    isSuccess = true;
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    LOGGER.warn("无法等待获取板子ip地址: {}", e.getMessage());
                }
            }
        }
        //存在静态数据库
        conn.commitTransaction(List.of(
                "insert into ibox (id, serialNumber, protocolType, protocolVersions, dataInterval, localIp, localPort, remoteIp, remotePort, model, firmwareVersion, wirelessType, timeZone, restart, gprs) values (1, ?, 0, '1.1', 1.0, ?, ?, ?, ?, 'kedge350', '1.0.0.0', 1, ?, 0, -75);",
                "update mqtt set clientID = ? where id = ?;"
        ), List.of(
                List.of(
                        sn,
                        remoteIp,
                        port,
                        localIp,
                        port,
                        tz
                ),
                List.of(
                        sn,
                        1
                )
        ));

        //存在数据字典数据库
        Map<String, Object> values = new HashMap<>();
        values.put("protocolType", 0);
        values.put("protocolVersions", "1.1");
        values.put("dataInterval", 1.0);
        values.put("serialNumber", sn);
        values.put("localIp", localIp);
        values.put("localPort", port);
        values.put("remoteIp", remoteIp);
        values.put("remotePort", port);
        values.put("model", "kedge350");
        values.put("firmwareVersion", "1.0.0.0");
        values.put("wirelessType", 1);
        values.put("timeZone", tz);
        values.put("restart", 0);
        values.put("gprs", -75);

        List<String> updateQueries = new ArrayList<>();
        List<List<Object>> params = new ArrayList<>();// 使用嵌套列表存储每条语句的参数

        values.forEach((name, value) -> {
            updateQueries.add("UPDATE iboxDic SET value = ? WHERE name = ?;");
            params.add(List.of(value, name)); // 每条语句的参数组合成一个列表
        });

        conn.commitTransaction(updateQueries, params); // 确保 commitTransaction 支持 List<List<Object>>

        //存入内存
        List<Map<String, Object>> querys = conn.query("select type, value from iboxDic;", null);
        Map<Integer, Object> resultMap = querys.stream().collect(Collectors.toMap(
                row -> (Integer) row.get("type"),
                row -> (String) row.get("value")
        ));
        DeviceMessage dm = DeviceMessage.getInstance();
        dm.setEmsBoxDic(resultMap);
        return sn;
    }

    private static void scanModbusDevices(SQLiteConnection conn, String serialNumber) throws SQLException, NullPointerException, UnknownHostException {
        List<Map<String, Object>> scaningDeviceInfo = conn.query("select modbusDevice.id, deviceType, deviceName, slaveId, readResponseTime, writeResponseTime, communicationMargin, baudRate, dataBits, stopBits, parity, flowControl from modbusDevice join modbusResponseTime rt on modbusDevice.responseTime = rt.id join modbusConnectionParameter cp on modbusDevice.connectionParameter = cp.id;", null);

        if (scaningDeviceInfo == null)
            throw new NullPointerException("找不到需要扫描的设备");

        Map<Integer, ModbusDevice> devices = new HashMap<>();
        for (Map<String, Object> m : scaningDeviceInfo) {
            ModbusDevice md = new ModbusDevice(
                    (String) m.get("deviceType"),
                    (String) m.get("deviceName"),
                    (int) m.get("slaveId"),
                    new CommunicationTime((int) m.get("readResponseTime"), (int) m.get("writeResponseTime"), (int) m.get("communicationMargin")),
                    new ConnectionParameter((int) m.get("baudRate"), (int) m.get("dataBits"), (int) m.get("stopBits"), (int) m.get("parity"), (int) m.get("flowControl"))
            );
            devices.put((int) m.get("id"), md);
        }

        List<Map<String, Object>> reg = conn.query("select device, key, startingAddress, functionCode, objectCount from modbusRegister;", null);

        if (reg == null)
            throw new NullPointerException("找不到需要扫描设备的寄存器信息");

        for (Map<String, Object> m : reg)
            devices.get((int) m.get("device")).addModbusRegister(new ModbusRegister((String) m.get("key"), (int) m.get("startingAddress"), (int) m.get("functionCode"), (int) m.get("objectCount")));

        LOGGER.info("device scanner需要的输入参数: {}", devices);
        List<DeviceScannerResult> results = scanDevice(devices.values().stream().toList());
        LOGGER.info("device scanner输出的参数: {}", results);
        if (results == null)
            throw new NullPointerException("无法扫到任何modbus设备");

        for (DeviceScannerResult dsr : results) {
            // 更新串口信息
            conn.execute("update modbusDevice set serialPortName = ? where deviceType = ? and deviceName = ?;", List.of(
                    dsr.getSerialPortName(),
                    dsr.getDeviceType(),
                    dsr.getDeviceName()
            ));
        }

        // 判断是否第一次开机
//        Map<String, Object> isFirstTimeToInitialize = conn.queryOne("select number > ? as result from (select count(*) as number from device join address a on device.id = a.deviceId join power p on device.id = p.deviceId join load l on device.id = l.deviceId);", List.of(results.size()));
        Map<String, Object> isFirstTimeToInitialize = conn.queryOne("select number > ? as result from (select count(*) as number from device join address a on device.id = a.deviceId);", List.of(0));
        LOGGER.info("是否第一次开机: {}", (int) isFirstTimeToInitialize.get("result") <= 0);

        // 存储设备信息
        if ((int) isFirstTimeToInitialize.get("result") <= 0)
        storeModbusDevices(conn, combineModbusDeviceInfo(devices, results, serialNumber));
    }

    private static void storeModbusDevices(SQLiteConnection conn, Map<String, ModbusDeviceInfo> modbusDeviceInfoMap) throws SQLException, UnknownHostException, NullPointerException {
        for (Map.Entry<String, ModbusDeviceInfo> entry : modbusDeviceInfoMap.entrySet()) {
            ModbusDeviceInfo info = entry.getValue();
            switch (entry.getKey()) {
                case "inverter" -> {
                    String deviceName = entry.getValue().getDeviceName();
                    // 存储逆变器以及电池信息
                    conn.execute("insert into device (serialNumber, deviceName, deviceType) values (?, ?, ?), (?, ?, ?);", List.of(info.serialNumber, info.deviceName, info.deviceType, String.format("%s-B", info.serialNumber), String.format("%s-battery", info.deviceName), "battery"));

                    // 生成地址
                    Pair<String, Integer> addr = generateIPaddress(conn);

                    conn.execute("insert into address (deviceId, ip, subnet, port) values ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                            info.serialNumber,
                            addr.getLeft(),
                            addr.getRight(),
                            8080,
                            String.format("%s-B", info.serialNumber),
                            addr.getLeft(),
                            addr.getRight(),
                            8080
                    ));
                    switch (deviceName) {
                        case "solinteg":
                            // 写入最大功率
                            Map<String, Object> ratedPowerQuery = conn.queryOne("select ratedPower from solintegInverterModelInfo where model = ? and rated = ?;", List.of(entry.getValue().getModel(), entry.getValue().getRated()));

                            if (ratedPowerQuery == null)
                                throw new NullPointerException("找不到逆变器最大功率");

                            // 保存最大功率
                            double power = (double) ratedPowerQuery.get("ratedPower");
                            conn.execute("insert into power (deviceId, min, max, interval) values ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                                    info.serialNumber,
                                    -power,
                                    power,
                                    1.0,
                                    String.format("%s-B", info.serialNumber),
                                    -power / 2,
                                    power / 2,
                                    1.0
                            ));

                            // 保存ems算法需要的参数
                            conn.execute("insert into load (deviceId, pvPriority, batteryPowerLimited, utilityPowerLimited) values ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                                    info.serialNumber,
                                    0,
                                    0.0,
                                    0.0,
                                    String.format("%s-B", info.serialNumber),
                                    7,
                                    0.0,
                                    0.0
                            ));
                            break;
                        case "slenergy":
                            // 保存最大功率
                            conn.execute("insert into power (deviceId, min, max, interval) values ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                                    info.serialNumber,
                                    -info.rated,
                                    info.rated,
                                    1.0,
                                    String.format("%s-B", info.serialNumber),
                                    -info.rated / 2,
                                    info.rated / 2,
                                    1.0
                            ));

                            // 保存ems算法需要的参数
                            conn.execute("insert into load (deviceId, pvPriority, batteryPowerLimited, utilityPowerLimited) values ((select id from device where serialNumber = ?), ?, ?, ?), ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                                    info.serialNumber,
                                    0,
                                    0.0,
                                    0.0,
                                    String.format("%s-B", info.serialNumber),
                                    7,
                                    0.0,
                                    0.0
                            ));
                            break;
                        default:
                            LOGGER.warn("未知厂商的设备：{}", deviceName);
                            break;

                    }

                }
                case "dehumidifier" -> {
                    conn.execute("insert into device (serialNumber, deviceName, deviceType) values (?, ?, ?);", List.of(info.serialNumber, info.deviceName, info.deviceType));
                    // 生成地址
                    Pair<String, Integer> addr = generateIPaddress(conn);

                    conn.execute("insert into address (deviceId, ip, subnet, port) values ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                            info.serialNumber,
                            "127.0.0.1",
                            0,
                            8082
                    ));
                }
                case "liquidCooling" -> {
                    conn.execute("insert into device (serialNumber, deviceName, deviceType) values (?, ?, ?);", List.of(info.serialNumber, info.deviceName, info.deviceType));
                    // 生成地址
                    Pair<String, Integer> addr = generateIPaddress(conn);

                    conn.execute("insert into address (deviceId, ip, subnet, port) values ((select id from device where serialNumber = ?), ?, ?, ?);", List.of(
                            info.serialNumber,
                            "127.0.0.1",
                            0,
                            8083
                    ));
                }
            }
        }
    }



    private static IBox initializeIBox(SQLiteConnection conn) throws SQLException {
        Map<String, Object> iboxInfo = conn.queryOne("select serialNumber, protocolType, protocolVersions, dataInterval, localIp, localPort, remoteIp, remotePort, model, firmwareVersion, wirelessType, timeZone, restart, gprs from ibox;", null);
        LOGGER.info("emsBox信息: {}", iboxInfo);

        IBox ibox = new IBox();
        for (Map.Entry<String, Object> entry : iboxInfo.entrySet())
            ibox.setAttribute(entry.getKey(), entry.getValue());

        return ibox;
    }

    private static EmsBox initializeEmsBox(SQLiteConnection conn) throws SQLException {
        Map<String, Object> iboxInfo = conn.queryOne("select serialNumber, protocolType, protocolVersions, dataInterval, localIp, localPort, remoteIp, remotePort, model, firmwareVersion, wirelessType, timeZone, restart, gprs from ibox;", null);
        LOGGER.info("emsBox信息: {}", iboxInfo);
        EmsBox emsBox = new EmsBox();
        emsBox.setSerialNumber((String) iboxInfo.get("serialNumber"));
        emsBox.setDeviceName((String) iboxInfo.get("deviceName"));
        emsBox.setDeviceType((String) iboxInfo.get("deviceType"));
        emsBox.setProtocolType((int) iboxInfo.get("protocolType"));
        emsBox.setProtocolVersions((String) iboxInfo.get("protocolVersion"));
        emsBox.setDataInterval((double) iboxInfo.get("dataInterval"));
        emsBox.setLocalIp((String) iboxInfo.get("localIp"));
        emsBox.setLocalPort((int) iboxInfo.get("localPort"));
        emsBox.setRemoteIp((String) iboxInfo.get("remoteIp"));
        emsBox.setRemotePort((int) iboxInfo.get("remotePort"));
        emsBox.setModel((String) iboxInfo.get("model"));
        emsBox.setFirmwareVersion((String) iboxInfo.get("firmwareVersion"));
        emsBox.setWirelessType((int) iboxInfo.get("wirelessType"));
        emsBox.setTimeZone((String) iboxInfo.get("timeZone"));
        emsBox.setRestart((int) iboxInfo.get("restart"));
        emsBox.setGprs((int) iboxInfo.get("gprs"));
        //保存一份信息到内存
        return emsBox;
    }

    private static void initializeMqtt(SQLiteConnection conn, DockerExecutor docker) throws SQLException, NullPointerException, InterruptedException {
        Map<String, Object> mqttInfo = conn.queryOne("select c.name as name, networkMode, ip, i.name as image from containerAddress as c join image i on c.name = i.deviceName where c.name = ? limit 1;", List.of("mqtt"));

        if (mqttInfo == null)
            throw new NullPointerException("没有mqtt容器的任何信息");

        String name = (String) mqttInfo.get("name");

        boolean isExited = docker.exist(name);
        if (isExited && !docker.isRunning((name))) {
            // 重启容器
            docker.restart(name);
            return;
        }

        // 开始跑容器
        Map<String, Object> interval = conn.queryOne("select dataInterval from ibox limit 1;", null);
        Map<String, Object> apiIP = conn.queryOne("select ip, port from containerAddress where name = ?;", List.of("gateway-api"));
        Map<String,Object> mqttStartInfo = conn.queryOne("select qos, broker, subscribeTopic, publishTopic, clientID, username, password from mqtt limit 1;", null);

        ContainerStartInfo containerConfig = new ContainerStartInfo((String) mqttInfo.get("image"), (String) mqttInfo.get("name"));
//        containerConfig.setNetwork(new ContainerStartInfo.ContainerNetwork(String.format("%s_%s", System.getenv("JELINA_APP_ID"), mqttInfo.get("networkMode")), (String) mqttInfo.get("ip")));
        containerConfig.setNetwork(new ContainerStartInfo.ContainerNetwork("host", null));
        //todo 增加日志映射
//        containerConfig.setPathBinds(new ContainerStartInfo.PathBind[]{
//                new ContainerStartInfo.PathBind("/root/logs", "/data/logs", "rw")
//        });
//        containerConfig.setVolumeBinds(new ContainerStartInfo.VolumeBind[]{
//                new ContainerStartInfo.VolumeBind("/root/logs", "/data/logs", "rw")
//        });
//
//        containerConfig.setMounts(new ContainerStartInfo.Mount[]{
//                new ContainerStartInfo.Mount("mqtt-logs", "/data/logs", false)
//        });

        containerConfig.setCommands(new String[]{
                "-d", String.valueOf(30000),
                "-p", String.valueOf(Duration.ofMinutes((long) (double) interval.get("dataInterval")).getSeconds() * 1000),
                "-a", (String) mqttInfo.get("ip"),
                "-ca", (String) apiIP.get("ip"),
                "-cp", String.valueOf((int) apiIP.get("port")),
                "-q", String.valueOf((int) mqttStartInfo.get("qos")),
                "-b", (String) mqttStartInfo.get("broker"),
                "-st", (String) mqttStartInfo.get("subscribeTopic"),
                "-pt", (String) mqttStartInfo.get("publishTopic"),
                "-c", (String) mqttStartInfo.get("clientID"),
                "-u", (String) mqttStartInfo.get("username"),
                "-pw", (String) mqttStartInfo.get("password")
        });

        if (!docker.findImage((String) mqttInfo.get("image"))) {
            docker.pullImage((String) mqttInfo.get("image"), 300, TimeUnit.SECONDS, System.getenv("DOCKER_USERNAME"), System.getenv("DOCKER_TOKEN"));
        }
        String containerId = docker.create(containerConfig);
        docker.start(containerId);
    }

    public static void main(String[] args) {
        // 解析命令行参数
        Options options = getOptions();

        CommandLine cli = null;
        CommandLineParser parser = new DefaultParser();

        try {
            cli = parser.parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("Input arguments:", options);
            LOGGER.error("无法解析命令行参数: {}", e.getMessage());
            System.exit(-1);
        }

        // 用命令行参数获取模块参数
        PathConfig pathConfig = parseArguments(Objects.requireNonNull(cli, "CommandLine实例不能为空"));
        // 读取模块配置文件
        JsonObject conf = null;
        // 检查配置
        try {
            conf = check(pathConfig);
        } catch (IOException e) {
            LOGGER.error("无法打开配置文件: {}", e.getMessage());
            System.exit(-2);
        }

        if (conf == null) {
            System.err.println("配置文件出错，请先检查配置文件");
            System.exit(-2);
        }

        final JsonObject config = conf;

        // 初始化数据库
        SQLiteConnection conn = new SQLiteConnection(config.getString("sql"));
        try {
            conn.initialize();
        } catch (SQLException | ClassNotFoundException e) {
            LOGGER.error("实例化静态数据库失败: {}", e.getMessage());
            System.exit(-3);
        }

        // 查询数据库
        Map<String, Object> iboxCnt = null;
        try {
            iboxCnt = conn.queryOne("select count(id) as num from ibox;", null);
        } catch (SQLException e) {
            LOGGER.error("无法获取一条ibox数据: {}", e.getMessage());
            System.exit(-3);
        }

        //todo 初始化emsbox， 第一次开机
        String sn = null;
        if (((int) iboxCnt.get("num")) == 0) {
            LOGGER.info("第一次开机，收集ibox信息存入数据库");
            try {
                sn = storeIBox(conn, config.getString("sn"), config.getString("ip"), config.getJsonObject("address").getInteger("port"));
            } catch (SQLException e) {
                LOGGER.error("静态数据库出问题了: {}", e.getMessage());
                System.exit(-3);
            } catch (IOException | InterruptedException e) {
                LOGGER.error("无法从板子获取序列号: {}", e.getMessage());
                System.exit(-4);
            }
            LOGGER.info("ibox信息已经存入数据库");
        } else {
            //不是第一次开机，就需要把数据采集器的数据存入内存中
            List<Map<String, Object>> querys = null;
            try {
                querys = conn.query("select type, value from iboxDic;", null);
            } catch (SQLException e) {
                LOGGER.error("静态数据库出问题了: {}", e.getMessage());
                System.exit(-3);
            }
            //获取数据库里ems的序列号
            try {
                List<Map<String, Object>> query = conn.query("select serialNumber from ibox", null);
                sn = (String) query.get(0).get("serialNumber");
            } catch (SQLException e) {
                LOGGER.error("静态数据库出问题了: {}, 读不到ems序列号", e.getMessage());
                System.exit(-3);
            }
            Map<Integer, Object> resultMap = querys.stream().collect(Collectors.toMap(
                    row -> (Integer) row.get("type"),
                    row -> (String) row.get("value")
            ));
            DeviceMessage dm = DeviceMessage.getInstance();
            dm.setEmsBoxDic(resultMap);
        }

        //todo attention
        DeviceMessage dm = DeviceMessage.getInstance();
        LOGGER.info("初始化emsBox参数");
        EmsBox emsBox = null;
        try {
            emsBox = initializeEmsBox(conn);
        } catch (SQLException e) {
            LOGGER.error("无法初始化emsBox: {}", e.getMessage());
            System.exit(-3);
        }
        dm.setEmsBox(emsBox);

        //todo attention 启动docker
        DockerExecutor docker = DockerExecutor.getInstance();
        JsonObject dockerConf = config.getJsonObject("docker");
        docker.initialize(dockerConf.getString("host"), dockerConf.getInteger("maxConnection"), dockerConf.getInteger("connectionTimeout"), dockerConf.getInteger("responseTimeout"));

        //todo attention  查询modbus设备
        //启动扫描的时候，有可能是重启，所以先删除以后的容器
        List<String> containerList = docker.getRunningContainerList();
        if (containerList != null && !containerList.isEmpty()) {
            for (int i = 0; i < containerList.size(); i++) {
                LOGGER.info("容器：{}", containerList.get(i));
                if (containerList.get(i).contains("dehumidifier")
                        || containerList.get(i).contains("liquidCooling")
                        || containerList.get(i).contains("mqtt")
                        || containerList.get(i).contains("modbus"))
                    docker.remove(containerList.get(i));
            }
        }

        //todo attention 扫描modbus设备
        LOGGER.info("开始scan device");
        try {
            scanModbusDevices(conn, sn);
        } catch (UnknownHostException e) {
            LOGGER.error("无法获取当前的ip地址: {}", e.getMessage());
            System.exit(-5);
        } catch (SQLException e) {
            LOGGER.error("数据库出问题了: {}", e.getMessage());
            System.exit(-3);
        }

        LOGGER.info("扫描设备完成");


        //todo attention 初始化mqtt
        try {
            initializeMqtt(conn, docker);
        } catch (SQLException | NullPointerException | InterruptedException e) {
            LOGGER.error("无法初始化mqtt模块: {}", e.getMessage());
            System.exit(-3);
        }

        List<Map<String, Object>> deviceInfo = null;
        try {
//            deviceInfo = conn.query("select serialNumber, deviceName, deviceType, ip, port, min, max, interval, pvPriority, batteryPowerLimited, utilityPowerLimited from device join address a on device.id = a.deviceId join power p on device.id = p.deviceId join load l on device.id = l.deviceId;", null);
            deviceInfo = conn.query("select serialNumber, deviceName, deviceType, ip, port from device join address a on device.id = a.deviceId;", null);
        } catch (SQLException e) {
            LOGGER.error("无法获取所有modbus设备信息: {}", e.getMessage());
            System.exit(-3);
        }

        LOGGER.info("modbus设备信息: {}", deviceInfo);
        Map<String, Map<String, Object>> deviceInfoMatch = new HashMap<>();
        for (Map<String, Object> elem : deviceInfo)
            deviceInfoMatch.put((String) elem.get("deviceType"), elem);

//         初始化设备
//        if (!deviceInfoMatch.containsKey("inverter")) {
//            LOGGER.error("数据库没有查到任何设备信息");
//            System.exit(-3);
//        }

        //todo attention初始化除湿机
        Map<String, Object> dhfInfo = deviceInfoMatch.get("dehumidifier");
        if (deviceInfoMatch.containsKey("dehumidifier")) {
            Dehumidifier dehumidifier = null;
            try {
                dehumidifier = (Dehumidifier) initializeCommandDevice("dehumidifier", config.getString("sql"), dhfInfo);
            } catch (Exception e) {
                LOGGER.error("无法初始化除湿机: {}", e.getMessage());
            }
            if (dehumidifier != null) {
                LOGGER.info("除湿机序列号：{}, ", dehumidifier.getSerialNumber());
                dm.addDehumidifier(dehumidifier.getSerialNumber(), dehumidifier);
            }
        }

        //todo attention初始化液冷机
        Map<String, Object> lcInfo = deviceInfoMatch.get("liquidCooling");
        if (deviceInfoMatch.containsKey("liquidCooling")) {
            LiquidCooling liquidCooling = null;
            try {
                liquidCooling = (LiquidCooling) initializeCommandDevice("liquidCooling", config.getString("sql"), lcInfo);
            } catch (Exception e) {
                LOGGER.error("无法初始化液冷机: {}", e.getMessage());
            }
            if (liquidCooling != null) {
                LOGGER.info("液冷机序列号：{}, ", liquidCooling.getSerialNumber());
                dm.addLiquidCooling(liquidCooling.getSerialNumber(), liquidCooling);
            }
        }


        // 初始化逆变器
//        Map<String, Object> invInfo = deviceInfoMatch.get("inverter");
//        if (deviceInfoMatch.containsKey("inverter")) {
//            Inverter inv = null;
//            try {
//                RealTimeDevice device = initializeRealTimeDevice("inverter", config.getString("sql"), invInfo);
//                inv = device.getDeviceName().compareTo("solinteg") == 0 ? (SolintegInverter) device : (Inverter) device;
//            } catch (Exception e) {
//                LOGGER.error("无法初始化逆变器: {}", e.getMessage());
//            }
//
//            if (inv != null) {
//                // 初始化电池
//                Map<String, Object> batteryInfo = deviceInfoMatch.get("battery");
//                Battery battery = null;
//                try {
//                    battery = (Battery) initializeRealTimeDevice("battery", config.getString("sql"), batteryInfo);
//                } catch (Exception e) {
//                    LOGGER.warn("无法初始化电池设备: {}", e.getMessage());
//                }
//
//                if (battery != null) {
//                    inv.setBatteryDevice(battery);
//                    inc.addBattery(battery.getSerialNumber(), battery);
//                }
//                inc.addInverter(inv.getSerialNumber(), inv);
//            }
//        }


        // 初始化后端
        final Vertx vertx = Vertx.vertx();
        int instances = config.getInteger("instances");
        LOGGER.info("部署{}个实例", instances);
        // 发布standard
        vertx.deployVerticle(GWapi::new, new DeploymentOptions()
                .setConfig(new JsonObject()
                        .put("database", config.getJsonObject("database"))
                        .put("address", config.getJsonObject("address")))
                .setInstances(instances), ar -> {
            if (ar.failed()) {
                LOGGER.error("实例部署失败, 请检查相关配置是否正确");
                System.exit(-6);
            }
        });

        //todo attention 给can设备下发指令用的
        CanServer canServer = CanServer.getInstance();
        try {
            canServer.start();
        } catch (IOException e) {
        }

        //wifi连接的服务
//        WifiConnectorServer wifiServer = WifiConnectorServer.getInstance();
//        try {
//            wifiServer.start();
//        } catch (IOException e) {
//            LOGGER.info("can协议服务接收失效");
//        }


        // 创建并启动一个普通线程
//        SQLiteConnection finalConn = conn;
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        // 调用获取 wifi 信号强度的方法
//                        WifiConfig.storeWifiStrength(finalConn);
//                    } catch (SQLException e) {
//                        LOGGER.info("wifi信号强度获取失败: " + e.getMessage());
//                    }
//
//                    // 每隔 1 分钟执行一次
//                    try {
//                        Thread.sleep(10000);  // 60000 毫秒 = 1 分钟
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();  // 如果线程被中断，恢复中断状态
//                        LOGGER.info("线程被中断");
//                    } finally {
//                        try {
//                            if (finalConn != null) {
//                                finalConn.close();  // 或 close，具体方法视 SQLiteConnection 的实现而定
//                                LOGGER.info("数据库连接已关闭");
//                            }
//                        } catch (Exception e) {
//                            LOGGER.error("关闭数据库连接时发生错误: " + e.getMessage());
//                        }
//                    }
//                }
//            }
//        });
//
//        // 将线程设为后台线程，这样主程序退出时它也会自动停止
//        thread.setDaemon(true);
//        thread.start();  // 启动线程


        try {
            conn.close();
            // 强制触发GC
            conn = null;
        } catch (SQLException e) {
            LOGGER.warn("关闭静态数据库错误");
        }

        /**
         * 连接上位机485串口
         * 串口名，例如 "COM2"
         */
//        String portName = "/dev/ttyUSB0";
//
//        // 创建串口对象
//        SerialPort serialPort = new SerialPort(portName);
//
//        try {
//            // 打开串口
//            serialPort.openPort();
//            // 设置串口参数：波特率、数据位、停止位和校验位
//            serialPort.setParams(
//                    SerialPort.BAUDRATE_115200, // 波特率
//                    SerialPort.DATABITS_8,    // 数据位
//                    SerialPort.STOPBITS_1,    // 停止位
//                    SerialPort.PARITY_NONE    // 无校验
//            );
//            // 添加串口事件监听器，监听数据可用事件
//            serialPort.addEventListener(new SerialPortEventListener() {
//                @Override
//                public void serialEvent(SerialPortEvent event) {
//                    if (event.isRXCHAR()) { // 数据可用事件
//                        try {
//                            // 读取串口缓冲区中的数据
//                            byte[] receivedData = serialPort.readBytes(event.getEventValue());
//
//                            // 打印接收到的数据（十六进制格式）
//                            System.out.println("Received data:");
//                            for (byte b : receivedData) {
//                                System.out.printf("%02X ", b);
//                            }
//
//                            // 在这里可以解析 MODBUS 协议数据并处理
//                            Api.handleModbusData(serialPort, receivedData);
//
//                        } catch (SerialPortException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            });
//
//            System.out.println("Waiting for data on " + portName + "...");
//
//        } catch (SerialPortException e) {
//            LOGGER.info("连接失败");
//        }

    }

}
