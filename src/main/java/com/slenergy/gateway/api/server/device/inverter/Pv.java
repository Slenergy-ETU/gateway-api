package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.ChangeAttribute;
import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class Pv description
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
public class Pv implements ChangeAttribute, toJson {

    private double current;
    private double voltage;
    private double power;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("current", current)
                .put("voltage", voltage)
                .put("power", power);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        switch (attribute) {
            case "voltage" -> voltage = (double) value;
            case "current" -> current = (double) value;
            case "power" -> power = (double) value;
        }
    }

}
