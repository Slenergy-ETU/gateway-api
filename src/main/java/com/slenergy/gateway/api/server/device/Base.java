package com.slenergy.gateway.api.server.device;

/**
 * interface Base description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-09
 * @since 1.0
 */
public interface Base {

    void setSerialNumber(String serialNumber);
    String getSerialNumber();
    void setDeviceName(String deviceName);
    String getDeviceName();
    void setDeviceType(String deviceType);
    String getDeviceType();

}
