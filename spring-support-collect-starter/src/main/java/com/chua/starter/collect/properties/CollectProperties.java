package com.chua.starter.collect.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据采集平台配置。
 *
 * <p>配置前缀 {@code plugin.collect}。每一项都在执行引擎或调度器中被真实读取，
 * 不接受只写不读的参数。</p>
 *
 * <p>YAML 配置示例（键为前缀下的短横线形式，注释即含义与默认值）：</p>
 * <pre>{@code
 * plugin:
 *   collect:
 *     enable: true                  # 平台总开关
 *     default-thread-count: 4       # 流程默认并行线程数
 *     request-timeout-ms: 30000     # 单次抓取默认超时毫秒
 *     request-retry-times: 1        # 默认重试次数
 *     user-agent: "Mozilla/5.0 ..." # 默认 User-Agent
 *     sql-allow-write: false        # SQL 节点是否允许写语句
 *     task-timeout-ms: 1800000      # 单任务最长执行毫秒
 *     max-record-per-task: 100000   # 单任务最多落库记录数
 *     preview-length: 500           # 节点出参预览字符数
 *     node-output-limit: 2000       # 节点产出留存条数上限（0 或负数不限）
 *     exists-batch-size: 500        # 「查已存在」IN 查询每片条数
 *     script:
 *       enable: true                # 脚本节点开关
 *     function:
 *       enable: true                # 自定义函数库开关
 *     notify:
 *       enable: true                # 执行通知开关
 *     process:
 *       enable: true                # 子流程节点开关
 *       max-depth: 5                # 子流程嵌套层数上限
 *     ai:
 *       enable: true                # 「AI 生成流程」开关
 *       base-url: http://127.0.0.1:8787   # ai-starter 服务地址
 *       model: deepseek-v4.1-flash        # 生成用模型名
 *       trust-header: x-nomi-local-trust  # 本地信任头名
 *       trust-value: collect-designer     # 本地信任头的值
 *       timeout-ms: 180000                # 单次生成总超时毫秒
 *     agent:
 *       enable: true                # Agent 节点开关
 *       timeout-ms: 120000          # Agent 调用兜底超时毫秒（0 不限）
 *       failure-strategy: fail      # fail / skip / defaultValue
 *     persistence:
 *       type: database              # database / none
 *       search-enabled: true        # collect_record 全文索引开关
 *       search-index-path: ""       # 留空用 java.io.tmpdir/collect-search-index
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = CollectProperties.PRE)
public class CollectProperties {

    /**
     * 配置前缀。
     *
     * <p>与 job-starter 的 {@code JobProperties.PRE} 一样抽成常量：自动装配的
     * {@code @ConditionalOnProperty} 与这里的 {@code @ConfigurationProperties}
     * 共用同一处前缀，改名时不会漏改。</p>
     */
    public static final String PRE = "plugin.collect";

    /**
     * 默认 User-Agent 请求头。
     */
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 平台总开关，关闭后不装配控制器、服务、调度任务与执行引擎。
     *
     * <p>默认 {@code true}。YAML：{@code plugin.collect.enable: true}。</p>
     */
    private boolean enable = true;

    /**
     * 流程默认并行线程数，可被流程定义覆盖。
     *
     * <p>默认 {@code 4}。流程未配置或并行线程数 ≤0 时回落到本值。
     * YAML：{@code plugin.collect.default-thread-count: 4}。</p>
     */
    private int defaultThreadCount = 4;

    /**
     * 单次抓取默认超时毫秒，节点未显式配置时使用。
     *
     * <p>默认 {@code 30000}。节点超时参数留空或 ≤0 时回落到本值。
     * YAML：{@code plugin.collect.request-timeout-ms: 30000}。</p>
     */
    // [P3C A20 豁免]
    private int requestTimeoutMs = 30000;

    /**
     * 默认重试次数，节点未显式配置时使用。
     *
     * <p>默认 {@code 1}。节点重试参数为负时执行侧会钳到 {@code 0}。
     * YAML：{@code plugin.collect.request-retry-times: 1}。</p>
     */
    private int requestRetryTimes = 1;

    /**
     * 默认 User-Agent，节点未显式配置时使用。
     *
     * <p>默认取 {@link #DEFAULT_USER_AGENT}（Chrome/120 的 Mozilla UA）。
     * YAML：{@code plugin.collect.user-agent: <your-user-agent>}。</p>
     */
    private String userAgent = DEFAULT_USER_AGENT;

