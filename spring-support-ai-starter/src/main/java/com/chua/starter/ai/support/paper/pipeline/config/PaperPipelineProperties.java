package com.chua.starter.ai.support.paper.pipeline.config;

import lombok.Data;

/**
 * 试卷管线运行时参数。
 *
 * <p>只描述「把卷面读成题目并判分」这条链路自己需要的开关与预算，
 * 不绑定任何业务配置前缀：业务模块通过 {@code @Bean} 提供本对象，
 * 未提供时由 AI 模块给一份默认值，保证只引 ai-starter 的宿主也能启动。</p>
 *
 * <p>与业务配置（如 {@code plugin.eduai.marking.*} 里的异步、题分、错题阈值、
 * 图片目录）严格分开：那些属于阅卷工作流，留在业务模块。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class PaperPipelineProperties {

    /**
     * 阅卷链路参数
     */
    private Marking marking = new Marking();

    /**
     * 模型调用参数
     */
    private Ai ai = new Ai();

    /**
     * 重试耗尽告警
     */
    private Alert alert = new Alert();

    /**
     * 阅卷链路参数
     */
    @Data
    public static class Marking {

        /**
         * 单次模型调用超时（秒）
         */
        // [P3C A20 豁免]
        private int callTimeout = 90;

        /**
         * 是否对卷面做图片预处理
         */
        private boolean preprocess = true;

        /**
         * 是否做版面矫正（默认关闭）
         */
        private boolean layoutCorrection = false;

        /**
         * 任意卷识别策略：whole / region
         */
        private String extractStrategy = "whole";
    }

    /**
     * 模型调用参数
     */
    @Data
    public static class Ai {

        /**
         * 总开关：关闭后回退到字符串匹配，不调用模型
         */
        private boolean enabled = true;

        /**
         * 重试策略
         */
        private Retry retry = new Retry();
    }

    /**
     * 重试策略
     */
    @Data
    public static class Retry {

        /**
         * 最大重试次数（0 = 不重试）
         */
        private int times = 5;

        /**
         * 初始退避毫秒
         */
        // [P3C A20 豁免]
        private long backoffMillis = 1000L;

        /**
         * 退避倍数（&lt;=1 按 2 处理）
         */
        private double backoffMultiplier = 2.0D;

        /**
         * 退避上限毫秒（&lt;=0 按 15000 处理）
         */
        // [P3C A20 豁免]
        private long maxBackoffMillis = 20000L;

        /**
         * 除限流外是否也重试瞬时故障（超时 / 502 / 503）
         */
        private boolean retryOnTransient = true;
    }

    /**
     * 重试耗尽告警
     */
    @Data
    public static class Alert {

        /**
         * 是否启用
         */
        private boolean enabled = false;

        /**
         * 告警 Webhook 地址
         */
        private String webhookUrl;

        /**
         * 告警请求超时（毫秒）
         */
        // [P3C A20 豁免]
        private int timeoutMillis = 5000;
    }
}
