package com.slenergy.gateway.api.server.schema;

import java.io.Serializable;
import java.util.List;

/**
 * {@code class} {@code RealTimeData} description
 * 实时数据传输实体
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-12-11
 * @since 1.0-SNAPSHOT
 */
public record RealTimeData(String deviceType, String serialNumber, String deviceName, long timestamp, String timeUnit, List<TimeSeriesData> timeSeries) implements Serializable {}
