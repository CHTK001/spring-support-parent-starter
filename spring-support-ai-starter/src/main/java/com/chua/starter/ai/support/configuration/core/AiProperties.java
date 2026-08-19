package com.chua.starter.ai.support.configuration.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 生成配置属性。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.generate")
public class AiProperties {

    /**
     * 图片生成默认配置
     */
    private Image image = new Image();

    /**
     * 视频生成默认配置
     */
    private Video video = new Video();

    /**
     * 图片上色默认配置
     */
    private Colorize colorize = new Colorize();

    /**
     * 图片生成配置。
     *
     * @since 4.0.0.42
     */
    @Data
    public static class Image {

        /**
         * 生成提供者（如 openai、zhipu）
         */
        private String provider = "openai";

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 自定义 Base URL（可选）
         */
        private String baseUrl;

        /**
         * 默认模型（如 dall-e-3）
         */
        private String model = "dall-e-3";

        /**
         * 默认宽度
         */
        private int width = 1024;

        /**
         * 默认高度
         */
        private int height = 1024;
    }

    /**
     * 视频生成配置。
     *
     * @since 4.0.0.42
     */
    @Data
    public static class Video {

        /**
         * 生成提供者（如 openai、zhipu）
         */
        private String provider = "openai";

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 自定义 Base URL（可选）
         */
        private String baseUrl;

        /**
         * 默认模型（如 sora）
         */
        private String model = "sora";

        /**
         * 默认时长（秒）
         */
        private Integer duration = 5;
    }

    /**
     * 图片上色配置。
     *
     * @since 4.0.0.42
     */
    @Data
    public static class Colorize {

        /**
         * 生成提供者（如 openai、zhipu）
         */
        private String provider = "openai";

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 自定义 Base URL（可选）
         */
        private String baseUrl;

        /**
         * 默认模型（如 dall-e-3）
         */
        private String model = "dall-e-3";
    }
}
