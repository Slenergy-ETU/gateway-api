package com.slenergy.gateway.api.server.schema.config;

import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.Validator;

import javax.annotation.Nonnull;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * {@code class} {@code ConfigReader} description
 * 模块配置文件读取以及验证类
 *
 * @author Eric Li
 * @version 1.0-SNAPSHOT
 *
 * @since 2023-12-11
 * @since 1.0-SNAPSHOT
 */
public class ConfigReader {

    /**
     *
     * @param config 命令行参数获取的路径参数配置
     * @return 模块配置类
     * @throws IOException IO异常
     */
    public static JsonObject check(@Nonnull PathConfig config) throws IOException {
        JsonObject obj = read(config.configPath());
        JsonObject schema = read(config.schemaPath());
        if (!validateConfig(obj, schema))
            return null;

        return obj;
    }

    /**
     *
     * @param path 读取文件的路径
     * @return 反序列化后的JsonObject
     * @throws IOException 读取文件异常
     */
    private static JsonObject read(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        JsonObject obj = new JsonObject(new String(fis.readAllBytes()));
        fis.close();
        return obj;
    }

    /**
     *
     * @param obj 需要验证的对象
     * @param schema 验证信息对象
     * @return 验证结果
     */
    private static boolean validateConfig(JsonObject obj, JsonObject schema) {
        return Validator.create(JsonSchema.of(schema), new JsonSchemaOptions().setBaseUri("https://vertx.io").setDraft(Draft.DRAFT7)).validate(obj).getValid();
    }

}
