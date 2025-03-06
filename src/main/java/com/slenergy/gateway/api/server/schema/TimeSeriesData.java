package com.slenergy.gateway.api.server.schema;

import java.io.Serializable;

/**
 * {@code class} {@code TimeSeriesData} description
 * 时间序列数据
 *
 *  @param tag    主tag
 *  @param subTag 子tag
 *  @param field  数据区域的key
 *  @param value 数据
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-12-11
 * @since 1.0-SNAPSHOT
 */
public record TimeSeriesData(String tag, String subTag, String field, Object value) implements Serializable {}
