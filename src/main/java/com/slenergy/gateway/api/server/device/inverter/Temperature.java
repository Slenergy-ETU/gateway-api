package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.ChangeAttribute;
import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class Temperature description
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
public class Temperature implements ChangeAttribute, toJson {

    private double environmentTemperature;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject().put("environmentTemperature", environmentTemperature);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        if (attribute.compareTo("environmentTemperature") == 0)
            environmentTemperature = (double) value;
    }

}
