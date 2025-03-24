package com.slenergy.gateway.api.server;

import com.slenergy.gateway.api.server.can.CanServer;
import com.slenergy.gateway.api.server.device.CommandDevice;
import com.slenergy.gateway.api.server.device.EmsBox;
import com.slenergy.gateway.api.server.device.IBox;
import com.slenergy.gateway.api.server.device.RealTimeDevice;
import com.slenergy.gateway.api.server.device.chargingPile.ChargingPile;
import com.slenergy.gateway.api.server.device.heatPump.HeatPump;
import com.slenergy.gateway.api.server.device.inverter.Inverter;
import com.slenergy.gateway.api.server.schema.*;
import com.slenergy.gateway.api.server.util.CRC16;
import com.slenergy.gateway.api.server.util.HexUtils;
import com.slenergy.gateway.api.server.wifi.WifiConfig;
import com.slenergy.gateway.database.influxdb.InfluxConnection;
import com.slenergy.gateway.database.influxdb.Piece;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.java.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Array;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code class} {@code GWapi} description
 * 网关系统后端API服务
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-12-11
 * @since 1.0-SNAPSHOT
 */
public class GWapi extends AbstractVerticle {

    private final static Logger LOGGER = LogManager.getLogger(GWapi.class);

    private final static Set<String> FILTER_EXPRESSION = Set.of("_measurement", "tag", "_time", "deviceName", "deviceSN", "subTag");

    // 定义除湿机需要拆分的寄存器读取10
    private static final Set<String> DEHUMIDIFIER_ADDRESSES = Set.of("7594", "7595", "7596", "7597");
    private final static String QUERY_ALL_BY_DEVICE = """
              from(bucket: "%s")
          |> range(start: %s, stop: %s)
          |> filter(fn: (r) => r._measurement == "%s" and r.deviceName == "%s" and r.deviceSN == "%s")
          |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
          |> sort(columns: ["_time"], desc: true)
          |> group(columns: ["deviceName", "deviceSN", "tag"])
          """;

    private final static String QUERY_ALL_BY_EMS_DEVICE = """
            from(bucket: "%s")
        |> range(start: %s, stop: now())
        |> filter(fn: (r) => r._measurement == "%s" and r.subTag == "%s")
        |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
        |> sort(columns: ["_time"], desc: true)
        |> group(columns: ["deviceName", "deviceSN", "tag"])
        |> limit(n: 1)
        """;

    private final static String QUERY_BMS_MONOMER = """
            from(bucket: "%s")
        |> range(start: -500ms, stop: now())
        |> filter(fn: (r) => r._measurement == "%s")
        |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
        |> sort(columns: ["_time"], desc: true)
        |> group(columns: ["deviceName", "deviceSN", "tag"])
        |> limit(n: 1)
        """;

    private final static String QUERY_BMS_DEVICE = """
            from(bucket: "ibox")
        |> range(start: -1m, stop: now())
        |> filter(fn: (r) => r._measurement == "bms" and r.deviceName == "slenergy" and r.deviceSN == "E1YYWWDAQQQ99GBAV" and r.subTag == "%s")
        |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
        |> sort(columns: ["_time"], desc: true)
        |> group(columns: ["deviceName", "deviceSN", "tag"])
        |> limit(n: 1)
        """;

    private InfluxConnection connection;

    @Override
    public void start() throws Exception {
        JsonObject config = config();
        LOGGER.info("配置参数: {}", config);
        JsonObject db = config.getJsonObject("database");
        // 初始化数据库对象
        String bucket = db.getString("bucket");
        connection = new InfluxConnection(db.getString("host"), db.getString("token"), db.getString("org"), bucket);
        while (!connection.ping()) {
            LOGGER.error("无法ping通实时数据库，请检查连接是否正确");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                LOGGER.warn("线程{}无法睡眠, {}", getClass().getName(), e.getMessage());
            }
        }

        final HttpServer server = this.vertx.createHttpServer(new HttpServerOptions()
                .setLogActivity(true));
        //在不同路由之间共享
        LocalMap<String, String> sharedMap = vertx.sharedData().getLocalMap("processMap");
        sharedMap.put("download", "00");
        sharedMap.put("upgrate", "00");
        // 创建路由对象
        final Router router = Router.router(this.vertx);
        router.route().handler(BodyHandler.create());

        //记录进度
//        AtomicReference<String> process = new AtomicReference<>("00");

