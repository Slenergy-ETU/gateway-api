package com.slenergy.gateway.api.server.device.chargingPile;

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
 * class ChargingPile description
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
public class ChargingPile extends RealTimeDevice {

    private int numberOfConnectors;
    private int userCurrentLimit;
    private int chargingMode;
    private double voltage;
    private double currentImport;
    private double powerActiveImport;
    private double energyActiveImportRegister;
    /**
     * 用来计算todayEnergyActiveImportRegister的辅助属性, 每日凌晨0点更新
     */
    private double lastDayEnergyActiveImportRegister;
    private String chargePointStatus;
    private String chargePointModel;
    private String firmwareVersion;
    private String chargePointVendor;
    private String chargingAvailability;
    private ChargingProfile chargingProfile;
    private final static Logger LOGGER = LogManager.getLogger(ChargingPile.class);

    @Getter
    private enum EnergySource {
        PLUG_AND_PLAY(1, "PlugAndPlay"),
        CHARGING_PLAN(2, "ChargingPlan"),
        PV_FIRST_AND_BATTERY(3, "PvFirstAndBattery"),
        PV_AND_GRID_AND_BATTERY(4, "PvAndGridAndBattery"),
        PV_AND_OPTIMIZED_GRID_AND_BATTERY(5, "PvAndOptimizedGridAndBattery"),;

        private final int number;
        private final String name;

        EnergySource(int number, String name) {
            this.number = number;
            this.name = name;
        }

        public static ChargingPile.EnergySource ofNumber(int number) {
            for (ChargingPile.EnergySource source : ChargingPile.EnergySource.values()) {
                if (source.number == number) {
                    return source;
                }
            }

            return null;
        }

        public static ChargingPile.EnergySource ofName(String name) {
            for (ChargingPile.EnergySource source : ChargingPile.EnergySource.values()) {
                if (source.name.compareTo(name) == 0)
                    return source;
            }

            return null;
        }

    }

    public ChargingPile(String sqlAddress) {
        super(sqlAddress);
        deviceType = "chargingPile";
    }

