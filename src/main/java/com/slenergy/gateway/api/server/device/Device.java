package com.slenergy.gateway.api.server.device;

import com.slenergy.gateway.ems.Power;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * class Device description
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
public abstract class Device extends Communicator implements Base {

    protected String serialNumber;
    protected String deviceName;
    protected String deviceType;

    public Device(Power power, int pvPriority, double batteryPowerLimited, double utilityPowerLimited, int capacity, int timeout, int port, String ip, String serialNumber, String deviceName, String deviceType) {
        super(power, pvPriority, batteryPowerLimited, utilityPowerLimited, capacity, timeout, port, ip);
        this.serialNumber = serialNumber;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
    }

}
