package com.chua.starter.ai.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 生成相关配置属性
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.generate")
public class AiProperties {

    /** 文生图默认配置 */
    private Image image = new Image();

    /** 文生视频默认配置 */
    private Video video = new Video();

    /** 上色默认配置 */
    private Colorize colorize = new Colorize();

    @Data
    public static class Image {
        /** 服务商名称，如 openai、zhipu 等 */
        private String provider = "openai";
        /** API Key */
        private String apiKey;
        /** 自定义 Base URL（可选） */
        private String baseUrl;
        /** 默认模型，如 dall-e-3 */
        private String model = "dall-e-3";
        /** 默认宽度 */
        private int width = 1024;
        /** 默认高度 */
        private int height = 1024;
    }

    @Data
    public static class Video {
        private String provider = "openai";
        private String apiKey;
        private String baseUrl;
        private String model = "sora";
        private Integer duration = 5;
    }

    @Data
    public static class Colorize {
        private String provider = "openai";
        private String apiKey;
        private String baseUrl;
        private String model = "dall-e-3";
    }
}