    public ChargingPile(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip, serialNumber, deviceName, deviceType, sqlAddress);
    }

    @Override
    public void initializeParameters() throws Exception {
        List<Object> parameters = new ArrayList<>(List.of(serialNumber));
        Map<String, Object> strategyQuery = conn.queryOne("select strategy from energyMode join device d on d.id = energyMode.deviceId where serialNumber == ? limit 1;", parameters);
        if (!strategyQuery.containsKey("strategy"))
            throw new Exception("充电桩没法初始化能量来源");
        setChargingMode((int) strategyQuery.get("strategy"));
        List<Map<String, Object>> querys = conn.query("select key, value from deviceSpecificKey join device d on deviceSpecificKey.deviceId = d.id where serialNumber == ?;", parameters);
        if (querys.isEmpty())
            throw new Exception("初始化充电桩静态参数失败");
        for (Map<String, Object> query : querys) {
            setAttribute((String) query.get("key"), query.get("value"));
        }

        Map<String, Object> lastDayTotalEnergyQuery = conn.queryOne("select value as lastDayTotalEnergy from lastDayTotalEnergy join device d on d.id = lastDayTotalEnergy.deviceId where serialNumber == ? limit 1;", parameters);
        if (lastDayTotalEnergyQuery.containsKey("lastDayTotalEnergy"))
            lastDayEnergyActiveImportRegister = (double) lastDayTotalEnergyQuery.get("lastDayTotalEnergy");

        chargingProfile = new ChargingProfile();
        List<Map<String, Object>> cps = conn.query("select connectorId, chargingProfileId, stackLevel, chargingProfilePurpose, chargingProfileKind, duration, startPeriod, chargingRateUnit, startSchedule, limitNumber from chargingProfile join device d on d.id = chargingProfile.deviceId where serialNumber == ?;", parameters);
        if (!cps.isEmpty()) {
            for (Map<String, Object> cp : cps) {
                for (Map.Entry<String, Object> entry : cp.entrySet()) {
                    chargingProfile.setAttribute(entry.getKey(), entry.getValue());
                }
            }
        } else {
            persistChargingProfile();
        }
    }

    @Override
    public void sendEmsCommand(String string) throws Exception {

    }

    @Override
    public double getCurrentPower() {
        return powerActiveImport;
    }

    public String setChargingMode(int chargingMode) {
        boolean result = false;
        switch (chargingMode) {
            case 1, 2, 4, 5 -> {
                this.chargingMode = chargingMode;
                batteryPowerLimit = 1.0;
                utilityPowerLimit = 1.0;
                result = true;
            }
            case 3 -> {
                this.chargingMode = chargingMode;
                batteryPowerLimit = 1.0;
                utilityPowerLimit = 0.0;
                result = true;
            }
        }

        if (result) {
            try {
                conn.commitTransaction(List.of(
                        "update energyMode set strategy = ?, name = ? where energyMode.deviceId = (select id from device where serialNumber = ?);",
                        "update load set batteryPowerLimited = ?, utilityPowerLimited = ? where load.deviceId = (select id from device where serialNumber = ?);"
                ), List.of(
                        List.of(chargingMode, EnergySource.ofNumber(chargingMode).getName(), serialNumber),
                        List.of(batteryPowerLimit, utilityPowerLimit, serialNumber)
                ));
            } catch (SQLException e) {
            }
        }

        return result ? "success" : "failure";
    }

    @Override
    public void setAttribute(String key, Object value) {
        switch (key) {
            case "numberOfConnectors" -> numberOfConnectors = (int) value;
            case "userCurrentLimit" -> userCurrentLimit = (int) value;
            case "voltage" -> voltage = (double) value;
            case "currentImport" -> currentImport = (double) value;
            case "powerActiveImport" -> powerActiveImport = (double) value;
            case "energyActiveImportRegister" -> energyActiveImportRegister = (double) value;
            case "lastDayEnergyActiveImportRegister" -> setLastDayEnergyActiveImportRegister((double) value);
            case "chargePointStatus" -> chargePointStatus = (String) value;
            case "chargePointModel" -> setChargePointModel((String) value);
            case "firmwareVersion" -> setFirmwareVersion((String) value);
            case "chargePointVendor" -> setChargePointVendor((String) value);
            case "chargingAvailability" -> setChargingAvailability((String) value);
            case "chargingMode" -> setChargingMode((int) value);
        }

        super.setAttribute(key, value);
    }

    public void setChargePointModel(String chargePointModel) {
        this.chargePointModel = chargePointModel;
        try {
            conn.execute(getDeviceSpecificKeySql("chargePointModel"), List.of(chargePointModel, serialNumber));
        } catch (SQLException e) {}
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
        try {
            conn.execute(getDeviceSpecificKeySql("firmwareVersion"), List.of(firmwareVersion, serialNumber));
        } catch (SQLException e) {}
    }

    public void setChargePointVendor(String chargePointVendor) {
        this.chargePointVendor = chargePointVendor;
        try {
            conn.execute(getDeviceSpecificKeySql("chargePointVendor"), List.of(chargePointVendor, serialNumber));
        } catch (SQLException e) {}
    }

    public void setChargingAvailability(String chargingAvailability) {
        this.chargingAvailability = chargingAvailability;
        try {
            conn.execute(getDeviceSpecificKeySql("chargingAvailability"), List.of(chargingAvailability, serialNumber));
        } catch (SQLException e) {}
    }

    public void setLastDayEnergyActiveImportRegister(double lastDayEnergyActiveImportRegister) {
        this.lastDayEnergyActiveImportRegister = lastDayEnergyActiveImportRegister;
        try {
            conn.execute("update lastDayTotalEnergy set value = ? where deviceId = (select id from device where serialNumber = ?);", List.of(lastDayEnergyActiveImportRegister, serialNumber));
        } catch (SQLException e) {}
    }

    @Override
    public JsonObject toDynamicData() {
        return new JsonObject()
                .put("chargePointModel", chargePointModel)
                .put("firmwareVersion", firmwareVersion)
                .put("numberOfConnectors", numberOfConnectors)
                .put("voltage", voltage)
                .put("currentImport", currentImport)
                .put("userCurrentLimit", userCurrentLimit)
                .put("powerActiveImport", powerActiveImport)
                .put("energyActiveImportRegister", energyActiveImportRegister / 1000)
                // 计算todayEnergyActiveImportRegister
                .put("todayEnergyActiveImportRegister", (energyActiveImportRegister - lastDayEnergyActiveImportRegister) / 1000)
                .put("chargePointStatus", chargePointStatus)
                .put("pvPriority", pvPriority)
                .put("deviceSn", serialNumber)
                .put("timestamp", tsToSecond());
    }

    @Override
    public JsonObject toStaticData() {
        return new JsonObject()
                .put("chargePointVendor", chargePointVendor)
                .put("userCurrentLimit", userCurrentLimit)
                .put("chargingMode", chargingMode)
                .put("chargingAvailability", chargingAvailability)
                .put("chargingProfile", chargingProfile.toJsonObject())
                .put("pvPriority", pvPriority)
                .put("timestamp", tsToSecond())
                .put("deviceSn", serialNumber);
    }

    @Override
    public void setPvPriority(int pvPriority) {
        super.setPvPriority(pvPriority);
        try {
            conn.execute("update load set pvPriority = ? where load.deviceId = (select id from device where serialNumber = ?);", List.of(pvPriority, serialNumber));
        } catch (SQLException e) {}
    }

    @Override
    public JsonObject command(JsonObject param) throws IOException {
        if (param.isEmpty())
            return null;

        if (!param.containsKey("chargingAvailability") && chargingAvailability.compareTo("Inoperative") == 0)
            return null;

        JsonObject result = new JsonObject();
        if (param.containsKey("chargingMode")) {
            String ret = setChargingMode(param.getInteger("chargingMode"));
            param.remove("chargingMode");
            result.put("chargingMode", ret);
        }

        if (param.containsKey("pvPriority")) {
            setPvPriority(param.getInteger("pvPriority"));
            param.remove("pvPriority");
            result.put("pvPriority", "success");
        }

        if (param.containsKey("chargingAvailability")) {
            setChargingAvailability(param.getString("chargingAvailability"));
//            param.remove("chargingAvailability");
            result.put("chargingAvailability", "success");
        }

        if (param.isEmpty()) {
            return result.isEmpty() ? null : result;
        }

        // 判断是否含有startSchedule以及duration, 两个key肯定是同时出现的
        if (param.containsKey("limit") && !param.containsKey("startSchedule"))
            param.put("startSchedule", chargingProfile.getStartSchedule().getTime()).put("duration", chargingProfile.getDuration());

        // 指令下发处理
        connect();
        result = sendCommand(param);
        disconnect();

        if (param.containsKey("limit") && result != null && result.containsKey("status") && result.getString("status").compareTo("success") == 0) {
            // 更新数据库
            Map<String, Object> plan = param.getMap();
            for (Map.Entry<String, Object> item : plan.entrySet())
                chargingProfile.setAttribute(item.getKey(), item.getValue());
            persistChargingProfile();

            for (String entry : param.getMap().keySet()) {
                result.put(entry, "success");
            }
        }

        return result;
    }

    private void persistChargingProfile() {
        try {
            conn.execute("update chargingProfile set connectorId = ?, chargingProfileId = ?, stackLevel = ?, chargingProfilePurpose = ?, chargingProfileKind = ?, duration = ?, startSchedule = ?, chargingRateUnit = ?, startPeriod = ?, limitNumber = ? where deviceId = (select id from device where serialNumber = ?);",
                    List.of(
                            chargingProfile.getConnectorId(),
                            chargingProfile.getChargingProfileId(),
                            chargingProfile.getStackLevel(),
                            chargingProfile.getChargingProfilePurpose(),
                            chargingProfile.getChargingProfileKind(),
                            chargingProfile.getDuration(),
                            chargingProfile.getStartSchedule("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                            chargingProfile.getChargingRateUnit(),
                            chargingProfile.getStartPeriod(),
                            chargingProfile.getLimit(),
                            serialNumber
                    ));
        } catch (SQLException e) {}
    }

    @Override
    public JsonObject sendPower(double power) throws Exception {
        if (compareDouble(power, 0.0, THRESHOLD) < 0)
            return new JsonObject().put("limit", "failure").put("startSchedule", "failure");

        return command(new JsonObject().put("limit", (int)(power / powerList.getInterval())).put("startSchedule", System.currentTimeMillis()));
    }

}