        router.post("/data/realtime/write").consumes("application/json").handler(ctx -> {
            RealTimeData data = ctx.body().asPojo(RealTimeData.class);
//            LOGGER.info("写入的实时数据: {}", data);
            saveDevice(data);
            List<Piece> pieces = new ArrayList<>();
            for (TimeSeriesData timeseries : data.timeSeries()) {
                Map<String, String> tags = new HashMap<>(Map.of("deviceSN", data.serialNumber(), "deviceName", data.deviceName()));
                if (timeseries.tag() != null)
                    tags.put("tag", timeseries.tag());
                if (timeseries.subTag() != null)
                    tags.put("subTag", timeseries.subTag());

                pieces.add(new Piece(
                        data.deviceType(),
                        data.timestamp(),
                        transferTimeUnit(data.timeUnit()),
                        tags,
                        new HashMap<>(Map.of(timeseries.field(), timeseries.value()))
                ));
            }
            connection.insert(pieces, transferTimeUnit(data.timeUnit()));
            ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), null));
        });

        router.post("/data/realtime/read").consumes("application/json").handler(ctx -> {
            DeviceRealTimeData data = ctx.body().asPojo(DeviceRealTimeData.class);
            String stop = data.stop() == null ? "now()" : data.stop();
            List<Map<String, Object>> results = connection.query(String.format(QUERY_ALL_BY_DEVICE, db.getString("bucket"), data.start(), stop, data.measurement(), data.deviceName(), data.deviceSN()));
            LOGGER.info("回复的数据：{}", results);
            ctx.json(new ResponseResult<>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), results));
        });

        router.post("/data/realtime/read/all").consumes("application/json").handler(ctx -> {
            AllDevice data = ctx.body().asPojo(AllDevice.class);
            int interval = data.interval();
            JsonObject results = new JsonObject().put("ibox", MessageQueue.getInstance().getIBoxSerialization().put("systemTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).put("timestamp", System.currentTimeMillis() / 1000));
            results.put("inverters", getInverters(interval)).put("heatPumps", getHeatPumps(interval)).put("chargingPiles", getChargingPiles(interval));
            LOGGER.info("回复数据: {}", results);
            ctx.json(new ResponseResult<>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), results));
        });

        //ems读取所有设备信息
        router.post("/data/realtime/read/ems/device").consumes("application/json").handler(ctx -> {
            //封装数据区段    WM1A23010318
//            DeviceMessage dm = DeviceMessage.getInstance();
//            List<Dehumidifier> dehumidifiers = dm.getDehumidifiers();
//            Dehumidifier dehumidifier = dehumidifiers.get(0);
//
//            String bmsSerialNumber = dm.getCanDevices("bms");
//            String bmsRunTimeInformation = null;
//            String bmsConfigInformation = null;
//            if (bmsSerialNumber != null) {
//                bmsRunTimeInformation = getInfoData(db, "bms", "slenergy", bmsSerialNumber, "runtimeInformation");
//                bmsConfigInformation = getInfoData(db, "bms", "slenergy", bmsSerialNumber, "configurationInformation");
//            }
//            JsonObject bmsJson = new JsonObject().put("runtimeInfo", bmsRunTimeInformation).put("configInfo", bmsConfigInformation);
            //查询实时数据和设置数据
            //除湿机
            String dhRuntimeInfoData = getInfoData(db, "dehumidifier", "runtimeInformation", "-30s");
            String dhConfigInfoData = getInfoData(db, "dehumidifier", "configurationInformation","-30s");
            //bms
            String bmsRuntimeInfoData = getInfoData(db, "bms", "runtimeInformation", "-30s");
            String bmsConfigInfoData = getInfoData(db, "bms", "configurationInformation", "-30s");
            //bms单体信息
//            String bmsMonomerInfoData = getInfoData(db, "bms_monomer", "runtimeInformation");
            String bmsMonomerInfoData = getbmsMonomerInfoData(db, "runtimeInformation");
            //dido信息
            String ioModuleRuntimeInfoData = getInfoData(db, "IOmodule", "runtimeInformation", "-30s");
            String ioModuleConfigInfoData = getInfoData(db, "IOmodule", "configurationInformation", "-30s");
            //液冷机信息
            String liquidCoolingRuntimeInfoData = getInfoData(db, "liquidCooling", "runtimeInformation", "-30s");
            String liquidCoolingConfigInfoData = getInfoData(db, "liquidCooling", "configurationInformation", "-30s");
            //pcs信息
            String pcsRuntimeInfoData = getInfoData(db, "pcs", "runtimeInformation", "-3m");
            String pcsConfigInfoData = getInfoData(db, "pcs", "configurationInformation", "-3m");
            //北斗
            String beidouRuntimeInfoData = getInfoData(db, "beidou", "runtimeInformation", "-30s");
            String beidouConfigInfoData = getInfoData(db, "beidou", "configurationInformation", "-30s");
            //空调
            String airRuntimeInfoData = getInfoData(db, "AirConditioner", "runtimeInformation", "-30s");
            String airConfigInfoData = getInfoData(db, "AirConditioner", "configurationInformation", "-30s");

            //数据采集器信息
            DeviceMessage dm = DeviceMessage.getInstance();
            EmsBox emsBox = dm.getEmsBox();
            HexFormat hexFormat = HexFormat.of();
            String collectorSNHexStr = hexFormat.formatHex(emsBox.getSerialNumber().getBytes());
            String collectorSNHexStrFormat = padLeft(collectorSNHexStr, 30);
            int emsBoxNum = dm.getEmsBoxNum();
            String eboxNumFormat = HexUtils.hexBytesToHexString(HexUtils.intToHexBytes(emsBoxNum));
            String emsBoxDicList = dm.getEmsBoxDicList(null);
            StringBuilder builder = new StringBuilder();
            StringBuilder eboxStr = builder.append(collectorSNHexStrFormat).append(eboxNumFormat).append("00").append(emsBoxDicList);


            JsonObject totalJson = new JsonObject();
            JsonObject dhJson = new JsonObject().put("runtimeInfo", dhRuntimeInfoData).put("configInfo", dhConfigInfoData);
            JsonObject bmsJson = new JsonObject().put("runtimeInfo", bmsRuntimeInfoData).put("configInfo", bmsConfigInfoData);
            JsonObject bmsMonomerJson = new JsonObject().put("runtimeInfo", bmsMonomerInfoData);
            JsonObject ioModuleJson = new JsonObject().put("runtimeInfo", ioModuleRuntimeInfoData).put("configInfo", ioModuleConfigInfoData);
            JsonObject liquidCoolingJson = new JsonObject().put("runtimeInfo", liquidCoolingRuntimeInfoData).put("configInfo", liquidCoolingConfigInfoData);
            JsonObject pcsJson = new JsonObject().put("runtimeInfo", pcsRuntimeInfoData).put("configInfo", pcsConfigInfoData);
            JsonObject bdJson = new JsonObject().put("runtimeInfo", beidouRuntimeInfoData).put("configInfo", beidouConfigInfoData);
            JsonObject airJson = new JsonObject().put("runtimeInfo", airRuntimeInfoData).put("configInfo", airConfigInfoData);

            JsonObject ebox = new JsonObject().put("runtimeInfo", eboxStr.toString());



            //查询实时数据和设置数据
//            String dhRuntimeInfoData = getInfoData(db, dehumidifier.getDeviceType(), dehumidifier.getDeviceName(), dehumidifier.getSerialNumber(), "runtimeInformation");
//            String dhConfigInfoData = getInfoData(db, dehumidifier.getDeviceType(), dehumidifier.getDeviceName(), dehumidifier.getSerialNumber(), "configurationInformation");

            totalJson.put("dehumidifier", dhJson)
                    .put("bms", bmsJson)
                    .put("bms_monomer", bmsMonomerJson)
                    .put("ioModule", ioModuleJson)
                    .put("liquidCooling", liquidCoolingJson)
                    .put("pcs", pcsJson)
                    .put("beidou", bdJson)
                    .put("air", airJson)
                    .put("ebox", ebox);
//            totalJson.put("bms", bmsJson);
            LOGGER.info("回复数据:{}", totalJson);
            ctx.json(new ResponseResult<>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), totalJson));
        });

        //ems完全透传功能
        router.post("/device/ems/command").consumes("application/text").handler(ctx -> {
            //数据区数据 数据采集器序列号(30) + 设备序列号（30） + 透传数据区长度（2） + 透传数据区
            JsonArray jsonArray = ctx.body().asJsonArray();
            LOGGER.info("route: /device/command, get parameters: {}", jsonArray);
            byte[] bytes = new byte[jsonArray.size()];
            for (int i = 0; i < jsonArray.size(); i++) {
                bytes[i] = (byte) ((int) jsonArray.getList().get(i) & 0xff);
            }
            byte[] dataByte = Arrays.copyOfRange(bytes, 8, bytes.length);
            //数据采集器序列号
            String collectorSn = HexUtils.hexByteToAscii(HexUtils.extractByte(dataByte, 0, 30));
            //设备序列号
            String deviceSn = HexUtils.hexByteToAscii(HexUtils.extractByte(dataByte, 30, 30));
            //数据区长度
            byte[] lenHex = Arrays.copyOfRange(dataByte, 60, 62);
            int len = HexUtils.hexByteToInt(lenHex[0], lenHex[1]);
            //透传数据区
            byte[] transParentDataBytes = Arrays.copyOfRange(dataByte, 62, dataByte.length);
//            透传
            DeviceMessage dm = DeviceMessage.getInstance();
            CommandDevice device = dm.getCommandDevice(deviceSn);

            String result = null;
            String message = HexUtils.hexBytesToHexString(transParentDataBytes);
            LOGGER.info("从云平台接收到的指令: {}", message);
            //etu
            try {
                result = CanServer.getInstance().sendMessageAndReceive(message);
                LOGGER.info("接收到下位机的返回: {}", result);
            } catch (Exception e) {
                LOGGER.info("发送失败：{}", e.getMessage());
            }
//            if (device == null) {
//                String functionCode = message.substring(2, 4);   // 功能码（应为10）
//                if (functionCode.equals("03")) {
//                    try {
//                        result = CanServer.getInstance().sendMessageAndReceive(message);
//                        LOGGER.info("接收到can的返回: {}", result);
//                    } catch (Exception e) {
//                        LOGGER.info("发送失败：{}", e.getMessage());
//                    }
//                } else {
//                    //bms的序列号和时间必须是10，地址范围20001-20009、23020-23022  十六进制就是4E21-4E29、59EC-59EE
//                    String fisrtAddress = message.substring(4, 8);
//                    if ("4E21".equalsIgnoreCase(fisrtAddress) || "59EC".equalsIgnoreCase(fisrtAddress)) {
//                        try {
//                            result = CanServer.getInstance().sendMessageAndReceive(message);
//                            LOGGER.info("接收到can的返回: {}", result);
//                        } catch (Exception e) {
//                            LOGGER.info("发送失败：{}", e.getMessage());
//                        }
//                    } else {
//                        boolean flag = true;
//                        StringBuilder modbus10Builder = new StringBuilder();
//                        List<String> modbus06 = convertTo06FunctionCodes(message);
//                        if (modbus06 != null && modbus06.size() != 0) {
//                            for (int i = 0; i < modbus06.size(); i++) {
//                                try {
//                                    String modbus06Ret = CanServer.getInstance().sendMessageAndReceive(modbus06.get(i));
//                                    if (modbus06Ret.substring(2, 4).equals("86")) flag = false;
//                                    LOGGER.info("接收到can的返回: {}", modbus06Ret);
//                                } catch (Exception e) {
//                                    LOGGER.info("发送失败：{}", e.getMessage());
//                                }
//                            }
//                        }
//
//                        if (flag) {
//                            String data = message.substring(0, 12);
//                            byte[] crcDataByte = HexUtils.hexStringToByteArray(data);
//                            byte[] crc16 = CRC16.crc16CCITT(crcDataByte);
//                            String crcStr = HexUtils.hexBytesToHexString(crc16);
//                            result = modbus10Builder.append(data).append(crcStr).toString();
//                        } else {
//                            result = "0190018DC0";
//                        }
//                    }
//                }
//            } else {
//                //modbus设备
//                try {
//                    boolean flag = true;
//                    StringBuilder modbus10Builder = new StringBuilder();
//                    String function = message.substring(2, 4);
//                    String firstAddress = message.substring(4, 8);
//                    //除湿机转成06下发
//                    if ("10".equals(function) && DEHUMIDIFIER_ADDRESSES.contains(firstAddress)) {
//                        List<String> modbus6List = convertTo06FunctionCodes(message);
//                        if (modbus6List != null && modbus6List.size() != 0) {
//                            for (int i = 0; i < modbus6List.size(); i++) {
//                                String modbus6Ret = device.sendEmsCommand(modbus6List.get(i));
//                                if ("86".equals(modbus6Ret.substring(2, 4))) flag = false;
//                                LOGGER.info("接收到除湿机的返回: {}", modbus6Ret);
//                            }
//                        }
//
//                        if (flag) {
//                            String data = message.substring(0, 12);
//                            byte[] crcDataByte = HexUtils.hexStringToByteArray(data);
//                            byte[] crc16 = CRC16.crc16CCITT(crcDataByte);
//                            String crcStr = HexUtils.hexBytesToHexString(crc16);
//                            result = modbus10Builder.append(data).append(crcStr).toString();
//                        } else {
//                            result = "0190018DC0";
//                        }
//                    } else {
//                        LOGGER.info("云平台给设备透传：{}", message);
////                    result = device.sendEmsCommand(HexUtils.hexBytesToHexString(message.getBytes()));
//                        result = device.sendEmsCommand(message);
//                        LOGGER.info("收到{}的返回：{}", device.getSerialNumber(), result);
//                    }
//                    } catch(Exception e){
//                        e.getMessage();
//                        LOGGER.warn("无法给设备下发指令: {}", e.getMessage());
//                    }
//            }
            ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), result));
        });
        
        //修改box的属性
        router.post("/device/box/command").consumes("application/text").handler(ctx -> {
            JsonArray jsonArray = ctx.body().asJsonArray();
            LOGGER.info("route: /device/command, get parameters: {}", jsonArray);
            byte[] dataByte = new byte[jsonArray.size()];
            for (int i = 0; i < jsonArray.size(); i++) {
                dataByte[i] = (byte) ((int) jsonArray.getList().get(i) & 0xff);
            }
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

            boolean isSucess = true;
            //80 固件升级功能
            Set<Integer> keySet = settingMap.keySet();
            if (keySet.contains(80)) {
                String updateCommand = settingMap.get(80);
                try {
                    LOGGER.info("发送出去的升级地址: {}", updateCommand);
                    CanServer.getInstance().sendMessageAndReceive(updateCommand);
                } catch (Exception e) {
                    LOGGER.warn("无法给设备下发指令: {}", e.getMessage());
                }
//                });
//                ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), "ok"));
            }
            //30 时区更改
            if (keySet.contains(30)) {
                String timeZone = settingMap.get(30);
                LOGGER.info("修改的时区: {}", timeZone);
                //修改配置
//                setTimezone(timeZone);
                //todo 暂时都改成ok
                ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), "ok"));
            }
            ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), "ok"));
        });

        //查找box的属性
        router.post("/device/box/read").consumes("application/text").handler(ctx -> {
            JsonArray jsonArray = ctx.body().asJsonArray();
            LOGGER.info("route: /device/command, get parameters: {}", jsonArray);
            byte[] dataByte = new byte[jsonArray.size()];
            String dataStr = HexUtils.hexBytesToHexString(dataByte);
            for (int i = 0; i < jsonArray.size(); i++) {
                dataByte[i] = (byte) ((int) jsonArray.getList().get(i) & 0xff);
            }
//            String dataStr = ctx.body().asString();
//            byte[] dataByte = HexUtils.hexStringToByteArray(dataStr);
            //采集器序列号（30） + 参数编号个数（2） + 参数编号列表+CRC（2）
            //数据采集器序列号
            String collectorSn = HexUtils.hexByteToAscii(HexUtils.extractByte(dataByte, 0, 30));
            //参数编号个数
            byte[] countHex = HexUtils.extractByte(dataByte, 30, 2);
            int count = HexUtils.hexByteToInt(countHex[1], countHex[0]);
            //参数编号列表
            byte[] parameterBytes = HexUtils.extractByte(dataByte, 32, count * 2);
            int[] parameter = HexUtils.hexBytesToInt(parameterBytes);

            StringBuilder result = new StringBuilder();
            result.append(dataStr.substring(0, 64));
            //状态码 0x00成功 0x01不合法
            result.append("00");
            DeviceMessage dm = DeviceMessage.getInstance();
            //判断是否包括31 系统时间属性
            boolean contains31 = Arrays.stream(parameter).anyMatch(x -> x == 31);
            if (contains31) {
                SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = new Date(System.currentTimeMillis());
                String currentTime = formatter.format(date);
                dm.setEmsBoxDic((Map<Integer, Object>) Map.of().put(31, currentTime));
                LOGGER.info("获取的当前时间：{}", currentTime);
            }
            //判断是否包括30 系统时区属性
            boolean contains30 = Arrays.stream(parameter).anyMatch(x -> x == 30);
            String timezone = null;
            if (contains30) {
                try {
                    // 指定文件路径
                    String filePath = "/etc/timezone";
                    BufferedReader reader = new BufferedReader(new FileReader(filePath));
                    String line = reader.readLine();
                    timezone = line.trim(); // 移除首尾空白字符
                    reader.close();
                    LOGGER.info("读取的Timezone: {}", timezone);
                } catch (IOException e) {
                    LOGGER.info("读取失败{}, 原因: {}", e.getMessage(), e.getStackTrace());
                }
                dm.setEmsBoxDic((Map<Integer, Object>) Map.of().put(30, timezone));
            }
            String emsBoxDicList = dm.getEmsBoxDicList(parameter);
            result.append(emsBoxDicList);
            LOGGER.info("查询的ems设置项返回：{}", result);
            ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), result.toString()));
        });

        //测试路由
        router.post("/test").consumes("application/text").handler(ctx ->{
            try {
                LOGGER.info("云平台给设备透传：{}", "0103759400041fe9");
//                    result = device.sendEmsCommand(HexUtils.hexBytesToHexString(message.getBytes()));
                DeviceMessage dm = DeviceMessage.getInstance();
                CommandDevice device = dm.getCommandDevice("1");
                String s = device.sendEmsCommand("0103759400041fe9");
                LOGGER.info("收到{}的返回：{}", device.getSerialNumber(), s);
            } catch (Exception e) {
                LOGGER.warn("无法给设备下发指令: {}", e.getMessage());
            }
        });

        //接收来自Can设备上报的进度
        router.post("/update/progress").handler(ctx -> {
            String processInfo = ctx.body().asString();
            String type = processInfo.substring(0, 1);
            String sn = processInfo.substring(1, processInfo.length() - 2);
            String process = processInfo.substring(processInfo.length() - 2, processInfo.length());
            sharedMap.put("sn", sn);
            if ("A".equals(type)) {
                sharedMap.put("download", process);
            } else {
                sharedMap.put("upgrate", process);
            }
//            sharedMap.put("process", newProcess);
            LOGGER.info("设备sn: {}", sn);
            LOGGER.info("download 获取到的进度, {}", sharedMap.get("download"));
            LOGGER.info("upgrate 获取到的进度, {}", sharedMap.get("upgrate"));
        });

