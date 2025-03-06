package com.slenergy.gateway.api.server.device.heatPump;

import com.slenergy.gateway.api.server.device.RealTimeDevice;
import com.slenergy.gateway.ems.Power;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.slenergy.gateway.ems.EnergyManagementStrategy.compareDouble;

/**
 * class HeatPump description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-03-20
 * @since 1.0
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HeatPump extends RealTimeDevice {

    private int energyMode;
    private int operatingMode;
    private int waterPump;
    private int pressFaultCode;
    private int hpPressOperatingStatus;
    private int hpOperationStatus;
    private int controlMode;
    private int airConditioningModeOn;
    private int airConditioningModeOff;
    private int underfloorHeatingModeOn;
    private int underfloorHeatingModeOff;
    private int hotWaterModeOn;
    private int hotWaterModeOff;
    private int tankElectricallyHeatedModeOn;
    private int tankElectricallyHeatedModeOff;
    private double ratedPower;
    private double currentPower;
    private double currentTemperature;
    private double energyActiveImportRegister;
    private double todayEnergyActiveImportRegister;
    private double airConditioningTemperature;
    private double underfloorHeatingTemperature;
    private double dhwTankTemperature;
    private double indoorTemperature;
    private double indoorTemperatureSetting;
    private double envirmentTemperature;
    private int refrigerationTemperatureSetting;
    private int eletricallyHeated1;
    private int eletricallyHeated2;
    private int tankElectricallyHeatedTemperature;
    private int hotWaterTemperatureSetting;
    private int tankElectricallyHeated;
    private int heatingTemperatureSetting;
    private int underfloorTemperatureSetting;
    private String connectMode;
    private String model;
    private final static Logger LOGGER = LogManager.getLogger(HeatPump.class);

    @Getter
    private enum EnergySource {
        PV_ONLY(1, "PvOnly"),
        PV_AND_GRID(2, "PvAndGrid"),
        PV_AND_OPTIMIZED_GRID(3, "PvAndOptimizedGrid");

        private final int number;
        private final String name;

        EnergySource(int number, String name) {
            this.number = number;
            this.name = name;
        }

        public static EnergySource ofNumber(int number) {
            for (EnergySource source : EnergySource.values()) {
                if (source.number == number) {
                    return source;
                }
            }

            return null;
        }

        public static EnergySource ofName(String name) {
            for (EnergySource source : EnergySource.values()) {
                if (source.name.compareTo(name) == 0)
                    return source;
            }

            return null;
        }

    }

    public HeatPump(String sqlAddress) {
        super(sqlAddress);
        deviceType = "heatPump";
    }

    public HeatPump(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip, serialNumber, deviceName, deviceType, sqlAddress);
    }

    @Override
    public void initializeParameters() throws Exception {
        List<Object> parameters = new ArrayList<>(List.of(serialNumber));
        List<Map<String, Object>> connectModeQuery = conn.query("select key, value from deviceSpecificKey join device d on deviceSpecificKey.deviceId = d.id where serialNumber = ?;", parameters);
        if (connectModeQuery == null)
            throw new Exception("没法初始化connectMode");
        for (Map<String, Object> row : connectModeQuery) {
            for (Map.Entry<String, Object> entry : row.entrySet())
                setAttribute(entry.getKey(), entry.getValue());
        }

        Map<String, Object> strategyQuery = conn.queryOne("select strategy from energyMode join device d on d.id = energyMode.deviceId where serialNumber == ? limit 1;", parameters);
        if (!strategyQuery.containsKey("strategy"))
            throw new Exception("热泵没法初始化能量来源");
        setEnergyMode((int) strategyQuery.get("strategy"));
    }

    @Override
    public void sendEmsCommand(String string) throws Exception {

    }

    public String setEnergyMode(int energyMode) {
        boolean result = false;
        switch (energyMode) {
            case 1 -> {
                this.energyMode = energyMode;
                batteryPowerLimit = 0.0;
                utilityPowerLimit = 0.0;
                result = true;
            }
            case 2, 3 -> {
                this.energyMode = energyMode;
                batteryPowerLimit = 0.0;
                utilityPowerLimit = 1.0;
                result = true;
            }
        };

        if (result) {
            try {
                conn.commitTransaction(List.of(
                        "update energyMode set strategy = ?, name = ? where energyMode.deviceId = (select id from device where serialNumber = ?);",
                        "update load set batteryPowerLimited = ?, utilityPowerLimited = ? where load.deviceId = (select id from device where serialNumber = ?);"
                ), List.of(
                        List.of(energyMode, EnergySource.ofNumber(energyMode).getName(), serialNumber),
                        List.of(batteryPowerLimit, utilityPowerLimit, serialNumber)
                ));
            } catch (SQLException e) {
            }
        }

        return result ? "success" : "failure";
    }

    public void setFunctionMode(String func, int mode) {
        switch (func) {
            case "airConditioningMode":
                setAirConditioningMode(mode);
                break;
            case "underfloorHeatingMode":
                setUnderfloorHeatingMode(mode);
                break;
            case "hotWaterMode":
                setHotWaterMode(mode);
                break;
            case "tankElectricallyHeatedMode":
                setTankElectricallyHeatedMode(mode);
                break;
        }
    }

    public void setAirConditioningMode(int mode) {
        switch (mode) {
            case 0:
                airConditioningModeOn = 0;
                airConditioningModeOff = 65280;
                break;
            case 1:
                airConditioningModeOn = 65280;
                airConditioningModeOff = 0;
                break;
        }
    }

    public void setUnderfloorHeatingMode(int mode) {
        switch (mode) {
            case 0:
                underfloorHeatingModeOn = 0;
                underfloorHeatingModeOff = 65280;
                break;
            case 1:
                underfloorHeatingModeOn = 65280;
                underfloorHeatingModeOff = 0;
                break;
        }
    }

    public void setHotWaterMode(int mode) {
        switch (mode) {
            case 0:
                hotWaterModeOn = 0;
                hotWaterModeOff = 65280;
                break;
            case 1:
                hotWaterModeOn = 65280;
                hotWaterModeOff = 0;
                break;
        }
    }

    public void setTankElectricallyHeatedMode(int mode) {
        switch (mode) {
            case 0:
                tankElectricallyHeatedModeOn = 0;
                tankElectricallyHeatedModeOff = 65280;
                break;
            case 1:
                tankElectricallyHeatedModeOn = 65280;
                tankElectricallyHeatedModeOff = 0;
                break;
        }
    }

    @Override
    public double getCurrentPower() {
        return currentPower;
    }

    @Override
    public void setAttribute(String key, Object value) {
        switch (key) {
            case "operatingMode" -> operatingMode = (int) value;
            case "waterPump" -> waterPump = (int) value;
            case "pressFaultCode" -> pressFaultCode = (int) value;
            case "hpPressOperatingStatus" -> hpPressOperatingStatus = (int) value;
            case "controlMode" -> controlMode = (int) value;
            case "hotWaterMode" -> setHotWaterMode((int) value);
            case "airConditioningMode" -> setAirConditioningMode((int) value);
            case "underfloorHeatingMode" -> setUnderfloorHeatingMode((int) value);
            case "tankElectricallyHeatedMode" -> setTankElectricallyHeatedMode((int) value);
            case "ratedPower" -> ratedPower = (double) value;
            case "currentPower" -> currentPower = (double) value;
            case "currentTemperature" -> currentTemperature = (double) value;
            case "energyActiveImportRegister" -> energyActiveImportRegister = (double) value;
            case "lastDayEnergyActiveImportRegister" -> todayEnergyActiveImportRegister = (double) value;
            case "airConditioningTemperature" -> airConditioningTemperature = (double)  value;
            case "dhwTankTemperature" -> dhwTankTemperature = (double) value;
            case "hpOperationStatus" -> hpOperationStatus = (int) value;
            case "environmentTemperature" -> envirmentTemperature = (double)  value;
            case "indoorTemperature" -> indoorTemperature = (double) value;
            case "indoorTemperatureSetting" -> indoorTemperatureSetting = (double) value;
            case "refrigerationTemperatureSetting" -> refrigerationTemperatureSetting = (int) value;
            case "eletricallyHeated1" -> eletricallyHeated1 = (int) value;
            case "eletricallyHeated2" -> eletricallyHeated2 = (int) value;
            case "tankElectricallyHeatedTemperature" -> tankElectricallyHeatedTemperature = (int) value;
            case "hotWaterTemperatureSetting" -> hotWaterTemperatureSetting = (int) value;
            case "tankElectricallyHeated" -> tankElectricallyHeated = (int) value;
            case "heatingTemperatureSetting" -> heatingTemperatureSetting = (int) value;
            case "underfloorHeatingTemperature" -> underfloorHeatingTemperature = (double) value;
            case "underfloorTemperatureSetting" -> underfloorTemperatureSetting = (int) value;
            case "connectMode" -> connectMode = (String) value;
            case "energyMode" -> setEnergyMode((int) value);
            case "model" -> setModel((String) value);
        }

        super.setAttribute(key, value);
    }

    public void setConnectMode(String connectMode) {
        this.connectMode = connectMode;
        try {
            conn.execute(getDeviceSpecificKeySql("connectMode"), List.of(connectMode, serialNumber));
        } catch (SQLException e) {}
    }

    public void setModel(String model) {
        this.model = model;
        try {
            conn.execute(getDeviceSpecificKeySql("model"), List.of(model, serialNumber));
        } catch (SQLException e) {}
    }

    public void setMaxPower(double power) {
        powerList.setMaximum(power);
        try {
            conn.execute("update power set max = ? where deviceId = (select id from device where serialNumber = ?);", List.of(power, serialNumber));
        } catch (SQLException e) {}
    }

    @Override
    public void initialize() throws Exception {
        super.initialize();
    }

    @Override
    public void setPvPriority(int pvPriority) {
        super.setPvPriority(pvPriority);
        try {
            conn.execute("update load set pvPriority = ? where load.deviceId = (select id from device where serialNumber = ?);", List.of(pvPriority, serialNumber));
        } catch (SQLException e) {
        }
    }

    @Override
    public JsonObject toDynamicData() {
        return new JsonObject()
                .put("ratedPower", ratedPower)
                .put("currentTemperature", currentTemperature)
                .put("energyActiveImportRegister", energyActiveImportRegister)
                .put("operatingMode", operatingMode)
                .put("waterPump", waterPump)
                .put("airConditioningTemperature", airConditioningTemperature)
                .put("underfloorHeatingTemperature", underfloorHeatingTemperature)
                .put("pressFaultCode", pressFaultCode)
                .put("hpPressOperatingStatus", hpPressOperatingStatus)
                .put("dhwTankTemperature", dhwTankTemperature)
                .put("hpOperationStatus", hpOperationStatus)
                .put("connectMode", connectMode)
                .put("currentPower", currentPower)
                .put("indoorTemperature", indoorTemperature)
                .put("maxPowerInput", powerList.getMaximum())
                .put("pvPriority", pvPriority)
                .put("model", getModel())
                .put("deviceSn", getSerialNumber())
                .put("timestamp", tsToSecond());
    }

    @Override
    public JsonObject toStaticData() {
        return new JsonObject()
                .put("controlMode", controlMode)
                .put("indoorTemperatureSetting", indoorTemperatureSetting)
                .put("refrigerationTemperatureSetting", refrigerationTemperatureSetting)
                .put("energyMode", energyMode)
                .put("eletricallyHeated1", eletricallyHeated1)
                .put("eletricallyHeated2", eletricallyHeated2)
                .put("hotWaterModeOn", hotWaterModeOn)
                .put("hotWaterModeOff", hotWaterModeOff)
                .put("tankElectricallyHeatedTemperature", tankElectricallyHeatedTemperature)
                .put("airConditioningModeOn", airConditioningModeOn)
                .put("airConditioningModeOff", airConditioningModeOff)
                .put("underfloorHeatingModeOn", underfloorHeatingModeOn)
                .put("underfloorHeatingModeOff", underfloorHeatingModeOff)
                .put("hotWaterTemperatureSetting", hotWaterTemperatureSetting)
                .put("tankElectricallyHeated", tankElectricallyHeated)
                .put("tankElectricallyHeatedModeOn", tankElectricallyHeatedModeOn)
                .put("tankElectricallyHeatedMode", tankElectricallyHeatedModeOff)
                .put("heatingTemperatureSetting", heatingTemperatureSetting)
                .put("underfloorTemperatureSetting", underfloorTemperatureSetting)
                .put("maxPowerInput", powerList.getMaximum())
                .put("pvPriority", pvPriority)
                .put("model", getModel())
                .put("deviceSn", serialNumber)
                .put("timestamp", tsToSecond());
    }

    @Override
    public JsonObject command(JsonObject param) throws IOException {
        JsonObject result = new JsonObject();
        //一进来param就为空，那就返回null
        if (param.isEmpty())
            return result.isEmpty() ? null : result;

        if (param.containsKey("energyMode")) {
            String ret = setEnergyMode(param.getInteger("energyMode"));
            param.remove("energyMode");
            result.put("energyMode", ret);
        }

        if (param.containsKey("pvPriority")) {
            setPvPriority(param.getInteger("pvPriority"));
            param.remove("pvPriority");
            result.put("pvPriority", "success");
        }

        if (param.containsKey("maxPowerInput")) {
            setMaxPower(param.getDouble("maxPowerInput"));
            param.remove("maxPowerInput");
            result.put("maxPowerInput", "success");
        }

        if (param.containsKey("model")) {
            setModel(param.getString("model"));
            param.remove("model");
            result.put("model", "success");
        }
        //param把静态数据库更新后为空，就不下发指令给设备
        if (!param.isEmpty()) {
            // 指令下发处理
            connect();
            result = sendCommand(param);
            disconnect();
        }

        return result;
    }

    @Override
    public JsonObject sendPower(double power) throws Exception {
//        int ambientTemp = (int) envirmentTemperature;
//        List<Map<String, Object>> result = sqlConn.query("select ambientTemp, outletTemp, power from heatPumpHotWater join device d on heatPumpHotWater.deviceId = d.id where d.serialNumber == ? and abs(ambientTemp - ?) <= ? and abs(power - ?) <= ?;", List.of(serialNumber, ambientTemp, 5, power, 500.0));
//        if (result.isEmpty())
//            throw new Exception(String.format("没有找到合适的温度调节, 需要调节的功率: %f", power));
//        List<TemperaturePowerDifference> differences = new ArrayList<>();
//        for (Map<String, Object> row : result)
//            differences.add(new TemperaturePowerDifference((int) row.get("ambientTemp"), (int) row.get("outletTemp"), (double) row.get("power"), Math.abs((double) row.get("power") - power)));
//        differences.sort(Comparator.comparing(TemperaturePowerDifference::difference));

        if (hotWaterTemperatureSetting >= 80)
            return new JsonObject().put("hotWaterTemperatureSetting", "failure");

        int changeHotWater = hotWaterTemperatureSetting;
        if (compareDouble(power - currentPower, 200.0, THRESHOLD) >= 0)
            changeHotWater += 5;
        else if (compareDouble(currentPower - power, 200.0, THRESHOLD) >= 0)
            changeHotWater -= 5;

        JsonObject result = new JsonObject();
        if (changeHotWater != hotWaterTemperatureSetting)
            result.put("hotWaterTemperatureSetting", changeHotWater);
        else
            return result.put("hotWaterTemperatureSetting", "failure");
        LOGGER.info("当前功率: {}, 需要调节的功率: {}, 需要调节的目标温度: {}", currentPower, power, result.getInteger("hotWaterTemperatureSetting"));
        return command(result);
    }

    public JsonObject changeTankElectricallyHeated(double power) throws IOException {
        if (hotWaterTemperatureSetting < 80) {
            if (compareDouble(power, 1000.0, THRESHOLD) >= 0)
                return openTankElectricallyHeated();
            else if (compareDouble(power, 0.0, THRESHOLD) <= 0)
                return closeTankElectricallyHeated();
        }

        return new JsonObject().put("tankElectricallyHeatedOpen", "failure").put("tankElectricallyHeatedClose", "failure");
    }

    private JsonObject openTankElectricallyHeated() throws IOException {
        return command(new JsonObject().put("tankElectricallyHeatedOpen", 65280).put("tankElectricallyHeatedClose", 0));
    }

    private JsonObject closeTankElectricallyHeated() throws IOException {
        return command(new JsonObject().put("tankElectricallyHeatedOpen", 0).put("tankElectricallyHeatedClose", 65280));
    }

}
