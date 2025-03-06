package com.slenergy.gateway.api.server.device;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * 液冷机设备状态信息
 */

public class LiquidCooling extends CommandDevice{

    private final static Logger LOGGER = LogManager.getLogger(LiquidCooling.class);

    public LiquidCooling(String serialNumber, String deviceName, String deviceType, String sqlAddress, String ip, int port) {
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