    /**
     * SQL 节点是否允许执行写语句（非 SELECT/WITH/SHOW）。
     *
     * <p>SQL 节点整段支持 {@code ${}} 插值、且不做语句白名单，插值内容可能来自被抓取的页面，
     * 因此写语句的注入面明显大于查询。默认 {@code false}：SQL 节点只放行查询（SELECT/WITH/SHOW），
     * 写库改走 database 节点（PreparedStatement 绑定、无字符串拼接）；确需放行写语句时才置 {@code true}。</p>
     *
     * <p>YAML：{@code plugin.collect.sql-allow-write: false}。</p>
     */
    private boolean sqlAllowWrite = false;

    /**
     * 单任务最长执行毫秒，超时后置为失败并中断流程线程池。
     *
     * <p>默认 {@code 1800000}（30 分钟）。早先取 10 分钟，但分片型采集很容易撞线：全唐诗示例 58 个分片、
     * 约 5.8 万条，实测跑完要 11 分钟出头，10 分钟会在收尾阶段被强制判失败，
     * 用户看到的是「跑了一半就失败」而不是真的出错。真正的失控保护由
     * {@link #maxRecordPerTask}（默认 10 万条）兜底，超时这里只防线程永久挂死。</p>
     *
     * <p>YAML：{@code plugin.collect.task-timeout-ms: 1800000}。</p>
     */
    // [P3C A20 豁免]
    private long taskTimeoutMs = 1800000L;

    /**
     * 单任务最多落库记录数，超出后丢弃并写任务日志，防止失控抓取撑爆结果表。
     *
     * <p>默认 {@code 100000}。YAML：{@code plugin.collect.max-record-per-task: 100000}。</p>
     */
    // [P3C A20 豁免]
    private int maxRecordPerTask = 100000;

    /**
     * 节点出参预览保留字符数。
     *
     * <p>默认 {@code 500}；执行侧按 {@code Math.max(32, 配置值)} 生效，配置过小时按 32 兜底。
     * YAML：{@code plugin.collect.preview-length: 500}。</p>
     */
    // [P3C A20 豁免]
    private int previewLength = 500;

    /**
     * 各节点产出留存条数上限（{@code ${nodes.<id>.字段} 的底表）。
     *
     * <p>节点产出存的是引用不是拷贝，单条不额外占内存，但一条采集动辄上万行、
     * 支路又多时累计持有量仍可达几十 MB。默认 {@code 2000}：超过后不再登记新的节点产出，
     * 已登记的照常可引用；取不到与「变量未定义」同语义，求值得 null。
     * 0 或负数表示不限。</p>
     *
     * <p>YAML：{@code plugin.collect.node-output-limit: 2000}。</p>
     */
    // [P3C A20 豁免]
    private int nodeOutputLimit = 2000;

    /**
     * 「查已存在」节点 IN 查询的每片条数。
     *
     * <p>中文成语这类数据，一个字面量就是几个字节，9429 条拼出来是几 MB SQL，
     * 会顶到 {@code max_allowed_packet}（默认 4~64MB）。分片后每片走一次
     * PreparedStatement 绑定，既不超包也无注入面。默认 {@code 500}；
     * 执行侧按 {@code Math.max(1, 配置值)} 生效，配置 ≤0 时按每片 1 条兜底。</p>
     *
     * <p>YAML：{@code plugin.collect.exists-batch-size: 500}。</p>
     */
    // [P3C A20 豁免]
    private int existsBatchSize = 500;

    /**
     * 脚本节点配置。
     *
     * <p>YAML：{@code plugin.collect.script.enable: true}（字段见 {@link Script}）。</p>
     */
    private Script script = new Script();

    /**
     * 执行通知配置。
     *
     * <p>YAML：{@code plugin.collect.notify.enable: true}（字段见 {@link Notify}）。</p>
     */
    private Notify notify = new Notify();

    /**
     * 自定义函数库配置。
     *
     * <p>YAML：{@code plugin.collect.function.enable: true}（字段见 {@link Function}）。</p>
     */
    private Function function = new Function();

    /**
     * 子流程节点配置。
     *
     * <p>YAML：{@code plugin.collect.process.enable: true}（字段见 {@link Process}）。</p>
     */
    private Process process = new Process();

