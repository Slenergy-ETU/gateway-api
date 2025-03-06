package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class Electricity description
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
public class Electricity implements toJson {

    private double todayElectricitySoldByGrid;
    private double todayEnergyUsed;
    private double todayGridElectricityPurchase;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("todayElectricitySoldByGrid", todayElectricitySoldByGrid)
                .put("todayEnergyUsed", todayEnergyUsed)
                .put("todayGridElectricityPurchase", todayGridElectricityPurchase);
    }

}
