package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.RealTimeDevice;
import com.slenergy.gateway.api.server.device.toJson;
import com.slenergy.gateway.ems.Power;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.IOException;

/**
 * class Inverter description
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
public class Inverter extends RealTimeDevice implements toJson {

    protected int runningState;
    protected int totalGridConnectedTime;
    protected int lastDataGridConnectedTime;
    protected int activePowerEnabled;
    protected int machineRatedPower;
    protected double todayGridConnectedPowerGeneration;
    protected double todayPowerGeneration;
    protected double pvPower;
    protected double activePowerPercentageSetting;
    protected Temperature temperature;
    protected ElectricityMeter electricityMeter;
    protected EnergyStorage energyStorage;
    protected GridSide gridSide;
    protected Battery batteryDevice;
    protected Pv pv1;
    protected Pv pv2;

    public Inverter(String sqlAddress) {
        super(sqlAddress);
        deviceType = "inverter";
    }

    public Inverter(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip, serialNumber, deviceName, deviceType, sqlAddress);
    }

    @Override
    public void initialize() throws Exception {
        temperature = new Temperature();
        electricityMeter = new ElectricityMeter();
        energyStorage = new EnergyStorage();
        gridSide = new GridSide();
        batteryDevice = new Battery(sqlAddress);
        pv1 = new Pv();
        pv2 = new Pv();

        super.initialize();
    }

    @Override
    public void setAttribute(String key, Object value) {
        switch (key) {
            case "runningState":
                runningState = (int) value;
                break;
            case "totalGridConnectedTime":
                totalGridConnectedTime = (int) value;
                break;
            case "lastDataGridConnectedTime":
                lastDataGridConnectedTime = (int) value;
                break;
            case "activePowerEnabled":
                activePowerEnabled = (int) value;
                break;
            case "machineRatedPower":
                machineRatedPower = (int) value;
            case "todayGridConnectedPowerGeneration":
                todayGridConnectedPowerGeneration = (double) value;
                break;
            case "todayPowerGeneration":
                todayPowerGeneration = (double) value;
                break;
            case "pvPower":
                pvPower = (double) value;
                break;
            case "pv1":
                pv1 = (Pv) value;
                break;
            case "pv1Voltage":
                pv1.setVoltage((double) value);
                break;
            case "pv1Current":
                pv1.setCurrent((double) value);
                break;
            case "pv1Power":
                pv1.setPower((double) value);
                break;
            case "pv2":
                pv2 = (Pv) value;
                break;
            case "pv2Voltage":
                pv2.setVoltage((double) value);
                break;
            case "pv2Current":
                pv2.setCurrent((double) value);
                break;
            case "pv2Power":
                pv2.setPower((double) value);
                break;
            case "activePowerPercentageSetting":
                activePowerPercentageSetting = (double) value;
                break;
            case "temperatureInformation":
                temperature = (Temperature) value;
                break;
            case "environmentTemperature":
                temperature.setEnvironmentTemperature((double) value);
                break;
            case "electricityMeter":
                electricityMeter = (ElectricityMeter) value;
                break;
            case "combinedActivePower":
            case "rPower":
            case "sPower":
            case "tPower":
            case "todayPositiveEnergy":
            case "todayReverseActiveEnergy":
                electricityMeter.setAttribute(key, value);
                break;
            case "energyStorage":
                energyStorage = (EnergyStorage) value;
                break;
            case "offGridAcOutputInformation":
                energyStorage.setOffGridAcOutput((OffGridAcOutput) value);
                break;
            case "offGridActivePower":
            case "todayElectricitySoldByGrid":
            case "todayGridElectricityPurchase":
            case "todayEnergyUsed":
                energyStorage.setAttribute(key, value);
                break;
            case "gridSideInformation":
                gridSide = (GridSide) value;
                break;
            case "rVoltage":
            case "rCurrent":
            case "rFrequency":
            case "rActivePower":
            case "sVoltage":
            case "sCurrent":
            case "sFrequency":
            case "sActivePower":
            case "tVoltage":
            case "tCurrent":
            case "tFrequency":
            case "tActivePower":
                gridSide.setAttribute(key, value);
                break;
            case "gridSideActivePower":
                gridSide.setAttribute(key, electricityMeter.getCombinedActivePower());
//                gridSide.setActivePower(electricityMeter.getCombinedActivePower());      //这里是因为老王和蔡工协商，电表测量值和电网侧值相同
                break;
            case "battery":
                batteryDevice = (Battery) value;
                break;
            case "soc":
            case "soh":
            case "batteryVoltage":
            case "batteryChargingCurrent":
            case "batteryDischargingCurrent":
            case "batteryChargingPower":
            case "batteryDischargePower":
            case "chargeCutOffVoltage":
            case "dischargeCutOffVoltage":
            case "chargeCurrentLimit":
            case "dischargeCurrentLimit":
            case "todayBatteryChargingCapacity":
            case "todayBatteryDischargeCapacity":
            case "totalBatteryChargingCapacity":
            case "totalBatteryDischargeCapacity":
            case "chargePeriodEnabled":
            case "periodEnabledFlag":
            case "chargePeriod1":
            case "chargePeriod1chargeMode":
            case "chargePeriod1chargingMode":
            case "chargePeriod1powerLimit":
            case "chargePeriod1startTime":
            case "chargePeriod1endTime":
            case "chargePeriod2":
            case "chargePeriod2chargeMode":
            case "chargePeriod2chargingMode":
            case "chargePeriod2powerLimit":
            case "chargePeriod2startTime":
            case "chargePeriod2endTime":
            case "chargePeriod3":
            case "chargePeriod3chargeMode":
            case "chargePeriod3chargingMode":
            case "chargePeriod3powerLimit":
            case "chargePeriod3startTime":
            case "chargePeriod3eendTime":
            case "chargePeriod4":
            case "chargePeriod4chargeMode":
            case "chargePeriod4chargingMode":
            case "chargePeriod4powerLimit":
            case "chargePeriod4startTime":
            case "chargePeriod4endTime":
            case "chargePeriod5":
            case "chargePeriod5chargeModl":
            case "chargePeriod5chargingMode":
            case "chargePeriod5powerLimit":
            case "chargePeriod5startTime":
            case "chargePeriod5endTime":
            case "chargePeriod6":
            case "chargePeriod6chargeMode":
            case "chargePeriod6chargingMode":
            case "chargePeriod6powerLimit":
            case "chargePeriod6startTime":
            case "chargePeriod6endTime":
                batteryDevice.setAttribute(key, value);
        }

        super.setAttribute(key, value);
    }

    @Override
    public JsonObject sendPower(double power) throws IOException {
        return null;
    }

    @Override
    public void initializeParameters() throws Exception {}

    @Override
    public double getCurrentPower() {
        return 0;
    }

    @Override
    public void sendEmsCommand(String string) throws Exception {
//        sendCommand(string);
    }

    @Override
    public JsonObject toDynamicData() {
        return new JsonObject()
                .put("electricityMeter", electricityMeter.toJsonObject()
                        .put("deviceSn", getSerialNumber())
                        .put("timestamp", tsToSecond()))
                .put("energyStorage", energyStorage.toJsonObject()
                        .put("deviceSn", serialNumber)
                        .put("timestamp", tsToSecond()))
                .put("inverter", toJsonObject())
                .put("battery", batteryDevice.toDynamicData()
                        .put("deviceSn", serialNumber)
                        .put("timestamp", tsToSecond()))
                .put("machineRatedPower", machineRatedPower);
    }

    @Override
    public JsonObject toStaticData() {
        return batteryDevice.toStaticData()
                .put("activePowerPercentageSetting", activePowerPercentageSetting)
                .put("activePowerEnabled", activePowerEnabled)
                .put("deviceSn", serialNumber)
                .put("manufacturer", deviceName)
                .put("timestamp", tsToSecond());
    }

    @Override
    public JsonObject command(JsonObject param) throws IOException {
        if (param.isEmpty())
            return null;

        JsonObject result = new JsonObject();
        if (param.containsKey("maxBatteryPower")) {
            JsonObject batteryResult = batteryDevice.command(new JsonObject().put("maxBatteryPower", param.getInteger("maxBatteryPower")));
            param.remove("maxBatteryPower");
            result.mergeIn(batteryResult);
        }

        // 指令下发处理
        connect();
        JsonObject ret = sendCommand(param);
        disconnect();

        result.mergeIn(ret);

        return result;
    }

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("runningState", runningState)
                .put("todayGridConnectedTime", totalGridConnectedTime - lastDataGridConnectedTime)
                .put("todayGridConnectedPowerGeneration", todayGridConnectedPowerGeneration)
                .put("pvPower", pvPower)
                .put("todayPowerGeneration", todayPowerGeneration)
                .put("pvInformation", new JsonArray().add(pv1).add(pv2))
                .put("gridSideInformation", gridSide.toJsonObject())
                .put("temperatureInformation", temperature)
                .put("deviceSn", serialNumber)
                .put("timestamp", tsToSecond());
    }

}