    /**
     * AI 生成流程配置（设计器「AI 生成」）。
     *
     * <p>YAML：{@code plugin.collect.ai.base-url: http://127.0.0.1:8787}（字段见 {@link Ai}）。</p>
     */
    private Ai ai = new Ai();

    /**
     * Agent 节点配置（采集流程里的 agent 节点）。
     *
     * <p>YAML：{@code plugin.collect.agent.timeout-ms: 120000}（字段见 {@link Agent}）。</p>
     */
    private Agent agent = new Agent();

    /**
     * 结果持久化配置。
     *
     * <p>YAML：{@code plugin.collect.persistence.type: database}（字段见 {@link Persistence}）。</p>
     */
    private Persistence persistence = new Persistence();

    /**
     * 脚本节点开关。
     *
     * <p>关闭后脚本执行器不再注册，工具箱里不会出现脚本节点，
     * 已保存的含脚本节点的流程在保存与运行校验时报"类型未注册"。</p>
     */
    @Data
    public static class Script {

        /**
         * 是否启用脚本节点。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.collect.script.enable: true}。</p>
         */
        private boolean enable = true;
    }

    /**
     * 自定义函数库开关。
     *
     * <p>关闭后注册表不再挂到函数解析链上，{@code ${}} 表达式与脚本里的 {@code fn} 都只剩内置函数；
     * 函数管理接口不再装配，已登记的函数定义保留在库里不动。</p>
     */
    @Data
    public static class Function {

        /**
         * 是否启用自定义函数库。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.collect.function.enable: true}。</p>
         */
        private boolean enable = true;
    }

    /**
     * 子流程节点配置。
     *
     * <p>关闭后子流程执行器不再注册，工具箱里不会出现子流程节点，已保存的含该节点的流程
     * 在保存与运行校验时报"类型未注册"。</p>
     */
    @Data
    public static class Process {

        /**
         * 是否启用子流程节点。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.collect.process.enable: true}。</p>
         */
        private boolean enable = true;

        /**
         * 子流程嵌套层数上限，超过后节点直接失败，防止引用链过深耗尽线程池。
         *
         * <p>默认 {@code 5}；执行侧按 {@code Math.max(1, 配置值)} 生效，配置 ≤0 时按 1 层兜底。
         * YAML：{@code plugin.collect.process.max-depth: 5}。</p>
         */
        private int maxDepth = 5;
    }

    /**
     * 执行通知开关。
     *
     * <p>关闭后任务终态不再投递通知，配置页的试发也会直接失败并给出原因。</p>
     */
    @Data
    public static class Notify {

        /**
         * 是否启用执行通知。
         *
         * <p>默认 {@code true}。YAML：{@code plugin.collect.notify.enable: true}。</p>
         */
        private boolean enable = true;
    }

    /**
     * AI 生成流程配置。
     *
     * <p>设计器「AI 生成」把自然语言描述发给 ai-starter（OpenAI 兼容
     * {@code /v2/v1/chat/completions}），由模型产出流程 DSL；生成的 DSL 走与保存
     * 相同的图校验（{@code CollectFlowService#normalizeDsl}）后才返回给前端。</p>
     *
     * <p>ai-starter 的 {@code LocalTrustAuthFilter} 对回环来源放行
     * {@code x-nomi-local-trust} 头，因此默认配置只在采集后端与 ai-starter
     * 同机部署时可用（与 198 的实际部署形态一致）。</p>
     */
    @Data
    public static class Ai {

        /**
         * 是否启用「AI 生成流程」。
         *
         * <p>默认 {@code true}；还要求类路径存在 {@code AiChatFacadeService}。
         * YAML：{@code plugin.collect.ai.enable: true}。</p>
         */
        private boolean enable = true;

        /**
         * ai-starter 服务地址（与采集后端同机时用回环）。
         *
         * <p>默认 {@code http://127.0.0.1:8787}。
         * YAML：{@code plugin.collect.ai.base-url: http://127.0.0.1:8787}。</p>
         */
        private String baseUrl = "http://127.0.0.1:8787";

        /**
         * 模型名，须为 ai-starter 中已启用的模型（见其 GET /v2/v1/models 与供应商模型清单）。
         *
         * <p>默认 {@code deepseek-v4.1-flash}。
         * YAML：{@code plugin.collect.ai.model: deepseek-v4.1-flash}。</p>
         */
        private String model = "deepseek-v4.1-flash";

