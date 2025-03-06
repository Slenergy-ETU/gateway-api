package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.RealTimeDevice;
import com.slenergy.gateway.ems.Power;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static com.slenergy.gateway.ems.EnergyManagementStrategy.compareDouble;

/**
 * class Battery description
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
public class Battery extends RealTimeDevice {

    private double dischargeCurrentLimit;
    private double dischargeCutOffVoltage;
    private double batteryChargingCurrent;
    private double batteryDischargeCurrent;
    private double totalBatteryChargingCapacity;
    private double totalBatteryDischargeCapacity;
    private double todayBatteryChargingCapacity;
    private double todayBatteryDischargeCapacity;
    private double batteryVoltage;
    private double soc;
    private double soh;
    private double batteryChargingPower;
    private double batteryDischargePower;
    private double chargeCurrentLimit;
    private double chargeCutOffVoltage;
    protected ChargePeriodEnabled chargePeriodEnabled;
    protected ChargePeriod chargePeriod1;
    protected ChargePeriod chargePeriod2;
    protected ChargePeriod chargePeriod3;
    protected ChargePeriod chargePeriod4;
    protected ChargePeriod chargePeriod5;
    protected ChargePeriod chargePeriod6;
    private final static Logger LOGGER = LogManager.getLogger(Battery.class);

    public Battery(String sqlAddress) {
        super(sqlAddress);
        deviceType = "battery";
    }

    public Battery(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip, serialNumber, deviceName, deviceType, sqlAddress);
    }

    @Override
    public void initialize() throws Exception {
        chargePeriodEnabled = new ChargePeriodEnabled();
        chargePeriod1 = new ChargePeriod();
        chargePeriod2 = new ChargePeriod();
        chargePeriod3 = new ChargePeriod();
        chargePeriod4 = new ChargePeriod();
        chargePeriod5 = new ChargePeriod();
        chargePeriod6 = new ChargePeriod();

        super.initialize();
    }

    @Override
    public void setSerialNumber(String serialNumber) {
        super.setSerialNumber(serialNumber + "-B");
    }

    @Override
    public void setAttribute(String key, Object value) {
        switch (key) {
            case "dischargeCurrentLimit" -> dischargeCurrentLimit = (double) value;
            case "dischargeCutOffVoltage" -> dischargeCutOffVoltage = (double) value;
            case "batteryChargingCurrent" -> batteryChargingCurrent = (double) value;
            case "batteryDischargeCurrent" -> batteryDischargeCurrent = (double) value;
            case "totalBatteryChargingCapacity" -> totalBatteryChargingCapacity = (double) value;
            case "totalBatteryDischargeCapacity" -> totalBatteryDischargeCapacity = (double) value;
            case "todayBatteryChargingCapacity" -> todayBatteryChargingCapacity = (double) value;
            case "todayBatteryDischargeCapacity" -> todayBatteryDischargeCapacity = (double) value;
            case "batteryVoltage" -> batteryVoltage = (double) value;
            case "soc" -> soc = (double) value;
            case "soh" -> soh = (double) value;
            case "batteryChargingPower" -> batteryChargingPower = (double) value;
            case "batteryDischargePower" -> batteryDischargePower = (double) value;
            case "chargeCurrentLimit" -> chargeCurrentLimit = (double) value;
            case "chargeCutOffVoltage" -> chargeCutOffVoltage = (double) value;
            case "chargePeriodEnabled" -> chargePeriodEnabled = (ChargePeriodEnabled) value;
            case "periodEnabledFlag" -> chargePeriodEnabled.setPeriodEnabledFlag((int) value);
            case "chargePeriod1" -> chargePeriod1 = (ChargePeriod) value;
            case "chargePeriod1chargeMode" -> chargePeriod1.setChargeMode((int) value);
            case "chargePeriod1chargingMode" -> chargePeriod1.setChargingMode((int) value);
            case "chargePeriod1powerLimit" -> chargePeriod1.setPowerLimit((double) value);
            case "chargePeriod1startTime" -> chargePeriod1.setStartTime((int) value);
            case "chargePeriod1endTime" -> chargePeriod1.setEndTime((int) value);
            case "chargePeriod2" -> chargePeriod2 = (ChargePeriod) value;
            case "chargePeriod2chargeMode" -> chargePeriod2.setChargeMode((int) value);
            case "chargePeriod2chargingMode" -> chargePeriod2.setChargingMode((int) value);
            case "chargePeriod2powerLimit" -> chargePeriod2.setPowerLimit((double) value);
            case "chargePeriod2startTime" -> chargePeriod2.setStartTime((int) value);
            case "chargePeriod2endTime" -> chargePeriod2.setEndTime((int) value);
            case "chargePeriod3" -> chargePeriod3 = (ChargePeriod) value;
            case "chargePeriod3chargeMode" -> chargePeriod3.setChargeMode((int) value);
            case "chargePeriod3chargingMode" -> chargePeriod3.setChargingMode((int) value);
            case "chargePeriod3powerLimit" -> chargePeriod3.setPowerLimit((double) value);
            case "chargePeriod3startTime" -> chargePeriod3.setStartTime((int) value);
            case "chargePeriod3eendTime" -> chargePeriod3.setEndTime((int) value);
            case "chargePeriod4" -> chargePeriod4 = (ChargePeriod) value;
            case "chargePeriod4chargeMode" -> chargePeriod4.setChargeMode((int) value);
            case "chargePeriod4chargingMode" -> chargePeriod4.setChargingMode((int) value);
            case "chargePeriod4powerLimit" -> chargePeriod4.setPowerLimit((double) value);
            case "chargePeriod4startTime" -> chargePeriod4.setStartTime((int) value);
            case "chargePeriod4endTime" -> chargePeriod4.setEndTime((int) value);
            case "chargePeriod5" -> chargePeriod5 = (ChargePeriod) value;
            case "chargePeriod5chargeMode" -> chargePeriod5.setChargeMode((int) value);
            case "chargePeriod5chargingMode" -> chargePeriod5.setChargingMode((int) value);
            case "chargePeriod5powerLimit" -> chargePeriod5.setPowerLimit((double) value);
            case "chargePeriod5startTime" -> chargePeriod5.setStartTime((int) value);
            case "chargePeriod5endTime" -> chargePeriod5.setEndTime((int) value);
            case "chargePeriod6" -> chargePeriod6 = (ChargePeriod) value;
            case "chargePeriod6chargeMode" -> chargePeriod6.setChargeMode((int) value);
            case "chargePeriod6chargingMode" -> chargePeriod6.setChargingMode((int) value);
            case "chargePeriod6powerLimit" -> chargePeriod6.setPowerLimit((double) value);
            case "chargePeriod6startTime" -> chargePeriod6.setStartTime((int) value);
            case "chargePeriod6endTime" -> chargePeriod6.setEndTime((int) value);
        }

        super.setAttribute(key, value);
    }

    public void setMaxPower(double power) {
        powerList.setMaximum(power);
        try {
            conn.execute("update power set max = ? where deviceId = (select id from device where serialNumber = ?);", List.of(power, serialNumber));
        } catch (SQLException e) {}
    }

    @Override
    public JsonObject toDynamicData() {
        return new JsonObject()
                .put("dischargeCurrentLimit", dischargeCurrentLimit)
                .put("dischargeCutOffVoltage", dischargeCutOffVoltage)
                .put("batteryChargingCurrent", batteryChargingCurrent)
                .put("batteryDischargeCurrent", batteryDischargeCurrent)
                .put("totalBatteryChargingCapacity", totalBatteryChargingCapacity)
                .put("totalBatteryDischargeCapacity", totalBatteryDischargeCapacity)
                .put("todayBatteryChargingCapacity", todayBatteryChargingCapacity)
                .put("todayBatteryDischargeCapacity", todayBatteryDischargeCapacity)
                .put("batteryVoltage", batteryVoltage)
                .put("soc", soc)
                .put("soh", soh)
                .put("batteryChargingPower", batteryChargingPower)
                .put("batteryDischargePower", batteryDischargePower)
                .put("chargeCurrentLimit", chargeCurrentLimit)
                .put("chargeCutOffVoltage", chargeCutOffVoltage);
    }

    @Override
    public JsonObject toStaticData() {
        return new JsonObject()
                .put("maxBatteryPower", powerList.getMaximum())
                .put("chargePeriodEnabled", chargePeriodEnabled.toJsonObject())
                .put("chargePeriod1", chargePeriod1.toJsonObject())
                .put("chargePeriod2", chargePeriod2.toJsonObject())
                .put("chargePeriod3", chargePeriod3.toJsonObject())
                .put("chargePeriod4", chargePeriod4.toJsonObject())
                .put("chargePeriod5", chargePeriod5.toJsonObject())
                .put("chargePeriod6", chargePeriod6.toJsonObject());
    }

    @Override
    public JsonObject command(JsonObject param) throws IOException {
        JsonObject result = new JsonObject();
        if (param.containsKey("maxBatteryPower")) {
            setMaxPower(param.getDouble("maxBatteryPower"));
            param.remove("maxBatteryPower");
            result.put("maxBatteryPower", "success");
        }

        if (param.isEmpty())
            return result.isEmpty() ? null : result;

        // 指令下发处理
        connect();
        result = sendCommand(param);
        disconnect();

        return result;
    }

    @Override
    public void initializeParameters() throws Exception {}

    @Override
    public void sendEmsCommand(String string) throws Exception {

    }

    @Override
    public double getCurrentPower() {
        return batteryChargingPower > 0 ? -batteryChargingPower : batteryDischargePower;
    }

    @Override
    public JsonObject sendPower(double power) throws Exception {
        boolean usable = true;
        if (compareDouble(soc, 20.0, THRESHOLD) <= 0 && compareDouble(power, 0.0, THRESHOLD) > 0) {
            LOGGER.warn("电池此时soc为{}，不能放电", soc);
            usable = false;
        } else if (compareDouble(soc, 99.0, THRESHOLD) >= 0 && compareDouble(power, 0.0, THRESHOLD) < 0) {
            LOGGER.warn("电池此时soc为{}，不能充电", soc);
            usable = false;
        }
        return usable ? command(new JsonObject().put("batteryPowerSetting", power / 1000.0)) : new JsonObject().put("batteryPowerSetting", "failure");
    }

}
