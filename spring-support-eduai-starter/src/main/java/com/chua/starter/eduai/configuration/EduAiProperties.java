package com.chua.starter.eduai.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 教育 AI 模块配置。
 *
 * <p>对应 application.yml 中的 {@code plugin.eduai} 配置段：</p>
 * <pre>{@code
 * plugin:
 *   eduai:
 *     marking:
 *       async: true                # 是否异步阅卷
 *       timeout: 300               # 阅卷超时（秒）
 *       max-score: 5               # 单题最大分（兜底）
 *     paper:
 *       max-total-score: 100       # 试卷最高总分
 *       default-question-count: 20 # 默认题目数量
 *       default-score-per-question: 5
 *     knowledge:
 *       vector-dim: 768            # 向量维度
 *     ai:
 *       provider: openai           # AI 服务商
 *       model: gpt-3.5-turbo
 *       temperature: 0.3
 *       enabled: true              # 关闭后回退到字符串匹配
 *     collect:
 *       default-cron: 0 0/30 * * * ?  # 采集任务未填 Cron 时的默认调度周期
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.eduai")
public class EduAiProperties {

    /**
     * 阅卷配置
     */
    private Marking marking = new Marking();

    /**
     * 组卷配置
     */
    private Paper paper = new Paper();

    /**
     * 知识库配置
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * AI 引擎配置
     */
    private Ai ai = new Ai();

    /**
     * 题库采集配置
     */
    private Collect collect = new Collect();

    @Data
    /**
     * Marking类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Marking {
        /**
         * 是否异步阅卷
         */
        private boolean async = true;

        /**
         * 阅卷超时（秒），由调用方在 对话scope.超时millis 中体现
         */
        private int timeout = 300;

        /**
         * 单题最大分（兜底值）
         */
        private int maxScore = 5;

        /**
         * 扫描阅卷的图片存放目录。
         *
         * <p>任务是异步且可重启续跑的，输入图片必须持久保存；不能只留在内存里。</p>
         */
        private String imageDir = "./data/marking";

        /**
         * 启动时最多续跑多少个未完成任务（防止历史积压一次性打满）。
         */
        private int resumeLimit = 50;

        /**
         * 后台同时处理的阅卷任务数上限（默认 1 = 串行）。
         *
         * <p>每个任务都要跑 ONNX 版面 + OCR，原生/直连内存占用大且不受 {@code -Xmx} 约束；
         * 并发跑多路会把整机内存打满 —— 实测 5 路并发触发内核 OOM 杀掉本服务
         * （{@code journalctl -u eduai-backend} 可见 {@code Failed with result 'oom-kill'}），
         * 重启后又续跑同样多的任务，形成崩溃循环、任务永久卡在「识别中」。
         * 故默认串行；内存充裕的机器可调大。</p>
         */
        private int maxConcurrent = 1;
    }

    @Data
    /**
     * Paper类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Paper {
        /**
         * 试卷最高总分
         */
        private int maxTotalScore = 100;

        /**
         * 默认题目数量
         */
        private int defaultQuestionCount = 20;

        /**
         * 默认每题分值
         */
        private int defaultScorePerQuestion = 5;
    }

    @Data
    /**
     * 知识类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Knowledge {
        /**
         * 向量维度
         */
        private int vectorDim = 768;
    }

    @Data
    /**
     * AI类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Ai {
        /**
         * 是否启用 AI（关闭后回退到本地启发式逻辑）
         */
        private boolean enabled = true;

        /**
         * AI 服务商（与 对话客户端registry 中的 提供者 一致）
         */
        private String provider;

        /**
         * 模型名
         */
        private String model;

        /**
         * 温度参数
         */
        private Double temperature = 0.3;
    }

    @Data
    /**
     * 采集类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Collect {
        /**
         * 采集任务未配置 Cron 时的默认调度周期（Spring 6 Cron）
         */
        private String defaultCron = "0 */30 * * * ?";
    }
}
