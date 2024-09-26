package com.chua.report.server.starter.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.velocity.app.Velocity;

import java.util.Properties;

import static org.apache.commons.codec.CharEncoding.UTF_8;

/**
 * VelocityEngine工厂
 *
 * @author ruoyi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VelocityInitializer {

    /**
     * 初始化vm方法
     */
    public static void initVelocity() {
        Properties p = new Properties();
        try {
            // 加载classpath目录下的vm文件
            p.setProperty("resource.loader.file.class", "com.chua.report.server.starter.loader.StringCustomResourceLoader");
            p.setProperty("root", "");
            // 定义字符集
            p.setProperty(Velocity.INPUT_ENCODING, UTF_8);
            // 初始化Velocity引擎，指定配置Properties
            Velocity.reset();
            Velocity.init(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void initFileVelocity() {
        Properties p = new Properties();
        try {
            // 加载classpath目录下的vm文件
            p.setProperty("resource.loader.file.class", "com.chua.report.server.starter.loader.CustomResourceLoader");
            p.setProperty("root", "");
            // 定义字符集
            p.setProperty(Velocity.INPUT_ENCODING, UTF_8);
            Velocity.reset();
            // 初始化Velocity引擎，指定配置Properties
            Velocity.init(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
