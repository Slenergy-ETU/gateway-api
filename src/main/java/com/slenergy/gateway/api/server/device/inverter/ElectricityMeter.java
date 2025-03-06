package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.ChangeAttribute;
import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class ElectricityMeter description
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
public class ElectricityMeter implements ChangeAttribute, toJson {

    private double combinedActivePower;
    private double rPower;
    private double sPower;
    private double tPower;
    private double todayReverseActiveEnergy;
    private double todayPositiveEnergy;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("combinedActivePower", combinedActivePower)
                .put("todayReverseActiveEnergy", todayReverseActiveEnergy)
                .put("rActivePower", rPower)
                .put("sActivePower", sPower)
                .put("todayPositiveEnergy", todayPositiveEnergy)
                .put("tActivePower", tPower);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        switch (attribute) {
            case "combinedActivePower" -> combinedActivePower = (double) value;
            case "rPower" -> rPower = (double) value;
            case "sPower" -> sPower = (double) value;
            case "tPower" -> tPower = (double) value;
            case "todayPositiveEnergy" -> todayPositiveEnergy = (double) value;
            case "todayReverseActiveEnergy" -> todayReverseActiveEnergy = (double) value;
        }
    }

}