        /**
         * 本地信任头名（ai-starter 约定）。
         *
         * <p>默认 {@code x-nomi-local-trust}。
         * YAML：{@code plugin.collect.ai.trust-header: x-nomi-local-trust}。</p>
         */
        private String trustHeader = "x-nomi-local-trust";

        /**
         * 本地信任头的值（服务端只要求非空，内容不校验）。
         *
         * <p>默认 {@code collect-designer}。
         * YAML：{@code plugin.collect.ai.trust-value: collect-designer}。</p>
         */
        private String trustValue = "collect-designer";

        /**
         * 单次生成总超时毫秒（整图 JSON 生成较慢，放宽）。
         *
         * <p>默认 {@code 180000}。YAML：{@code plugin.collect.ai.timeout-ms: 180000}。</p>
         */
        // [P3C A20 豁免]
        private int timeoutMs = 180000;
    }

    /**
     * Agent 节点配置。
     *
     * <p>采集流程的 agent 节点经运行期 {@code AgentRegistry} 调用平台 Agent；
     * 这里只放节点未显式配置时的兜底值，节点参数优先于本配置。</p>
     */
    @Data
    public static class Agent {

        /**
         * 是否启用 Agent 节点。
         *
         * <p>对应 {@code plugin.collect.agent.enable}（执行器装配开关，未配置即开启）；
         * 关闭后 Agent 执行器不再注册，工具箱里不会出现 Agent 节点，
         * 已保存的含 Agent 节点的流程在保存与运行校验时报「类型未注册」。</p>
         *
         * <p>默认 {@code true}。YAML：{@code plugin.collect.agent.enable: true}。</p>
         */
        private boolean enable = true;

        /**
         * Agent 调用兜底超时毫秒；节点 callTimeoutMs 留空时使用，0 表示不限制。
         *
         * <p>Agent 调用是长阻塞操作，超时只中断等待并按失败策略处置，
         * 不保证底层 Agent 立即停止。</p>
         *
         * <p>默认 {@code 120000}。YAML：{@code plugin.collect.agent.timeout-ms: 120000}。</p>
         */
        // [P3C A20 豁免]
        private long timeoutMs = 120000L;

        /**
         * 默认失败策略：fail 中断流程 / skip 记警告后跳过 / defaultValue 写入兜底值继续。
         *
         * <p>默认 {@code fail}；节点参数优先于本配置。
         * YAML：{@code plugin.collect.agent.failure-strategy: fail}。</p>
         */
        private String failureStrategy = "fail";
    }

    /**
     * 结果持久化配置。
     *
     * <p>默认把节点日志与采集结果写入本模块数据表；宿主注册自己的
     * {@code CollectSinkFactory} Bean 即可整体替换持久化实现（如写 ES、写数仓）。</p>
     */
    @Data
    public static class Persistence {

        /**
         * 内置实现：database=数据库落库；none=只跑流程不落结果。
         *
         * <p>仅对默认工厂生效；宿主替换工厂 Bean 后本项不再参与选择。</p>
         *
         * <p>默认 {@code database}。YAML：{@code plugin.collect.persistence.type: database}。</p>
         */
        private String type = "database";

        /**
         * 是否启用结果记录 Lucene 全文索引。
         *
         * <p>开启后 {@code collect_record} 落库/删除会同步维护内嵌 Lucene 索引，
         * {@code /v2/collect/record/search} 走倒排检索；关闭后检索接口降级为
         * 数据库 LIKE 模糊查询。索引目录损坏时自动降级为不可用（只告警不阻断采集）。</p>
         *
         * <p>默认 {@code true}。YAML：{@code plugin.collect.persistence.search-enabled: true}。</p>
         */
        private boolean searchEnabled = true;

        /**
         * Lucene 索引目录；留空时使用 {@code ${java.io.tmpdir}/collect-search-index}。
         *
         * <p>索引可随时从结果表全量重建（POST /v2/collect/record/search/rebuild），
         * 因此放在临时目录也不会永久丢数据；生产建议显式指向稳定盘。</p>
         *
         * <p>默认空串（回落到 java.io.tmpdir 下的 collect-search-index 目录）。
         * YAML：{@code plugin.collect.persistence.search-index-path: ""}。</p>
         */
        private String searchIndexPath = "";
    }

}
