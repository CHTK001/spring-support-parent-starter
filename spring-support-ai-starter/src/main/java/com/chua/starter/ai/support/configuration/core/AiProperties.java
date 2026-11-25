package com.chua.starter.ai.support.configuration.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 生成配置属性，前缀 {@code plugin.ai.generate}。
 *
 * <p>文生图 / 文生视频任务的默认提供者、凭据与参数。完整 YAML 示例
 * （各键含义与默认值见字段注释）：</p>
 *
 * <pre>
 * plugin:
 *   ai:
 *     generate:
 *       enabled: true              # 生成模块装配开关（AiGenerateAutoConfiguration 读取），默认 true
 *       image:
 *         provider: openai         # 图片生成提供者，默认 openai
 *         api-key: ${IMAGE_API_KEY:change-me}
 *         base-url:                # 可空，默认 null
 *         model: dall-e-3          # 默认图片模型，默认 dall-e-3
 *         width: 1024              # 默认宽度，默认 1024
 *         height: 1024             # 默认高度，默认 1024
 *       video:
 *         provider: openai         # 视频生成提供者，默认 openai
 *         api-key: ${VIDEO_API_KEY:change-me}
 *         base-url:                # 可空，默认 null
 *         model: sora              # 默认视频模型，默认 sora
 *         duration: 5              # 默认时长（秒），默认 5
 * </pre>
 *
 * <p>注意：{@code enabled} 不是本类的绑定字段，而是 {@code AiGenerateAutoConfiguration}
 * 的装配开关（{@code plugin.ai.generate.enabled}，未配置时默认开启）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.ai.generate")
public class AiProperties {

    /**
     * 图片生成默认配置。
     *
     * <p>YAML：{@code plugin.ai.generate.image.*}；未配置时各字段走下方代码默认值。</p>
     */
    private Image image = new Image();

    /**
     * 视频生成默认配置。
     *
     * <p>YAML：{@code plugin.ai.generate.video.*}；未配置时各字段走下方代码默认值。</p>
     */
    private Video video = new Video();

    /**
     * 图片生成配置。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class Image {

        /**
         * 生成提供者（如 openai、zhipu）。
         *
         * <p>默认 {@code openai}；YAML：{@code plugin.ai.generate.image.provider}。</p>
         */
        private String provider = "openai";

        /**
         * API Key。
         *
         * <p>默认未配置（{@code null}）；YAML：{@code plugin.ai.generate.image.api-key}，建议用环境变量注入。</p>
         */
        private String apiKey;

        /**
         * 自定义 基础 URL（可选）。
         *
         * <p>默认未配置（{@code null}）；YAML：{@code plugin.ai.generate.image.base-url}。</p>
         */
        private String baseUrl;

        /**
         * 默认模型（如 dall-e-3）。
         *
         * <p>默认 {@code dall-e-3}；YAML：{@code plugin.ai.generate.image.model}。</p>
         */
        private String model = "dall-e-3";

        /**
         * 默认宽度。
         *
         * <p>默认 {@code 1024}；YAML：{@code plugin.ai.generate.image.width}。</p>
         */
        // [P3C A20 豁免]
        private int width = 1024;

        /**
         * 默认高度。
         *
         * <p>默认 {@code 1024}；YAML：{@code plugin.ai.generate.image.height}。</p>
         */
        // [P3C A20 豁免]
        private int height = 1024;
    }

    /**
     * 视频生成配置。
     *
     * @since 4.0.0.42
     * @author CH
     */
    @Data
    public static class Video {

        /**
         * 生成提供者（如 openai、zhipu）。
         *
         * <p>默认 {@code openai}；YAML：{@code plugin.ai.generate.video.provider}。</p>
         */
        private String provider = "openai";

        /**
         * API Key。
         *
         * <p>默认未配置（{@code null}）；YAML：{@code plugin.ai.generate.video.api-key}，建议用环境变量注入。</p>
         */
        private String apiKey;

        /**
         * 自定义 基础 URL（可选）。
         *
         * <p>默认未配置（{@code null}）；YAML：{@code plugin.ai.generate.video.base-url}。</p>
         */
        private String baseUrl;

        /**
         * 默认模型（如 sora）。
         *
         * <p>默认 {@code sora}；YAML：{@code plugin.ai.generate.video.model}。</p>
         */
        private String model = "sora";

        /**
         * 默认时长（秒）。
         *
         * <p>默认 {@code 5}；YAML：{@code plugin.ai.generate.video.duration}。</p>
         */
        private Integer duration = 5;
    }
}
