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

    /**
     * AI 调用失败告警配置
     */
    private Alert alert = new Alert();

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
         * 判错阈值：最终分 / 满分 低于该比例即记为错题。
         *
         * <p>「完成」把错题归入学生错题库时用它判错：0 表示退回旧口径（只有 0 分才算错），
         * 0.6 表示不及格（得分率低于 60%）就算错。</p>
         *
         * <p>为什么要有这个开关：满分 10 分的主观题拿 3 分，旧口径（{@code score > 0} 就跳过）
         * 会让它永远进不了错题本，学生看到的错题都是客观题，主观题薄弱点被系统性漏掉。</p>
         */
        private double wrongScoreRate = 0.6D;

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

        /**
         * 是否对上传图片做预处理（照度校正去阴影 + 灰度归一化）后再识别。
         *
         * <p>拍照卷常见「一侧亮一侧暗」，暗部题号会被识别成别的字符（实测 D→0）、
         * 题号与作答被切成不同文字块。属于输入质量问题，调模型解决不了。</p>
         */
        private boolean preprocess = true;

        /**
         * 是否启用 ONNX 卡片/布局矫正（card-correction-detector）。
         *
         * <p>开启时先在 paper 扫描管线里检测卡片四边形并透视拉平，再走原有预处理、
         * 版面与 OCR；未检出或模型不可用时自动回退原图，不阻断阅卷。</p>
         */
        private boolean layoutCorrection = false;

        /**
         * 任意卷识别策略（SPI key）。
         *
         * <p>走 {@code AnyExtractStrategy} 扩展点，内置两个实现：
         * {@code whole}（默认）= 整卷一次识别（版面规则切题 → 模型补题干 → 整卷模型 → 行规则兜底）；
         * {@code region} = 按版面区域分块识别（区域切不出来时自动回退整卷）。</p>
         *
         * <p>默认 {@code whole}：与既有行为一致，调用量最小；区域内逐块识别更细但调用量成倍上升，
         * 配额充足、版式复杂（多栏/答题框）时再切到 {@code region}。</p>
         */
        private String extractStrategy = "whole";
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

        /**
         * AI 调用重试配置（限流 429 与瞬时故障）。
         */
        private Retry retry = new Retry();
    }

    /**
     * AI 调用重试配置（限流 429 与瞬时故障）。
     *
     * <p>模型侧限流（429）与瞬时故障（超时/502/503/连接重置）不该一次失败就降级：
     * 判分会掉成 0 分（「AI 判分暂不可用，请人工复核」），抽取会整卷失败。
     * 这里把重试做成系统配置，运维可按压测/配额调次数与退避，不必改代码。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     ai:
     *       retry:
     *         times: 3                 # 最大重试次数（0 = 不重试）
     *         backoff-millis: 1000     # 初始退避
     *         backoff-multiplier: 2.0  # 每次退避倍数
     *         max-backoff-millis: 15000
     *         retry-on-transient: true # 除限流外，是否也重试瞬时故障
     * }</pre>
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Retry {
        /**
         * 最大重试次数（0 = 不重试）
         */
        private int times = 3;

        /**
         * 初始退避毫秒
         */
        private long backoffMillis = 1000L;

        /**
         * 退避倍数（<=1 时按 2 处理）
         */
        private double backoffMultiplier = 2.0D;

        /**
         * 退避上限毫秒（<=0 时按 15000 处理）
         */
        private long maxBackoffMillis = 15000L;

        /**
         * 除限流外，是否也重试瞬时故障（超时 / 502 / 503 / 连接异常）
         */
        private boolean retryOnTransient = true;
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

    /**
     * AI 调用重试耗尽时的告警配置。
     *
     * <p>重试全部失败后触发 HTTP POST 到指定地址，供接入钉钉/企微/飞书 Webhook 等通知渠道。
     * 默认不开启；配置 webhookUrl 后立即生效，无需重启。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     ai:
     *       alert:
     *         enabled: true
     *         webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
     *         timeout-millis: 5000
     * }</pre>
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Alert {
        /**
         * 是否启用告警
         */
        private boolean enabled = false;
        /**
         * Webhook 地址（支持钉钉/企微/飞书等标准卡片格式）
         */
        private String webhookUrl;
        /**
         * HTTP 请求超时毫秒（<=0 时默认 5000）
         */
        private int timeoutMillis = 5000;
    }
}
