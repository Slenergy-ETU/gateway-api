package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.GWapi;
import com.slenergy.gateway.api.server.device.CommandDevice;
import com.slenergy.gateway.api.server.device.RealTimeDevice;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class SlenergyInverter extends CommandDevice {

    private final static Logger LOGGER = LogManager.getLogger(SlenergyInverter.class);

    public SlenergyInverter(String serialNumber, String deviceName, String deviceType, String sqlAddress) {
        super(serialNumber, deviceName, deviceType, sqlAddress);
    }


    @Override
    public double getCurrentPower() {
        return 0;
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
}
