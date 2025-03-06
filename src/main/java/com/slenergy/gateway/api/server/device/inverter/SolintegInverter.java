package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.ems.Power;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * class SolintegInverter description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-07
 * @since 1.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SolintegInverter extends Inverter {

    private final static Logger LOGGER = LogManager.getLogger(SolintegInverter.class);

    public SolintegInverter(String sqlAddress) {
        super(sqlAddress);
    }

    public SolintegInverter(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip, serialNumber, deviceName, deviceType, sqlAddress);
    }

    @Override
    public void setAttribute(String key, Object value) {
        super.setAttribute(key, value);

        switch (key) {
            case "rPower" -> electricityMeter.setRPower(electricityMeter.getRPower() * 1000.0);
            case "sPower" -> electricityMeter.setSPower(electricityMeter.getSPower() * 1000.0);
            case "tPower" -> electricityMeter.setTPower(electricityMeter.getTPower() * 1000.0);
            case "combinedActivePower" -> electricityMeter.setCombinedActivePower(electricityMeter.getCombinedActivePower() * 1000.0);
//            case "gridSideActivePower" -> gridSide.setActivePower(gridSide.getActivePower() * 1000.0);
            case "pvPower" -> pvPower *= 1000.0;
            case "pv1Power" -> pv1.setPower(pv1.getPower() * 1000.0);
            case "pv2Power" -> pv2.setPower(pv2.getPower() * 1000.0);
            case "offGridActivePower" -> energyStorage.getOffGridAcOutput().setActivePower(energyStorage.getOffGridAcOutput().getActivePower() * 1000.0);
            case "rActivePower" -> gridSide.setRActivePower(gridSide.getRActivePower() * 1000.0);
            case "sActivePower" -> gridSide.setSActivePower(gridSide.getSActivePower() * 1000.0);
            case "tActivePower" -> gridSide.setTActivePower(gridSide.getTActivePower() * 1000.0);
            case "batteryDischargePower" -> batteryDevice.setBatteryDischargePower(batteryDevice.getBatteryDischargePower() * 1000.0);
            case "batteryChargingPower" -> batteryDevice.setBatteryChargingPower(batteryDevice.getBatteryChargingPower() * 1000.0);
        }
    }

    @Override
    public void initializeParameters() throws Exception {
        // 调节最大电压
        Map<String, Object> query = conn.queryOne("select min, max from power join device d on d.id = power.deviceId where serialNumber = ?;", List.of(serialNumber));
        if (!query.containsKey("min") || !query.containsKey("max"))
            throw new Exception(String.format("could not find any power information from database for %s", serialNumber));

        // 调节最大功率
        LOGGER.info("需要调节的最大功率参数, min={}, max={}", query.get("min"), query.get("max"));
//        setMachineRatedPower((int) query.get("max") / 1000);
        boolean inited = false;
        JsonObject ret = null;
        do {
            try {
                ret = changeMaxPower((double) query.get("min"), (double) query.get("max"));
            } catch (IOException e) {
                LOGGER.warn("尝试连接逆变器");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    LOGGER.warn("等待连接逆变器中无法睡眠");
                }
                continue;
            }

            inited = true;
        } while (!inited);

        LOGGER.info("调节逆变器功率上下限结果: {}", ret);
    }

    private JsonObject changeMaxPower(double min, double max) throws IOException {
        return command(new JsonObject().put("activePowerUpperSetting", max / 1000.0).put("activePowerLowerSetting", min / 1000.0));
    }

    public void setMachineRatedPower(int value) {
        this.machineRatedPower = value;
    }
}
