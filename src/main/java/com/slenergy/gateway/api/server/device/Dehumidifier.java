package com.slenergy.gateway.api.server.device;

import com.slenergy.gateway.api.server.device.inverter.SlenergyInverter;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * 除湿机设备状态信息
 */

public class Dehumidifier extends CommandDevice{

    private final static Logger LOGGER = LogManager.getLogger(Dehumidifier.class);

    public Dehumidifier(String serialNumber, String deviceName, String deviceType, String sqlAddress, String ip, int port) {
        super(serialNumber, deviceName, deviceType, sqlAddress);
        this.ip = ip;
        this.port = port;
    }

    @Override
    public String sendEmsCommand(String commandStr) throws IOException {
        //指令下发
        connect();
        String result = sendCommand(commandStr);
        disconnect();
        return result;
    }

    @Override
    public void initializeParameters() throws Exception {

    }

    @Override
    public double getCurrentPower() {
        return 0;
    }
}