//        上报固件升级的进度
        router.post("/device/update/getProcess").consumes("application/octet-stream").handler(ctx -> {
            HexFormat hexFormat = HexFormat.of();
            DeviceMessage dm = DeviceMessage.getInstance();
            EmsBox emsBox = dm.getEmsBox();
            //采集器序列号
            String collectorSNHexStr = hexFormat.formatHex(emsBox.getSerialNumber().getBytes());
//            String collectorSNHexStr = hexFormat.formatHex("EMS123467".getBytes());
            String collectorSNHexStrFormat = padLeft(collectorSNHexStr, 30);
            StringBuilder builder = new StringBuilder();
            //下载进度
            String downloadProcess = sharedMap.get("download");
            //升级进度
            String upgrateProcess = sharedMap.get("upgrate");
            LOGGER.info("设备sn: {}", sharedMap.get("sn"));
            LOGGER.info("download 获取到的进度, {}", sharedMap.get("download"));
            LOGGER.info("upgrate 获取到的进度, {}", sharedMap.get("upgrate"));
            //拼接下载进度
            if ("00".equals(upgrateProcess) && !"64".equals(downloadProcess)) {
                LOGGER.info("正在下载。。。。。。");
                builder.append(collectorSNHexStrFormat).append(sharedMap.get("sn")).append("A1").append(downloadProcess);
            }
            //拼接升级进度
            if ("64".equals(downloadProcess)) {
                LOGGER.info("正在升级。。。。。。");
                builder.append(collectorSNHexStrFormat).append(sharedMap.get("sn")).append(upgrateProcess);

            }
            //清处共享变量
            if ("64".equals(upgrateProcess) && "64".equals(downloadProcess)) {
                LOGGER.info("下载完成。。。。。。");
                sharedMap.put("sn", "00");
                sharedMap.put("download", "00");
                sharedMap.put("upgrate", "00");
                LOGGER.info("设备sn: {}", sharedMap.get("sn"));
                LOGGER.info("download 获取到的进度, {}", sharedMap.get("download"));
                LOGGER.info("upgrate 获取到的进度, {}", sharedMap.get("upgrate"));
            }
//            String currentProcess = sharedMap.get("process");
//            LOGGER.info("获取到的进度, {}", currentProcess);
//            StringBuilder result = builder.append(collectorSNHexStrFormat).append(currentProcess);
//
//            //当进度为100的时候，发送完100就清零
//            if (currentProcess != null) {
//                String flag = currentProcess.substring(currentProcess.length() - 2, currentProcess.length());
//                if ("89".equals(flag)) currentProcess = "00";
//            } else {
//                currentProcess = "00";
//            }
            LOGGER.info("返回给mqtt的进度: {}", builder.toString());
            ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), builder.toString()));
        });

        router.post("/device/ems/getConfig").consumes("application/json").handler(ctx -> {
            LOGGER.info("上报设置类数据");
            //查询设置数据
            //除湿机
            String dhConfigInfoData = getInfoData(db, "dehumidifier", "configurationInformation","-30s");
            //bms
            String bmsConfigInfoData = getInfoData(db, "bms", "configurationInformation", "-30s");
            //dido信息
            String ioModuleConfigInfoData = getInfoData(db, "IOmodule", "configurationInformation", "-30s");
            //液冷机信息
            String liquidCoolingConfigInfoData = getInfoData(db, "liquidCooling", "configurationInformation", "-30s");
            //pcs信息
            String pcsConfigInfoData = getInfoData(db, "pcs", "configurationInformation", "-3m");
            //空调信息
            String airConfigInfoData = getInfoData(db, "AirConditioner", "configurationInformation", "-30s");
            //北斗信息
            String beidouConfigInfoData = getInfoData(db, "beidou", "configurationInformation", "-30s");
            //数据采集器信息
            DeviceMessage dm = DeviceMessage.getInstance();
            EmsBox emsBox = dm.getEmsBox();
            HexFormat hexFormat = HexFormat.of();
            String collectorSNHexStr = hexFormat.formatHex(emsBox.getSerialNumber().getBytes());
            String collectorSNHexStrFormat = padLeft(collectorSNHexStr, 30);
            int emsBoxNum = dm.getEmsBoxNum();
            String eboxNumFormat = HexUtils.hexBytesToHexString(HexUtils.intToHexBytes(emsBoxNum));
            String emsBoxDicList = dm.getEmsBoxDicList(null);
            StringBuilder builder = new StringBuilder();
            StringBuilder eboxStr = builder.append(collectorSNHexStrFormat).append(eboxNumFormat).append("00").append(emsBoxDicList);


            JsonObject totalJson = new JsonObject();
            JsonObject dhJson = new JsonObject().put("runtimeInfo", null).put("configInfo", dhConfigInfoData);
            JsonObject bmsJson = new JsonObject().put("runtimeInfo", null).put("configInfo", bmsConfigInfoData);
            JsonObject bmsMonomerJson = new JsonObject().put("runtimeInfo", null);
            JsonObject ioModuleJson = new JsonObject().put("runtimeInfo", null).put("configInfo", ioModuleConfigInfoData);
            JsonObject liquidCoolingJson = new JsonObject().put("runtimeInfo", null).put("configInfo", liquidCoolingConfigInfoData);
            JsonObject pcsJson = new JsonObject().put("runtimeInfo", null).put("configInfo", pcsConfigInfoData);
            JsonObject airJson = new JsonObject().put("runtimeInfo", null).put("configInfo", airConfigInfoData);
            JsonObject beidouJson = new JsonObject().put("runtimeInfo", null).put("configInfo", beidouConfigInfoData);
            JsonObject ebox = new JsonObject().put("runtimeInfo", eboxStr.toString());

            //查询实时数据和设置数据
            //            String dhRuntimeInfoData = getInfoData(db, dehumidifier.getDeviceType(), dehumidifier.getDeviceName(), dehumidifier.getSerialNumber(), "runtimeInformation");
            //            String dhConfigInfoData = getInfoData(db, dehumidifier.getDeviceType(), dehumidifier.getDeviceName(), dehumidifier.getSerialNumber(), "configurationInformation");

            totalJson.put("dehumidifier", dhJson)
                    .put("bms", bmsJson)
                    .put("bms_monomer", bmsMonomerJson)
                    .put("ioModule", ioModuleJson)
                    .put("liquidCooling", liquidCoolingJson)
                    .put("pcs", pcsJson)
                    .put("air", airJson)
                    .put("beidou", beidouJson)
                    .put("ebox", ebox);
            //            totalJson.put("bms", bmsJson);
            LOGGER.info("回复数据:{}", totalJson);
            ctx.json(new ResponseResult<>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), totalJson));
        });

        router.get("/").handler(ctx -> ctx.json(new ResponseResult<String>(ResponseEnum.SUCCESS.getCode(), ResponseEnum.SUCCESS.getMessage(), null)));

        server.requestHandler(router);
        JsonObject address = config.getJsonObject("address");
        LOGGER.info("开启服务实例, 访问地址: http://{}:{}", address.getString("host"), address.getInteger("port"));
        server.listen(address.getInteger("port"), address.getString("host"));
    }

