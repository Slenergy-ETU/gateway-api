package com.slenergy.gateway.api.server.device;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmsBox implements Base, ChangeAttribute{
    private String serialNumber;
    private String deviceName;
    private String deviceType;
    private int protocolType;
    private String protocolVersions;
    private double dataInterval;
    private String localIp;
    private int localPort;
    private String remoteIp;
    private int remotePort;
    private String model;
    private String firmwareVersion;
    private int wirelessType;
    private String timeZone;
    private int restart;
    private int gprs;

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("protocolType", protocolType)
                .put("protocolVersions", protocolVersions)
                .put("dataInterval", dataInterval)
                .put("datalogSn", serialNumber)
                .put("localIp", localIp)
                .put("localPort", localPort)
                .put("remoteIp", remoteIp)
                .put("remotePort", remotePort)
                .put("dataLoggerModel", model)
                .put("firmwareVersion", firmwareVersion)
                .put("wirelessType", wirelessType)
                .put("timeZone", timeZone)
                .put("dataloggerRestart", restart)
                .put("gprs", gprs);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        switch (attribute) {
            case "serialNumber"-> serialNumber = (String) value;
            case "protocolType"-> protocolType = (int) value;
            case "protocolVersions"-> protocolVersions = (String) value;
            case "dataInterval"-> dataInterval = (double) value;
            case "localIp"-> localIp = (String) value;
            case "localPort"-> localPort = (int) value;
            case "remoteIp"-> remoteIp = (String) value;
            case "remotePort"-> remotePort = (int) value;
            case "model"-> model = (String) value;
            case "firmwareVersion"-> firmwareVersion = (String) value;
            case "wirelessType"-> wirelessType = (int) value;
            case "timeZone"-> timeZone = (String) value;
            case "restart"-> restart = (int) value;
            case "gprs"-> gprs = (int) value;
        }
    }
}
