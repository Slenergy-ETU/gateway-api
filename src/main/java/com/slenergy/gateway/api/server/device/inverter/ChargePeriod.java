package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class ChargePeriod description
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
public class ChargePeriod implements toJson {

    private int chargingMode;
    private int chargeMode;
    private int startTime;
    private int endTime;
    private double powerLimit;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("chargingMode", chargingMode)
                .put("powerLimit", powerLimit)
                .put("chargeMode", chargeMode)
                .put("startTime", startTime)
                .put("endTime", endTime);
    }

}
