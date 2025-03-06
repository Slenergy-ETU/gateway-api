package com.slenergy.gateway.api.server.schema;

/**
 * {@code record} {@code DeviceRealTimeData} description
 * 用于获取单个设备的实时数据
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-12-11
 * @since 1.0-SNAPSHOT
 */
public record DeviceRealTimeData(String measurement, String deviceSN, String deviceName, String start, String stop) {}
