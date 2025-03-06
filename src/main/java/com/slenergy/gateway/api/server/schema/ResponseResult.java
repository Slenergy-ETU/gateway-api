package com.slenergy.gateway.api.server.schema;

import java.io.Serializable;

/**
 * {@code class} {@code ResponseResult} description
 * 后端回复的数据结构
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-11-23
 * @since 1.0-SNAPSHOT
 */
public record ResponseResult<T>(int code, String message, T data) implements Serializable {}
