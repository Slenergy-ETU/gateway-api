package com.slenergy.gateway.api.server.schema;

import lombok.Getter;

/**
 * {@code enum} {@code ResponseEnum} description
 * HTTP回复的代码以及信息
 *
 * <p>
 * - {@link ResponseEnum#SUCCESS}: 成功调用
 * </p>
 *
 * <p>
 * <pre>{@code
 * ResponseEnum resp = ResponseEnum.SUCCESS;
 * int code = resp.getCode();
 * String message = resp.getMessage();
 * }</pre>
 * </p>
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-11-30
 * @since 1.0-SNAPSHOT
 */
@Getter
public enum ResponseEnum {
    /**
     *
     */
    SUCCESS(20000, "成功调用"),
    FAILED(40000, "没有找到数据");

    /**
     * 枚举常量的代码
     * -- GETTER --
     *
     * @return 返回常量代码

     */
    private final int code;

    /**
     * 枚举常量的描述
     * -- GETTER --
     *
     * @return 返回常量描述

     */
    private final String message;

    /**
     *
     * @param code 常量代码
     * @param message 常量描述
     */
    ResponseEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
