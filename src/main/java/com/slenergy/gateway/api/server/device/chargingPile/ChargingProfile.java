package com.slenergy.gateway.api.server.device.chargingPile;

import com.slenergy.gateway.api.server.device.toJson;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/**
 * class ChargingProfile description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-03-20
 * @since 1.0
 */
@Data
@AllArgsConstructor
public class ChargingProfile implements toJson {

    private int connectorId;
    private int chargingProfileId;
    private int stackLevel;
    private int duration;
    private long startPeriod;
    private int limit;
    private String chargingProfilePurpose;
    private String chargingProfileKind;
    private String chargingRateUnit;
    private Date startSchedule;

    public ChargingProfile() {
        connectorId = 1;
        chargingProfileId = 1;
        stackLevel = 10;
        duration = (int) Duration.ofHours(2).getSeconds();
        limit = 0;
        startPeriod = 0;
        chargingProfilePurpose = "ChargePointMaxProfile";
        chargingProfileKind = "Absolute";
        chargingRateUnit = "A";
        startSchedule = new Date();
    }

    public ChargingProfile(int connectorId) {
        this.connectorId = connectorId;
        chargingProfileId = 1;
        stackLevel = 10;
        duration = (int) Duration.ofHours(2).getSeconds();
        limit = 0;
        startPeriod = 0;
        chargingProfilePurpose = "ChargePointMaxProfile";
        chargingProfileKind = "Absolute";
        chargingRateUnit = "A";
        startSchedule = new Date();
    }

    public void setAttribute(String key, Object value) {
        switch (key) {
            case "connectorId" -> connectorId = (int) value;
            case "chargingProfileId" -> chargingProfileId = (int) value;
            case "stackLevel" -> stackLevel = (int) value;
            case "duration" -> duration = (int) value;
            case "startPeriod" -> startPeriod = (int) value;
            case "limit" -> limit = (int) value;
            case "chargingProfilePurpose" -> chargingProfilePurpose = (String) value;
            case "chargingProfileKind" -> chargingProfileKind = (String) value;
            case "chargingRateUnit" -> chargingRateUnit = (String) value;
            case "startSchedule" -> setStartScheduleByDifferentVariable(value);
        }
    }

    public JsonObject toJsonObject() {
        return new JsonObject()
                .put("connectorId", connectorId)
                .put("chargingProfileId", chargingProfileId)
                .put("stackLevel", stackLevel)
                .put("duration", duration)
                .put("startPeriod", startPeriod)
                .put("limit", limit)
                .put("chargingProfilePurpose", chargingProfilePurpose)
                .put("chargingProfileKind", chargingProfileKind)
                .put("chargingRateUnit", chargingRateUnit)
                .put("startSchedule", getStartSchedule("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }

    public void setStartScheduleByDifferentVariable(Object value) {
        if (value instanceof Long)
            setStartSchedule((long) value);
        else if (value instanceof String)
            setStartSchedule((String) value, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        else if (value instanceof Date)
            setStartSchedule((Date) value);
    }

    public void setStartSchedule(Date startSchedule) {
        if (startSchedule != null && startSchedule.after(this.startSchedule))
            this.startSchedule = startSchedule;
    }

    public void setStartSchedule(String dateString, String format) {
        Date temp = startSchedule;
        try {
            startSchedule = new SimpleDateFormat(format).parse(dateString);
        } catch (ParseException pe) {
            startSchedule = temp;
        }
    }

    public void setStartSchedule(long ts) {
        startSchedule = new Date(ts);
    }

    public String getStartSchedule(String format) {
        return new SimpleDateFormat(format).format(startSchedule);
    }

}