//    @NotNull
    private String getInfoData(JsonObject db, String deviceType, String infoType, String time) {
        List<Map<String, Object>> info = connection.query(String.format(QUERY_ALL_BY_EMS_DEVICE, db.getString("bucket"), time, deviceType, infoType));
        if (info.size() ==  0) {
            LOGGER.info("查询不到{}的{}信息", deviceType, infoType);
            return null;
        }
        String serialNumber = (String) info.get(0).get("deviceSN");
        Map<String, Map<String, Object>> runtimeMap = splitMap(info.get(0), FILTER_EXPRESSION);
        return convertDataIntoSegment(runtimeMap.get("numericKeys"), serialNumber, deviceType);
    }

    private String getbmsMonomerInfoData(JsonObject db, String infoType) {
        List<Map<String, Object>> info = connection.query(String.format(QUERY_BMS_MONOMER, db.getString("bucket"), "bms_monomer"));
        if (info.size() ==  0) {
            LOGGER.info("查询不到bms_monomer的{}信息", infoType);
            return null;
        }
        String serialNumber = (String) info.get(0).get("deviceSN");
        return joinBmsMonomerData(info, serialNumber);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        connection.close();
    }

    private TimeUnit transferTimeUnit(String unit) {
        if (unit == null)
            return TimeUnit.MILLISECONDS;

        TimeUnit ret = TimeUnit.MILLISECONDS;
        switch (unit.toLowerCase()) {
            case "s" -> ret = TimeUnit.SECONDS;
            case "us" -> ret = TimeUnit.MICROSECONDS;
            case "ns" -> ret = TimeUnit.NANOSECONDS;
        }
        return ret;
    }

    private void saveDevice(RealTimeData data) {
        MessageQueue inc = MessageQueue.getInstance();
        RealTimeDevice device = inc.getRealTimeDevice(data.serialNumber());
        switch (data.deviceType()) {
            case "inverter":
                saveInverter((Inverter) device, data.timestamp(), data.timeSeries());
                break;
            case "heatPump":
                saveHeatPump((HeatPump) device, data.timestamp(), data.timeSeries());
                break;
            case "chargingPile":
                saveChargingPile((ChargingPile) device, data.timestamp(), data.timeSeries());
                break;
        }
    }

    private void saveInverter(Inverter inverter, long timestamp, List<TimeSeriesData> data) {
        if (inverter == null)
            return;

        inverter.setTimestamp(timestamp);
        for (TimeSeriesData record : data) {
            if (record.tag().compareTo("inverterSetting") != 0) {
                inverter.setAttribute(record.field(), record.value());
            } else {
                if (record.subTag() != null && record.subTag().startsWith("chargePeriod")) {
                    inverter.setAttribute(record.subTag() + record.field(), record.value());
                } else {
                    inverter.setAttribute(record.field(), record.value());
                }
            }
        }
    }

    private void saveHeatPump(HeatPump heatPump, long timestamp, List<TimeSeriesData> data) {
        if (heatPump == null)
            return;

        heatPump.setTimestamp(timestamp);
        for (TimeSeriesData record : data)
            heatPump.setAttribute(record.field(), record.value());
    }

    private void saveChargingPile(ChargingPile chargingPile, long timestamp, List<TimeSeriesData> data) {
        if (chargingPile == null)
            return;

        chargingPile.setTimestamp(timestamp);
        for (TimeSeriesData record : data)
            chargingPile.setAttribute(record.field(), record.value());
    }

    private JsonObject getInverters(int interval) {
        MessageQueue inc = MessageQueue.getInstance();
        List<Inverter> inverters = inc.getInverters();
        JsonArray dynamicList = new JsonArray();
        JsonArray staticList = new JsonArray();
        for (Inverter elem : inverters) {
            if (System.currentTimeMillis() - elem.getTimestamp() > interval * 60000L)
                continue;
            dynamicList.add(elem.toDynamicData());
            staticList.add(elem.toStaticData());
        }

        return new JsonObject().put("dynamicList", dynamicList).put("staticList", staticList);
    }


    private JsonObject getHeatPumps(int interval) {
        MessageQueue inc = MessageQueue.getInstance();
        List<HeatPump> heatPumps = inc.getHeatPumps();
        JsonArray dynamicList = new JsonArray();
        JsonArray staticList = new JsonArray();
        for (HeatPump elem : heatPumps) {
            if (System.currentTimeMillis() - elem.getTimestamp() > interval * 60000L)
                continue;
            dynamicList.add(elem.toDynamicData());
            staticList.add(elem.toStaticData());
        }

        return new JsonObject().put("dynamicList", dynamicList).put("staticList", staticList);
    }

    private JsonObject getChargingPiles(int interval) {
        MessageQueue inc = MessageQueue.getInstance();
        List<ChargingPile> heatPumps = inc.getChargingPiles();
        JsonArray dynamicList = new JsonArray();
        JsonArray staticList = new JsonArray();
        for (ChargingPile elem : heatPumps) {
            if (System.currentTimeMillis() - elem.getTimestamp() > interval * 60000L)
                continue;
            dynamicList.add(elem.toDynamicData());
            staticList.add(elem.toStaticData());
        }

        return new JsonObject().put("dynamicList", dynamicList).put("staticList", staticList);
    }


    public static Map<String, Map<String, Object>> splitMap(Map<String, Object> data, Set epSet) {
        Map<String, Object> numericKeyMap = new HashMap<>();
        Map<String, Object> nonNumericKeyMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();

            // 检查键是否匹配数字模式
            if (!epSet.contains(key)) {
                numericKeyMap.put(key, entry.getValue());
            } else {
                nonNumericKeyMap.put(key, entry.getValue());
            }

//            if (!key.matches(".*[a-zA-Z].*")) {
//                numericKeyMap.put(key, entry.getValue());
//            } else {
//                nonNumericKeyMap.put(key, entry.getValue());
//            }
        }

        // 将两个 Map 放入一个包含两个 Map 的结果 Map 中
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        resultMap.put("numericKeys", numericKeyMap);
        resultMap.put("nonNumericKeys", nonNumericKeyMap);

        return resultMap;
    }

    public static String joinBmsMonomerData(List<Map<String, Object>> data, String serialNumber) {
        HexFormat hexFormat = HexFormat.of();
        DeviceMessage dm = DeviceMessage.getInstance();
        EmsBox emsBox = dm.getEmsBox();
        //采集器序列号
        String collectorSNHexStr = hexFormat.formatHex(emsBox.getSerialNumber().getBytes());
//        String collectorSNHexStr = hexFormat.formatHex("EMS123467".getBytes());
        String collectorSNHexStrFormat = padLeft(collectorSNHexStr, 30);
        String deviceSNHexStr = hexFormat.formatHex(serialNumber.getBytes());
        String deviceSNHexStrFormat = padLeft(deviceSNHexStr, 30);
        //时间戳
        String currentTimestampHexStrFormat = getCurrentTimestampInHex();
        // 定义顺序的参考列表
        List<String> order = List.of("SingleCellVoltage", "SingleTemperature", "RTControlTemperature", "MonomerEquilibriumState");

        // 数字字符串的正则表达式
        Pattern numberPattern = Pattern.compile("^[0-9a-fA-F]+$");

        // 创建结果容器，用于拼接最终字符串
        StringBuilder bmsMonomerInfo = new StringBuilder();

        //电芯数量
        for (Map<String, Object> datum : data) {
            if (datum.containsKey("tag") && "SingleCellVoltage".equals(datum.get("tag"))) {
                // 提取纯数字键值对
                for (Map.Entry<String, Object> entry : datum.entrySet()) {
                    if (numberPattern.matcher(entry.getKey()).matches()) {
                        //只加上数量
                        bmsMonomerInfo.append(entry.getKey());
                    }
                }
                break;
            }
        }

        //压缩状态
        bmsMonomerInfo.append("00");

        // 按照顺序遍历
        for (String key : order) {
            for (Map<String, Object> datum : data) {
                if (datum.containsKey("tag") && key.equals(datum.get("tag"))) {
                    // 提取纯数字键值对
                    for (Map.Entry<String, Object> entry : datum.entrySet()) {
                        if (numberPattern.matcher(entry.getKey()).matches()) {
                            bmsMonomerInfo.append(entry.getKey()).append(entry.getValue());
                        }
                    }
                    break;
                }
            }
        }

        // 构建完整的输出
        StringBuilder output = new StringBuilder();
        output.append(collectorSNHexStrFormat);         //数据采集器序列号
        output.append(deviceSNHexStrFormat);         //设备序列号
        output.append(currentTimestampHexStrFormat);   //数据时间戳
        output.append("f901");
        //数据区段个数
        output.append("00");
        //单体信息拼接特殊
        output.append(bmsMonomerInfo);

        return output.toString();
    }

    public static String convertDataIntoSegment(Map<String, Object> data, String deviceSN, String deviceType) {
        // todo 获取数据采集器序列号和设备序列号（假设长度为30字节）
        HexFormat hexFormat = HexFormat.of();
        DeviceMessage dm = DeviceMessage.getInstance();
        EmsBox emsBox = dm.getEmsBox();
        //采集器序列号
        String collectorSNHexStr = hexFormat.formatHex(emsBox.getSerialNumber().getBytes());
//        String collectorSNHexStr = hexFormat.formatHex("EMS123467".getBytes());

        DeviceMessage deviceMessage = DeviceMessage.getInstance();
        String emsSN = deviceMessage.getEmsBox().getSerialNumber();

        //如果是io模块，序列号= IO+ems序列号
        switch (deviceType) {
            case "IOmodule" -> deviceSN = "IO" + emsSN;
            case "beidou" -> deviceSN = "BD" + emsSN;
            case "AirConditioner" -> deviceSN = "Air" + emsSN;
        }

        String collectorSNHexStrFormat = padLeft(collectorSNHexStr, 30);
        String deviceSNHexStr = hexFormat.formatHex(deviceSN.getBytes());
        String deviceSNHexStrFormat = padLeft(deviceSNHexStr, 30);

        // 获取数据时间戳（长度为6字节）
        String currentTimestampHexStrFormat = getCurrentTimestampInHex();
//        LOGGER.info("时间戳：{}", currentTimestampHexStrFormat);

        // 构建数据区段内容
        List<String> segments = new ArrayList<>();
        int segmentCount = 0;
        StringBuilder bmsMonomerInfo = new StringBuilder();

        TreeMap<String, Object> sortedData = new TreeMap<>(data);
        String startAddress = null;
        StringBuilder segmentData = new StringBuilder();

        for (Map.Entry<String, Object> entry : sortedData.entrySet()) {
            String key = entry.getKey();

            if (startAddress == null) {
                startAddress = key;
            }
            segmentData.append(entry.getValue());
            String higherKey = sortedData.higherKey(key);
            //确定地址是否连续
            long currentKey = Long.parseLong(key, 16);

            if (higherKey == null) {
                segments.add(startAddress + key + segmentData.toString());
                segmentCount++;
                startAddress = null;
                segmentData.setLength(0);
            } else {
                long nextKey = Long.parseLong(higherKey, 16);
                if (nextKey != currentKey + 1) {
                    segments.add(startAddress + key + segmentData.toString());
                    segmentCount++;
                    startAddress = null;
                    segmentData.setLength(0);

                }
            }

        }
        // 构建完整的输出
        StringBuilder output = new StringBuilder();
        output.append(collectorSNHexStrFormat);         //数据采集器序列号
        output.append(deviceSNHexStrFormat);         //设备序列号
        output.append(currentTimestampHexStrFormat);   //数据时间戳
        switch (deviceType) {
            case "dehumidifier" -> output.append("fb01");
            case "bms" -> output.append("fc01");
            case "bms_monomer" -> output.append("fc01");
            case "liquidCooling" -> output.append("fa01");
            case "batteryCell" -> output.append("f901");
            case "dosimeter" -> output.append("fe01");
            case "electricityMeter" -> output.append("fd01");
            case "IOmodule" -> output.append("f601");
            case "pcs" -> output.append("ff03");
        }
        output.append(String.format("%02X", segmentCount));
        //单体信息拼接特殊
        if (!deviceType.equals("bms_monomer")) {
            for (String segment : segments) {
                output.append(segment);
            }
        } else {
            output.append(bmsMonomerInfo);
        }

        return output.toString();
    }

    /**
     * 补齐字符串到指定长度，左侧填充0
     * @param str
     * @param length
     * @return
     */
    private static String padLeft(String str, int length) {
        return String.format("%1$" + length * 2 + "s", str).replace(' ', '0');
    }

    /**
     * 获取当前时间时间戳，根据年月日时分秒转成十六进制6个字节
     * @return
     */
    public static String getCurrentTimestampInHex() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String year = intToHexStringFormat(now.getYear() - 2000, 2);
        String month = intToHexStringFormat(now.getMonthValue(), 2);
        String day = intToHexStringFormat(now.getDayOfMonth(), 2);
        String hour = intToHexStringFormat(now.getHour(), 2);
        String minute = intToHexStringFormat(now.getMinute(), 2);
        String second = intToHexStringFormat(now.getSecond(), 2);
        // 拼接结果
        return year + month + day + hour + minute + second;
    }

    /**
     * 将整数转成十六进制，并在左侧补0达到一定长度
     * @param digital 整数
     * @param num 位数
     * @return
     */
    public static String intToHexStringFormat(int digital, int num) {
        String hexString = Integer.toHexString(digital);
        if (hexString.length() < num) {
            hexString = '0' + hexString;
        }
        return hexString;
    }

    public static List<String> convertTo06FunctionCodes(String modbus10) {
        List<String> modbus06List = new ArrayList<>();

        // 解析原始10功能码
        String deviceAddress = modbus10.substring(0, 2);  // 设备地址
        String functionCode = modbus10.substring(2, 4);   // 功能码（应为10）
        String startingAddress = modbus10.substring(4, 8); // 起始地址
        String registerCount = modbus10.substring(8, 12); // 寄存器数量
        String byteCount = modbus10.substring(12, 14);    // 字节数量
        String data = modbus10.substring(14);             // 数据部分

        // 验证功能码是否为10
        if (!"10".equals(functionCode)) {
            throw new IllegalArgumentException("功能码不是10");
        }

        // 起始地址解析
        int startAddr = Integer.parseInt(startingAddress, 16);
        int count = Integer.parseInt(registerCount, 16);

        // 数据分离为每个寄存器的值
        for (int i = 0; i < count; i++) {
            String registerAddress = String.format("%04X", startAddr + i);
            String registerValue = data.substring(i * 4, i * 4 + 4);

            // 构造06功能码数据包
            String modbus06 = deviceAddress + "06" + registerAddress + registerValue + "1234";
            modbus06List.add(modbus06);
        }

        return modbus06List;
    }

    public CompletableFuture<String> sendMessageAndReceiveAsync(String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return CanServer.getInstance().sendMessageAndReceive(message); // 调用现有阻塞方法
            } catch (Exception e) {
                e.getMessage();
            }
            return null;
        });
    }

    private static void setTimezone(String timezone) {
        // 检查时区文件是否存在
        String tzFile = "/usr/share/zoneinfo/" + timezone;
        if (!new File(tzFile).exists()) {
            LOGGER.info("错误：无效时区文件{}", tzFile);
        }

        // 创建符号链接
        runCommand(
                "ln -sf " + tzFile + " /etc/localtime",
                "时区设置失败"
        );

        // 更新 /etc/timezone 文件（Debian 系系统需要）
        try (FileWriter writer = new FileWriter("/etc/timezone")) {
            writer.write(timezone + "\n");
        } catch (IOException e) {
            LOGGER.info("警告：无法写入 /etc/timezone 文件: {}", e.getMessage());
        }

        // 更新当前进程的时区环境变量
        System.setProperty("user.timezone", timezone);
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime.now(zoneId); // 刷新时区设置
    }

    /**
     * 运行系统命令并处理错误
     */
    private static void runCommand(String command, String errorMessage) {
        LOGGER.info("执行命令: {}", command);
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // 读取错误输出
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorOutput = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }

                LOGGER.info("错误: {}", errorMessage);
                LOGGER.info("命令输出: {}", errorOutput.toString());
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.info("错误: 执行命令失败 - {}", e.getMessage());
        }
    }

}
