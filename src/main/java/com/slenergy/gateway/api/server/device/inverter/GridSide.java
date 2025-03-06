package com.slenergy.gateway.api.server.device.inverter;

import com.slenergy.gateway.api.server.device.ChangeAttribute;
import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class GridSide description
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
public class GridSide implements ChangeAttribute, toJson {

    private double tActivePower;
    private double tFrequency;
    private double tCurrent;
    private double tVoltage;
    private double rActivePower;
    private double rFrequency;
    private double rCurrent;
    private double rVoltage;
    private double sActivePower;
    private double sFrequency;
    private double sCurrent;
    private double sVoltage;
    private double activePower;

    @Override
    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("tActivePower", tActivePower)
                .put("tFrequency", tFrequency)
                .put("tCurrent", tCurrent)
                .put("tVoltage", tVoltage)
                .put("rActivePower", rActivePower)
                .put("rFrequency", rFrequency)
                .put("rCurrent", rCurrent)
                .put("rVoltage", rVoltage)
                .put("sActivePower", sActivePower)
                .put("sFrequency", sFrequency)
                .put("sCurrent", sCurrent)
                .put("sVoltage", sVoltage)
                .put("activePower", activePower);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        switch (attribute) {
            case "rVoltage" -> setRVoltage((double) value);
            case "rCurrent" -> setRCurrent((double) value);
            case "rFrequency" -> setRFrequency((double) value);
            case "rActivePower" -> setRActivePower((double) value);
            case "sVoltage" -> setSVoltage((double) value);
            case "sCurrent" -> setSCurrent((double) value);
            case "sFrequency" -> setSFrequency((double) value);
            case "sActivePower" -> setSActivePower((double) value);
            case "tVoltage" -> setTVoltage((double) value);
            case "tCurrent" -> setTCurrent((double) value);
            case "tFrequency" -> setTFrequency((double) value);
            case "tActivePower" -> setTActivePower((double) value);
            case "gridSideActivePower" -> setActivePower((double) value);
//            case "gridSideActivePower" -> setActivePower(electricityMeter.getCombinedActivePower());      //这里是因为老王和蔡工协商，电表测量值和电网侧值相同
        }
    }

}
