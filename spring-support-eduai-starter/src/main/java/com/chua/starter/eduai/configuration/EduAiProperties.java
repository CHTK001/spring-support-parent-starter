package com.chua.starter.eduai.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 教育 AI 模块配置。
 *
 * <p>对应 application.yml 中的 {@code plugin.eduai} 配置段（各字段含义与默认值见嵌套类 Javadoc）：</p>
 * <pre>{@code
 * plugin:
 *   eduai:
 *     marking:
 *       async: true                  # 是否异步阅卷（默认 true）
 *       timeout: 1800                # 单任务总预算（秒），运行时下限 30（默认 1800）
 *       call-timeout: 90             # 单次模型调用超时（秒）（默认 90）
 *       max-score: 5                 # 单题最大分兜底（默认 5）
 *       wrong-score-rate: 0.6        # 判错阈值，0 = 仅 0 分算错（默认 0.6）
 *       image-dir: ./data/marking    # 扫描图片持久化目录（默认 ./data/marking）
 *       resume-limit: 50             # 启动续跑未完成任务上限（默认 50）
 *       max-concurrent: 1            # 后台并发任务上限（默认 1，建议保持串行）
 *       grade-concurrency: 4         # 单任务判分调用并发，下限 1（默认 4）
 *       preprocess: true             # 图片预处理（默认 true）
 *       layout-correction: false     # ONNX 版面矫正（默认 false）
 *       extract-strategy: whole      # 任意卷识别策略 whole/region（默认 whole）
 *       submit-grace-seconds: 30     # 在线交卷宽限秒数（默认 30）
 *     paper:
 *       max-total-score: 100         # 试卷最高总分（默认 100）
 *       default-question-count: 20   # 默认题目数量（默认 20）
 *       default-score-per-question: 5 # 默认每题分值（默认 5）
 *     knowledge:
 *       vector-dim: 768              # 向量维度（默认 768，当前主代码无读取点）
 *     ai:
 *       enabled: true                # 关闭后回退到本地启发式（默认 true）
 *       provider: your-provider      # AI 服务商（当前未读取，占位声明）
 *       model: your-model            # 模型名（当前未读取，占位声明）
 *       temperature: 0.3             # 温度参数（默认 0.3，当前未读取）
 *       retry:
 *         times: 5                   # 最大重试次数，0 = 不重试（默认 5）
 *         backoff-millis: 1000       # 初始退避毫秒（默认 1000）
 *         backoff-multiplier: 2.0    # 退避倍数，<=1 按 2 处理（默认 2.0）
 *         max-backoff-millis: 20000  # 退避上限毫秒，<=0 按 15000 处理（默认 20000）
 *         retry-on-transient: true   # 除限流 429 外重试瞬时故障（默认 true）
 *     alert:                         # 顶层字段：前缀是 plugin.eduai.alert，不在 ai 之下
 *       enabled: true                # 是否启用重试耗尽告警（默认 false）
 *       webhook-url: https://your-webhook.example.com/eduai-alert
 *       timeout-millis: 5000         # 告警 HTTP 超时毫秒，<=0 按 5000 处理（默认 5000）
 *     collect:
 *       default-cron: "0 0/30 * * * ?"  # 采集默认调度周期（当前未读取，兼容声明）
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.eduai")
public class EduAiProperties {

    /**
     * 阅卷配置。
     *
     * <p>默认 {@code new Marking()}（各字段默认值见 {@link Marking}）。
     * YAML 前缀：{@code plugin.eduai.marking.*}。</p>
     */
    private Marking marking = new Marking();

    /**
     * 组卷配置。
     *
     * <p>默认 {@code new Paper()}（各字段默认值见 {@link Paper}）。
     * YAML 前缀：{@code plugin.eduai.paper.*}。</p>
     */
    private Paper paper = new Paper();

    /**
     * 知识库配置。
     *
     * <p>默认 {@code new Knowledge()}（各字段默认值见 {@link Knowledge}）。
     * YAML 前缀：{@code plugin.eduai.knowledge.*}。</p>
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * AI 引擎配置。
     *
     * <p>默认 {@code new Ai()}（各字段默认值见 {@link Ai}）。
     * YAML 前缀：{@code plugin.eduai.ai.*}。</p>
     */
    private Ai ai = new Ai();

    /**
     * 题库采集配置。
     *
     * <p>默认 {@code new Collect()}（各字段默认值见 {@link Collect}）。
     * YAML 前缀：{@code plugin.eduai.collect.*}；当前主代码无读取点（采集已收敛到 collect-starter）。</p>
     */
    private Collect collect = new Collect();

    /**
     * AI 调用失败告警配置。
     *
     * <p>默认 {@code new Alert()}（默认关闭；各字段默认值见 {@link Alert}）。
     * YAML 前缀：{@code plugin.eduai.alert.*}（注意是顶层字段，不在 {@code ai} 之下）。</p>
     */
    private Alert alert = new Alert();

    @Data
    /**
     * 阅卷配置（{@code plugin.eduai.marking.*}）。
     *
     * <p>覆盖手工提交、扫描识别、AI 判分、错题归档与启动续跑等行为；
     * 其中 {@code call-timeout}/{@code preprocess}/{@code layout-correction}/{@code extract-strategy}
     * 会经 {@code EduAiAutoConfiguration#paperPipelineProperties} 映射给 ai-starter 扫描管线。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     marking:
     *       async: true                  # 手工提交是否异步阅卷（默认 true）
     *       timeout: 1800                # 单任务总预算（秒），下限 30（默认 1800）
     *       call-timeout: 90             # 单次模型调用超时（秒）（默认 90）
     *       max-score: 5                 # 单题最大分兜底（默认 5）
     *       wrong-score-rate: 0.6        # 判错阈值，0 = 仅 0 分算错（默认 0.6）
     *       image-dir: ./data/marking    # 扫描图片持久化目录（默认 ./data/marking）
     *       resume-limit: 50             # 启动续跑未完成任务上限（默认 50）
     *       max-concurrent: 1            # 后台并发任务上限（默认 1，建议保持串行）
     *       grade-concurrency: 4         # 单任务判分调用并发，下限 1（默认 4）
     *       preprocess: true             # 图片预处理（默认 true）
     *       layout-correction: false     # ONNX 版面矫正（默认 false）
     *       extract-strategy: whole      # 任意卷识别策略 whole/region（默认 whole）
     *       submit-grace-seconds: 30     # 在线交卷宽限秒数（默认 30）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Marking {
        /**
         * 是否异步阅卷。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.eduai.marking.async: true}。</p>
         */
        private boolean async = true;

        /**
         * 单个阅卷任务的<b>总预算</b>（秒）：超过即中止，释放并发闸门，队列继续。
         *
         * <p>这里是整条链路的预算，不是单次模型调用的超时 —— 两者必须分开，否则
         * 一次模型调用卡住就会吃掉整个任务的额度。历史上两者共用同一个值（300 秒），
         * 实测 18 题外来卷需要 1 次补题干 + 18 次判分共 19 次模型调用，跑到 17/18
         * 就被「处理超时（&gt;300s）」整任务判失败，最后一题永远拿不到分数。
         * 单次调用的上限见 {@link #callTimeout}。</p>
         *
         * <p>默认 {@code 1800}；运行时下限 30 秒（{@code Math.max(30, timeout)}）。
         * YAML：{@code plugin.eduai.marking.timeout: 1800}。</p>
         */
        // [P3C A20 豁免]
        private int timeout = 1800;

        /**
         * 单次模型调用的超时（秒）：超过即视为这次调用失败，交由重试逻辑处理。
         *
         * <p>与 {@link #timeout} 分工：本项约束「一次 HTTP 请求最多等多久」，
         * 保证任何一次调用都不会无限挂住；任务总时长由 {@link #timeout} 兜底。
         * 默认 90 秒 —— 实测国产网关正常响应 5~30 秒，90 秒足够覆盖长题干，
         * 又能在网关静默挂死时较快失败并重试。</p>
         *
         * <p>默认 {@code 90}。YAML：{@code plugin.eduai.marking.call-timeout: 90}。</p>
         */
        // [P3C A20 豁免]
        private int callTimeout = 90;

        /**
         * 单题最大分（兜底值）。
         *
         * <p>默认 {@code 5}。YAML：{@code plugin.eduai.marking.max-score: 5}。</p>
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
         *
         * <p>默认 {@code 0.6}；{@code 0} 表示退回「仅 0 分算错」的旧口径。
         * YAML：{@code plugin.eduai.marking.wrong-score-rate: 0.6}。</p>
         */
        private double wrongScoreRate = 0.6D;

        /**
         * 扫描阅卷的图片存放目录。
         *
         * <p>任务是异步且可重启续跑的，输入图片必须持久保存；不能只留在内存里。</p>
         *
         * <p>默认 {@code ./data/marking}。YAML：{@code plugin.eduai.marking.image-dir: ./data/marking}。</p>
         */
        private String imageDir = "./data/marking";

        /**
         * 启动时最多续跑多少个未完成任务（防止历史积压一次性打满）。
         *
         * <p>默认 {@code 50}。YAML：{@code plugin.eduai.marking.resume-limit: 50}。</p>
         */
        // [P3C A20 豁免]
        private int resumeLimit = 50;

        /**
         * 后台同时处理的阅卷任务数上限（默认 1 = 串行）。
         *
         * <p>每个任务都要跑 ONNX 版面 + OCR，原生/直连内存占用大且不受 {@code -Xmx} 约束；
         * 并发跑多路会把整机内存打满 —— 实测 5 路并发触发内核 OOM 杀掉本服务
         * （{@code journalctl -u eduai-backend} 可见 {@code Failed with result 'oom-kill'}），
         * 重启后又续跑同样多的任务，形成崩溃循环、任务永久卡在「识别中」。
         * 故默认串行；内存充裕的机器可调大。</p>
         *
         * <p>默认 {@code 1}。YAML：{@code plugin.eduai.marking.max-concurrent: 1}。</p>
         */
        private int maxConcurrent = 1;

        /**
         * 任意卷判分时，单个任务内的模型调用并发数（虚拟线程 + 信号量限流）。
         *
         * <p>判分是 IO 密集型调用（单次 5~30 秒），逐题串行时 18 题外来卷仅判分就要
         * 数分钟；有界并发可显著缩短。不宜过大：下游网关有速率限制，并发过高触发
         * 429 限流反而更慢。自有卷（SELF）走规则/相似度判分，本身不调模型，不并发。</p>
         *
         * <p>默认 {@code 4}；运行时下限 1（{@code Math.max(1, gradeConcurrency)}）。
         * YAML：{@code plugin.eduai.marking.grade-concurrency: 4}。</p>
         */
        private int gradeConcurrency = 4;

        /**
         * 是否对上传图片做预处理（照度校正去阴影 + 灰度归一化）后再识别。
         *
         * <p>拍照卷常见「一侧亮一侧暗」，暗部题号会被识别成别的字符（实测 D→0）、
         * 题号与作答被切成不同文字块。属于输入质量问题，调模型解决不了。</p>
         *
         * <p>默认 {@code true}。YAML：{@code plugin.eduai.marking.preprocess: true}。</p>
         */
        private boolean preprocess = true;

        /**
         * 是否启用 ONNX 卡片/布局矫正（card-correction-detector）。
         *
         * <p>开启时先在 paper 扫描管线里检测卡片四边形并透视拉平，再走原有预处理、
         * 版面与 OCR；未检出或模型不可用时自动回退原图，不阻断阅卷。</p>
         *
         * <p>默认 {@code false}。YAML：{@code plugin.eduai.marking.layout-correction: false}。</p>
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
         * 配额充足、版式复杂（多栏/答题框）时再切到 {@code region}。未匹配的 key 回落 {@code whole}。</p>
         *
         * <p>默认 {@code whole}。YAML：{@code plugin.eduai.marking.extract-strategy: whole}。</p>
         */
        private String extractStrategy = "whole";

        /**
         * 在线交卷宽限秒数：考试时长到期后，仍允许提交的宽限时间（秒）。
         *
         * <p>服务端计时按「本人草稿答题卡 create_time + 考试时长」判定超时；
         * 前端倒计时与服务器时钟存在微小偏差，网络往返也需要时间，踩点交卷
         * 若不给宽限会被误拒。只约束学生在线提交链路，教师手工录入不受限。</p>
         *
         * <p>默认 {@code 30}。YAML：{@code plugin.eduai.marking.submit-grace-seconds: 30}。</p>
         */
        // [P3C A20 豁免]
        private int submitGraceSeconds = 30;
    }

    @Data
    /**
     * 组卷配置（{@code plugin.eduai.paper.*}）。
     *
     * <p>作为 AI 组卷的 YAML 侧默认值；{@code sys_ai_global_setting} 的 {@code compose}
     * 分组已配置时优先于本组取值（见 {@code PaperComposeSettingService}）。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     paper:
     *       max-total-score: 100          # 试卷最高总分（默认 100）
     *       default-question-count: 20    # 默认题目数量（默认 20）
     *       default-score-per-question: 5 # 默认每题分值（默认 5）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Paper {
        /**
         * 试卷最高总分。
         *
         * <p>默认 {@code 100}。YAML：{@code plugin.eduai.paper.max-total-score: 100}。</p>
         */
        // [P3C A20 豁免]
        private int maxTotalScore = 100;

        /**
         * 默认题目数量。
         *
         * <p>默认 {@code 20}。YAML：{@code plugin.eduai.paper.default-question-count: 20}。</p>
         */
        // [P3C A20 豁免]
        private int defaultQuestionCount = 20;

        /**
         * 默认每题分值。
         *
         * <p>默认 {@code 5}。YAML：{@code plugin.eduai.paper.default-score-per-question: 5}。</p>
         */
        private int defaultScorePerQuestion = 5;
    }

    @Data
    /**
     * 知识库配置（{@code plugin.eduai.knowledge.*}）。
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     knowledge:
     *       vector-dim: 768            # 向量维度（默认 768，当前主代码无读取点，仅声明）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Knowledge {
        /**
         * 向量维度。
         *
         * <p>默认 {@code 768}；当前主代码无读取点，仅作声明保留。
         * YAML：{@code plugin.eduai.knowledge.vector-dim: 768}。</p>
         */
        // [P3C A20 豁免]
        private int vectorDim = 768;
    }

    @Data
    /**
     * AI 引擎配置（{@code plugin.eduai.ai.*}）。
     *
     * <p>{@code enabled} 与 {@code retry} 会经 {@code EduAiAutoConfiguration#paperPipelineProperties}
     * 映射给 ai-starter 的 {@code PaperPipelineProperties}；{@code provider}/{@code model}/
     * {@code temperature} 当前主代码未读取（客户端由 ai-starter 聚合 Bean / 控制台配置提供）。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     ai:
     *       enabled: true              # 是否启用 AI，关闭后回退本地启发式（默认 true）
     *       provider: your-provider    # AI 服务商（当前未读取，占位声明）
     *       model: your-model          # 模型名（当前未读取，占位声明）
     *       temperature: 0.3           # 温度参数（默认 0.3，当前未读取）
     *       retry:
     *         times: 5                 # 最大重试次数，0 = 不重试（默认 5）
     *         backoff-millis: 1000     # 初始退避毫秒（默认 1000）
     *         backoff-multiplier: 2.0  # 退避倍数，<=1 按 2 处理（默认 2.0）
     *         max-backoff-millis: 20000 # 退避上限毫秒，<=0 按 15000 处理（默认 20000）
     *         retry-on-transient: true # 除限流 429 外重试瞬时故障（默认 true）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Ai {
        /**
         * 是否启用 AI（关闭后回退到本地启发式逻辑）。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.eduai.ai.enabled: true}。</p>
         */
        private boolean enabled = true;

        /**
         * AI 服务商（与 对话客户端registry 中的 提供者 一致）。
         *
         * <p>默认未配置（{@code null}）；当前主代码未读取。
         * YAML：{@code plugin.eduai.ai.provider: your-provider}。</p>
         */
        private String provider;

        /**
         * 模型名。
         *
         * <p>默认未配置（{@code null}）；当前主代码未读取。
         * YAML：{@code plugin.eduai.ai.model: your-model}。</p>
         */
        private String model;

        /**
         * 温度参数。
         *
         * <p>默认 {@code 0.3}；当前主代码未读取。
         * YAML：{@code plugin.eduai.ai.temperature: 0.3}。</p>
         */
        private Double temperature = 0.3;

        /**
         * AI 调用重试配置（限流 429 与瞬时故障）。
         *
         * <p>默认 {@code new Retry()}（各字段默认值见 {@link Retry}）。
         * YAML 前缀：{@code plugin.eduai.ai.retry.*}。</p>
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
     *         times: 5                 # 最大重试次数，0 = 不重试（默认 5）
     *         backoff-millis: 1000     # 初始退避毫秒（默认 1000）
     *         backoff-multiplier: 2.0  # 退避倍数，<=1 按 2 处理（默认 2.0）
     *         max-backoff-millis: 20000 # 退避上限毫秒，<=0 按 15000 处理（默认 20000）
     *         retry-on-transient: true # 除限流外，是否也重试瞬时故障（默认 true）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Retry {
        /**
         * 最大重试次数（0 = 不重试）。
         *
         * <p>默认 {@code 5}。YAML：{@code plugin.eduai.ai.retry.times: 5}。</p>
         */
        private int times = 5;

        /**
         * 初始退避毫秒。
         *
         * <p>默认 {@code 1000}。YAML：{@code plugin.eduai.ai.retry.backoff-millis: 1000}。</p>
         */
        // [P3C A20 豁免]
        private long backoffMillis = 1000L;

        /**
         * 退避倍数（<=1 时按 2 处理）。
         *
         * <p>默认 {@code 2.0}；运行时 {@code <=1} 按 {@code 2} 处理。
         * YAML：{@code plugin.eduai.ai.retry.backoff-multiplier: 2.0}。</p>
         */
        private double backoffMultiplier = 2.0D;

        /**
         * 退避上限毫秒（<=0 时按 15000 处理）。
         *
         * <p>默认 {@code 20000}；运行时 {@code <=0} 按 {@code 15000} 处理。
         * YAML：{@code plugin.eduai.ai.retry.max-backoff-millis: 20000}。</p>
         */
        // [P3C A20 豁免]
        private long maxBackoffMillis = 20000L;

        /**
         * 除限流外，是否也重试瞬时故障（超时 / 502 / 503 / 连接异常）。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.eduai.ai.retry.retry-on-transient: true}。</p>
         */
        private boolean retryOnTransient = true;
    }

    @Data
    /**
     * 题库采集配置（{@code plugin.eduai.collect.*}）。
     *
     * <p>采集调度已收敛到 collect-starter（走 {@code plugin.collect.*} 前缀），
     * 本组当前主代码无读取点，仅作兼容声明保留。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     collect:
     *       default-cron: "0 0/30 * * * ?"   # 采集任务未填 Cron 时的默认调度周期（默认值，当前未读取）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Collect {
        /**
         * 采集任务未配置 Cron 时的默认调度周期（Spring 6 Cron）。
         *
         * <p>默认 {@code "0 0/30 * * * ?"}（每 30 秒一次的等价 Cron 写法）；当前主代码无读取点（采集已收敛到 collect-starter）。
         * YAML：{@code plugin.eduai.collect.default-cron: "0 0/30 * * * ?"}。</p>
         */
        private String defaultCron = "0 */30 * * * ?";
    }

    /**
     * AI 调用重试耗尽时的告警配置。
     *
     * <p>重试全部失败后触发 HTTP POST 到指定地址，供接入钉钉/企微/飞书 Webhook 等通知渠道。
     * 默认不开启；配置 webhookUrl 后立即生效，无需重启。</p>
     *
     * <p>注意：本组是 {@code EduAiProperties} 的顶层字段，YAML 前缀是
     * {@code plugin.eduai.alert.*}，不是 {@code plugin.eduai.ai.alert.*}。</p>
     *
     * <pre>{@code
     * plugin:
     *   eduai:
     *     alert:
     *       enabled: true                                              # 是否启用告警（默认 false）
     *       webhook-url: https://your-webhook.example.com/eduai-alert  # Webhook 地址（默认空，留空不发送）
     *       timeout-millis: 5000                                       # HTTP 超时毫秒，<=0 按 5000 处理（默认 5000）
     * }</pre>
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Alert {
        /**
         * 是否启用告警。
         *
         * <p>默认 {@code false}。YAML：{@code plugin.eduai.alert.enabled: true}。</p>
         */
        private boolean enabled = false;
        /**
         * Webhook 地址（支持钉钉/企微/飞书等标准卡片格式）。
         *
         * <p>默认未配置（{@code null}）；留空时即使 {@code enabled=true} 也不发送。
         * YAML：{@code plugin.eduai.alert.webhook-url: https://your-webhook.example.com/eduai-alert}。</p>
         */
        private String webhookUrl;
        /**
         * HTTP 请求超时毫秒（<=0 时默认 5000）。
         *
         * <p>默认 {@code 5000}；运行时 {@code <=0} 按 {@code 5000} 处理。
         * YAML：{@code plugin.eduai.alert.timeout-millis: 5000}。</p>
         */
        // [P3C A20 豁免]
        private int timeoutMillis = 5000;
    }
}
