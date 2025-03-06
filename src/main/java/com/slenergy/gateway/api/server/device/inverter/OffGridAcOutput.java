package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.ChangeAttribute;
import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class OffGridAcOutput description
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
public class OffGridAcOutput implements ChangeAttribute, toJson {

    private double activePower;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject().put("activePower", activePower);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        if (attribute.compareTo("offGridActivePower") == 0)
            activePower = (double) value;
    }

}
