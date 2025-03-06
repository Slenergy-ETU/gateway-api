package com.slenergy.gateway.api.server.device.heatPump;

/**
 * record TemperaturePowerDifference description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-15
 * @since 1.0
 */
public record TemperaturePowerDifference(int ambinentTemp, int outletTemp, double power, double difference) {}
