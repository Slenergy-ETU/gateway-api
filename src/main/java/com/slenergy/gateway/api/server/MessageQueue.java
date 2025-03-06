package com.slenergy.gateway.api.server;

import com.slenergy.gateway.api.server.device.IBox;
import com.slenergy.gateway.api.server.device.RealTimeDevice;
import com.slenergy.gateway.api.server.device.chargingPile.ChargingPile;
import com.slenergy.gateway.api.server.device.heatPump.HeatPump;
import com.slenergy.gateway.api.server.device.inverter.Battery;
import com.slenergy.gateway.api.server.device.inverter.Inverter;
import io.vertx.core.json.JsonObject;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * class MessageQueue description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-03-20
 * @since 1.0
 */
public final class MessageQueue {

    @Setter
    private IBox ibox;
    private final Map<String, RealTimeDevice> devices;
    private final Map<String, Inverter> inverters;
    private final Map<String, Battery> batterys;
    private final Map<String, HeatPump> heatPumps;
    private final Map<String, ChargingPile> chargingPiles;
    private final static Logger LOGGER = LogManager.getLogger(MessageQueue.class);
    private static MessageQueue INSTANCE = null;

    public static MessageQueue getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MessageQueue();
        }

        return INSTANCE;
    }

    private MessageQueue() {
        devices = new HashMap<>();
        inverters = new HashMap<>();
        batterys = new HashMap<>();
        heatPumps = new HashMap<>();
        chargingPiles = new HashMap<>();
    }

    public void addDevice(String deviceType, String sn, RealTimeDevice device) {
        switch (deviceType) {
            case "inverter" -> addInverter(sn, (Inverter) device);
            case "heatPump" -> addHeatPump(sn, (HeatPump) device);
            case "chargingPile" -> addChargingPile(sn, (ChargingPile) device);
        }
    }

    public JsonObject getIBoxSerialization() {
        return ibox.toJsonObject();
    }

    public RealTimeDevice getRealTimeDevice(String sn) {
        if (!devices.containsKey(sn))
            return null;

        return devices.get(sn);
    }

    public void addInverter(String sn, Inverter inv) {
        inverters.put(sn, inv);
        devices.put(sn, inv);
    }

    public boolean empty() {
        return inverters.isEmpty() && batterys.isEmpty() && heatPumps.isEmpty() && chargingPiles.isEmpty();
    }

    public List<Inverter> getInverters() {
        return inverters.values().stream().toList();
    }

    public void addBattery(String sn, Battery b) {
        batterys.put(sn, b);
        devices.put(sn, b);
    }

    public List<Battery> getBatterys() {
        return batterys.values().stream().toList();
    }

    public void addHeatPump(String sn, HeatPump hp) {
        heatPumps.put(sn, hp);
        devices.put(sn, hp);
    }

    public List<HeatPump> getHeatPumps() {
        return heatPumps.values().stream().toList();
    }

    public void addChargingPile(String sn, ChargingPile cp) {
        chargingPiles.put(sn, cp);
        devices.put(sn, cp);
    }

    public List<ChargingPile> getChargingPiles() {
        return chargingPiles.values().stream().toList();
    }

    public void updateChargingPileLastDayTotalEnergy() {
        for (ChargingPile cp : chargingPiles.values()) {
            cp.setLastDayEnergyActiveImportRegister(cp.getEnergyActiveImportRegister());
        }
    }

    public void changePvPriority(String sn, int pvPriority) {
        RealTimeDevice device = findPvPriority(pvPriority);
        if (device == null)
            return;

        RealTimeDevice changedDevice = devices.get(sn);
        if (changedDevice == null)
            return;

        device.setPvPriority(changedDevice.getPvPriority());
        changedDevice.setPvPriority(pvPriority);
    }

    private RealTimeDevice findPvPriority(int pvPriority) {
        for (RealTimeDevice device : devices.values()) {
            if (device.getPvPriority() == pvPriority)
                return device;
        }

        return null;
    }

    public JsonObject changePower(String sn, double power) {
        if (!devices.containsKey(sn))
            return null;

        RealTimeDevice device = devices.get(sn);
        JsonObject ret = null;
        try {
            ret = device.sendPower(power);
        } catch (Exception e) {
            LOGGER.warn("无法连接设备修改功率: {}", e.getMessage());
        }

        return ret;
    }

}
