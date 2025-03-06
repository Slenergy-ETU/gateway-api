package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class ChargePeriodEnabled description
 *
 * @author Eric Li
 * @since 2024-03-20
 * @since
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargePeriodEnabled implements toJson {

    private int periodEnabledFlag;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject().put("periodEnabledFlag", periodEnabledFlag);
    }

}
