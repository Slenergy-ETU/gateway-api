package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.ChangeAttribute;
import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * class EnergyStorage description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-03-20
 * @since 1.0
 */
@Data
@AllArgsConstructor
public class EnergyStorage implements ChangeAttribute, toJson {

    private OffGridAcOutput offGridAcOutput;
    private Electricity electricity;

    public EnergyStorage() {
        offGridAcOutput = new OffGridAcOutput();
        electricity = new Electricity();
    }

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("offGridAcOutputInformation", offGridAcOutput.toJsonObject())
                .put("electricityInformation", electricity.toJsonObject());
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        switch (attribute) {
            case "offGridActivePower" -> offGridAcOutput.setActivePower((double) value);
            case "todayElectricitySoldByGrid" -> electricity.setTodayElectricitySoldByGrid((double) value);
            case "todayGridElectricityPurchase" -> electricity.setTodayGridElectricityPurchase((double) value);
            case "todayEnergyUsed" -> electricity.setTodayEnergyUsed((double) value);
        }
    }

}
