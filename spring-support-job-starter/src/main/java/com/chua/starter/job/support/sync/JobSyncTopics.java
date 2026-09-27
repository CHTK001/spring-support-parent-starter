package com.chua.starter.job.support.sync;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 自研同步通道使用的 topic 常量。
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JobSyncTopics {

    /**
     * 调度命令 topic (调度器 → 执行器)
     */
    public static final String TASK_COMMAND = "job-cmd";

    /**
     * 执行结果 topic (执行器 → 调度器)
     */
    public static final String TASK_LOG = "job-log";

    /**
     * 注册心跳 topic
     */
    public static final String REGISTER = "job-reg";

    /**
     * 执行器处理器清单上报 topic (执行器 → 调度器)
     *
     * <p>分布式下调度中心节点本地没有业务处理器，只有执行器知道"这个任务到底能不能跑"。
     * 执行器在注册后与处理器集合变化时上报清单，调度中心据此校正"未注册"判定与执行器列表。</p>
     */
    public static final String HANDLERS = "job-handlers";

    /**
     * 脚本正文同步 topic (调度器 → 执行器广播)
     *
     * <p>脚本正文只在"保存/执行器上线"时推送一次，派发命令只带版本号（glueAt），
     * 避免每一拍都把正文塞进命令负载。</p>
     */
    public static final String GLUE = "job-glue";

    /**
     * 手动派发的命令负载前缀。
     *
     * <p>命令负载本身只有一个任务名，执行器分不清这一拍是定时到的还是人点的。前缀写在负载里，
     * 日志才能把「手动」和「定时」记成两种触发方式，而不是照抄台账的调度类型。</p>
     */
    private static final String MANUAL_PREFIX = "MANUAL|";

    /**
     * 创建 作业同步topics 实例
    */
    private JobSyncTopics() {
    }

    /**
     * 构造手动派发负载。
     *
     * @param jobName 任务名
     * @return 带手动前缀的负载
     */
    public static String manualCommand(String jobName) {
        return MANUAL_PREFIX + jobName;
    }

    /**
     * 取出负载里的任务名（去掉手动前缀）。
     *
     * @param payload 命令负载
     * @return 任务名
     */
    public static String commandJobName(String payload) {
        return isManualCommand(payload) ? payload.substring(MANUAL_PREFIX.length()) : payload;
    }

    /**
     * 判断这一拍是不是手动派发。
     *
     * @param payload 命令负载
     * @return 手动派发返回 true
     */
    public static boolean isManualCommand(String payload) {
        return payload != null && payload.startsWith(MANUAL_PREFIX);
    }

    /**
     * 构造带执行策略的派发负载（无脚本）。
     *
     * @param jobName           任务名（处理器名）
     * @param manual            是否手动派发
     * @param failRetry         失败重试次数，null/0 不重试
     * @param executeTimeoutSec 执行超时秒数，null/0 不限
     * @return 派发负载
     */
    public static String command(String jobName, boolean manual, Integer failRetry, Integer executeTimeoutSec) {
        return command(jobName, manual, failRetry, null, executeTimeoutSec, null, null, null,
                null, null, null, null, null);
    }

    /**
     * 构造带执行策略与脚本正文的派发负载（重试间隔为空）。
     *
     * @param jobName           任务名（处理器名/脚本任务标识）
     * @param manual            是否手动派发
     * @param failRetry         失败重试次数，null/0 不重试
     * @param executeTimeoutSec 执行超时秒数，null/0 不限
     * @param glueType          执行类型，BEAN/空为 bean 任务
     * @param glueSource        脚本正文，glueType 非 BEAN 时必填
     * @param glueUpdatetimeMs  脚本版本时刻（毫秒），用于执行器缓存失效
     * @return 派发负载
     */
    public static String command(String jobName, boolean manual, Integer failRetry, Integer executeTimeoutSec,
                                 String glueType, String glueSource, Long glueUpdatetimeMs) {
        return command(jobName, manual, failRetry, null, executeTimeoutSec, glueType, glueSource,
                glueUpdatetimeMs, null, null, null, null, null);
    }

    /**
     * 构造带执行策略与脚本正文的派发负载（无回调/告警）。
     *
     * @param jobName           任务名（处理器名/脚本任务标识）
     * @param manual            是否手动派发
     * @param failRetry         失败重试次数，null/0 不重试
     * @param retryIntervalSec  重试间隔秒数，null/0 立即重试
     * @param executeTimeoutSec 执行超时秒数，null/0 不限
     * @param glueType          执行类型，BEAN/空为 bean 任务
     * @param glueSource        脚本正文，glueType 非 BEAN 时必填
     * @param glueUpdatetimeMs  脚本版本时刻（毫秒），用于执行器缓存失效
     * @return 派发负载
     */
    public static String command(String jobName, boolean manual, Integer failRetry, Integer retryIntervalSec,
                                 Integer executeTimeoutSec, String glueType, String glueSource,
                                 Long glueUpdatetimeMs) {
        return command(jobName, manual, failRetry, retryIntervalSec, executeTimeoutSec, glueType, glueSource,
                glueUpdatetimeMs, null, null, null, null, null);
    }

    /**
     * 构造带执行策略、脚本正文与回调/告警的派发负载。
     *
     * <p>负载格式（向后兼容旧版纯任务名/手动前缀）：
     * {@code [MANUAL|]<jobName>[?retry=N&retryInterval=S&timeout=S&glue=<engine>&glueAt=<ms>&src=<base64>
     * &retryCb=<bean>&excCb=<bean>&alarm=<target>&alarmType=<EMAIL|WEBHOOK>&cc=<SKIP|ALLOW>]}
     * 值为 0/空时不写入查询串，因此未配置任何策略的任务负载与旧版完全一致。
     * 脚本正文 Base64 编码以保持单行（行协议按换行切分）。</p>
     *
     * @param jobName             任务名（处理器名/脚本任务标识）
     * @param manual              是否手动派发
     * @param failRetry           失败重试次数，null/0 不重试
     * @param retryIntervalSec    重试间隔秒数，null/0 立即重试
     * @param executeTimeoutSec   执行超时秒数，null/0 不限
     * @param glueType            执行类型，BEAN/空为 bean 任务
     * @param glueSource          脚本正文，glueType 非 BEAN 时必填
     * @param glueUpdatetimeMs    脚本版本时刻（毫秒），用于执行器缓存失效
     * @param retryCallbackBean   重试前回调处理器名，可为空
     * @param exceptionCallbackBean 异常回调处理器名，可为空
     * @param alarmEmail          失败告警目标（邮箱或 webhook 地址），可为空
     * @param alarmType           失败告警类型（EMAIL/WEBHOOK），为空按 EMAIL 处理
     * @param executeParam        台账执行参数（Base64 保持单行），可为空
     * @return 派发负载
     */
    public static String command(String jobName, boolean manual, Integer failRetry, Integer retryIntervalSec,
                                 Integer executeTimeoutSec, String glueType, String glueSource,
                                 Long glueUpdatetimeMs, String retryCallbackBean,
                                 String exceptionCallbackBean, String alarmEmail, String alarmType,
                                 String executeParam) {
        return command(jobName, manual, failRetry, retryIntervalSec, executeTimeoutSec, glueType, glueSource,
                glueUpdatetimeMs, retryCallbackBean, exceptionCallbackBean, alarmEmail, alarmType,
                executeParam, null);
    }

    /**
     * 构造带执行策略、脚本正文、回调/告警与并发策略的派发负载。
     *
     * <p>负载格式（向后兼容旧版纯任务名/手动前缀）：
     * {@code [MANUAL|]<jobName>[?retry=N&retryInterval=S&timeout=S&glue=<engine>&glueAt=<ms>&src=<base64>
     * &retryCb=<bean>&excCb=<bean>&alarm=<target>&alarmType=<EMAIL|WEBHOOK>&cc=<SKIP|ALLOW>]}
     * 值为 0/空时不写入查询串，因此未配置任何策略的任务负载与旧版完全一致。
     * {@code cc} 为空时同样不写入，执行器回落到自身配置的默认并发策略。</p>
     *
     * @param jobName             任务名（处理器名/脚本任务标识）
     * @param manual              是否手动派发
     * @param failRetry           失败重试次数，null/0 不重试
     * @param retryIntervalSec    重试间隔秒数，null/0 立即重试
     * @param executeTimeoutSec   执行超时秒数，null/0 不限
     * @param glueType            执行类型，BEAN/空为 bean 任务
     * @param glueSource          脚本正文，glueType 非 BEAN 时必填
     * @param glueUpdatetimeMs    脚本版本时刻（毫秒），用于执行器缓存失效
     * @param retryCallbackBean   重试前回调处理器名，可为空
     * @param exceptionCallbackBean 异常回调处理器名，可为空
     * @param alarmEmail          失败告警目标（邮箱或 webhook 地址），可为空
     * @param alarmType           失败告警类型（EMAIL/WEBHOOK），为空按 EMAIL 处理
     * @param executeParam        台账执行参数（Base64 保持单行），可为空
     * @param concurrentPolicy    并发策略（SKIP/ALLOW），为空则不写入负载
     * @return 派发负载
     */
    public static String command(String jobName, boolean manual, Integer failRetry, Integer retryIntervalSec,
                                 Integer executeTimeoutSec, String glueType, String glueSource,
                                 Long glueUpdatetimeMs, String retryCallbackBean,
                                 String exceptionCallbackBean, String alarmEmail, String alarmType,
                                 String executeParam, String concurrentPolicy) {
        StringBuilder sb = new StringBuilder();
        if (manual) {
            sb.append(MANUAL_PREFIX);
        }
        sb.append(jobName);
        StringBuilder query = new StringBuilder();
        if (failRetry != null && failRetry > 0) {
            query.append("retry=").append(failRetry);
        }
        if (retryIntervalSec != null && retryIntervalSec > 0) {
            append(query, "retryInterval=" + retryIntervalSec);
        }
        if (executeTimeoutSec != null && executeTimeoutSec > 0) {
            append(query, "timeout=" + executeTimeoutSec);
        }
        boolean script = glueType != null && !glueType.isBlank() && !"BEAN".equalsIgnoreCase(glueType.trim());
        if (script) {
            // 版本-only 命令也带 engine/glueAt（正文由 job-glue 主题单独同步），带 src 时一并写入
            append(query, "glue=" + glueType.trim());
            if (glueUpdatetimeMs != null && glueUpdatetimeMs > 0) {
                append(query, "glueAt=" + glueUpdatetimeMs);
            }
            if (glueSource != null && !glueSource.isBlank()) {
                append(query, "src=" + Base64.getEncoder().encodeToString(
                        glueSource.getBytes(StandardCharsets.UTF_8)));
            }
        }
        if (retryCallbackBean != null && !retryCallbackBean.isBlank()) {
            append(query, "retryCb=" + retryCallbackBean.trim());
        }
        if (exceptionCallbackBean != null && !exceptionCallbackBean.isBlank()) {
            append(query, "excCb=" + exceptionCallbackBean.trim());
        }
        if (alarmEmail != null && !alarmEmail.isBlank()) {
            append(query, "alarm=" + alarmEmail.trim());
            if (alarmType != null && !alarmType.isBlank()) {
                append(query, "alarmType=" + alarmType.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        if (executeParam != null && !executeParam.isBlank()) {
            append(query, "param=" + Base64.getEncoder().encodeToString(
                    executeParam.getBytes(StandardCharsets.UTF_8)));
        }
        if (concurrentPolicy != null && !concurrentPolicy.isBlank()) {
            append(query, "cc=" + concurrentPolicy.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (query.length() > 0) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    /**
     * 追加查询键值（自动补 &）。
     *
     * @param query 查询串缓冲
     * @param pair  键值对
     */
    private static void append(StringBuilder query, String pair) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(pair);
    }

    /**
     * 解析后的派发命令。
     *
     * @param jobName           任务名（处理器名）
     * @param manual            是否手动派发
     * @param failRetry         失败重试次数，null 表示未配置
     * @param retryIntervalSec  重试间隔秒数，null 表示未配置
     * @param executeTimeoutSec 执行超时秒数，null 表示未配置
     * @param glueType          执行类型，null 表示 bean 任务
     * @param glueSource        脚本正文（已从 Base64 解码），null 表示无
     * @param glueUpdatetimeMs  脚本版本时刻（毫秒），null 表示无
     * @param retryCallbackBean 重试前回调处理器名，null 表示无
     * @param exceptionCallbackBean 异常回调处理器名，null 表示无
     * @param alarmEmail        失败告警目标（邮箱或 webhook 地址），null 表示无
     * @param alarmType         失败告警类型（EMAIL/WEBHOOK），null 表示按 EMAIL 处理
     * @param executeParam      台账执行参数（已从 Base64 解码），null 表示无
     * @param concurrentPolicy  并发策略（SKIP/ALLOW），null 表示负载未携带，由执行器用自身默认值
     */
    public record Command(String jobName, boolean manual, Integer failRetry, Integer retryIntervalSec,
                          Integer executeTimeoutSec, String glueType, String glueSource, Long glueUpdatetimeMs,
                          String retryCallbackBean, String exceptionCallbackBean, String alarmEmail,
                          String alarmType, String executeParam, String concurrentPolicy) {

        /**
         * 兼容不携带并发策略的旧版命令（13 参）。
         *
         * @param jobName             任务名
         * @param manual              是否手动派发
         * @param failRetry           失败重试次数
         * @param retryIntervalSec    重试间隔秒数
         * @param executeTimeoutSec   执行超时秒数
         * @param glueType            执行类型
         * @param glueSource          脚本正文
         * @param glueUpdatetimeMs    脚本版本时刻
         * @param retryCallbackBean   重试前回调处理器名
         * @param exceptionCallbackBean 异常回调处理器名
         * @param alarmEmail          失败告警目标
         * @param alarmType           失败告警类型
         * @param executeParam        台账执行参数
         */
        public Command(String jobName, boolean manual, Integer failRetry, Integer retryIntervalSec,
                       Integer executeTimeoutSec, String glueType, String glueSource, Long glueUpdatetimeMs,
                       String retryCallbackBean, String exceptionCallbackBean, String alarmEmail,
                       String alarmType, String executeParam) {
            this(jobName, manual, failRetry, retryIntervalSec, executeTimeoutSec, glueType, glueSource,
                    glueUpdatetimeMs, retryCallbackBean, exceptionCallbackBean, alarmEmail, alarmType,
                    executeParam, null);
        }
    }

    /**
     * 解析派发负载（兼容旧版纯任务名与 MANUAL 前缀格式）。
     *
     * @param payload 负载
     * @return 解析结果
     */
    public static Command parse(String payload) {
        String raw = payload == null ? "" : payload;
        boolean manual = isManualCommand(raw);
        String rest = manual ? raw.substring(MANUAL_PREFIX.length()) : raw;
        String jobName = rest;
        Integer retry = null;
        Integer retryInterval = null;
        Integer timeout = null;
        String glueType = null;
        String glueSource = null;
        Long glueAt = null;
        String retryCb = null;
        String excCb = null;
        String alarm = null;
        String alarmType = null;
        String executeParam = null;
        String concurrentPolicy = null;
        int queryIndex = rest.indexOf('?');
        if (queryIndex >= 0) {
            jobName = rest.substring(0, queryIndex);
            for (String pair : rest.substring(queryIndex + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                try {
                    if ("retry".equals(key)) {
                        retry = Integer.parseInt(value);
                    } else if ("retryInterval".equals(key)) {
                        retryInterval = Integer.parseInt(value);
                    } else if ("timeout".equals(key)) {
                        timeout = Integer.parseInt(value);
                    } else if ("glue".equals(key)) {
                        glueType = value;
                    } else if ("glueAt".equals(key)) {
                        glueAt = Long.parseLong(value);
                    } else if ("src".equals(key)) {
                        glueSource = decodeSource(value);
                    } else if ("retryCb".equals(key)) {
                        retryCb = value;
                    } else if ("excCb".equals(key)) {
                        excCb = value;
                    } else if ("alarm".equals(key)) {
                        alarm = value;
                    } else if ("alarmType".equals(key)) {
                        alarmType = value;
                    } else if ("param".equals(key)) {
                        executeParam = decodeSource(value);
                    } else if ("cc".equals(key)) {
                        concurrentPolicy = value;
                    }
                } catch (NumberFormatException ignored) {
                    // 单个键解析失败不影响任务名与其它键
                }
            }
        }
        return new Command(jobName, manual, retry, retryInterval, timeout, glueType, glueSource, glueAt,
                retryCb, excCb, alarm, alarmType, executeParam, concurrentPolicy);
    }

    /**
     * 解码脚本正文：Base64 失败时回退原文（兼容未编码报文）。
     *
     * @param encoded 编码文本或原文
     * @return 脚本正文
     */
    private static String decodeSource(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return encoded;
        }
    }

    /**
     * 构造执行器注册负载。
     *
     * <p>格式: {@code nodeId=x&role=EXECUTOR&serverUrl=y&handlers=a,b,c}；
     * handlers 为执行器当前已注册的处理器名（逗号分隔，可为空）。</p>
     *
     * @param nodeId    节点标识
     * @param role      角色
     * @param serverUrl 调度中心长链地址
     * @param handlers  处理器名集合
     * @return 注册负载
     */
    public static String register(String nodeId, String role, String serverUrl, java.util.Collection<String> handlers) {
        StringBuilder sb = new StringBuilder();
        sb.append("nodeId=").append(nodeId == null ? "" : nodeId);
        sb.append("&role=").append(role == null ? "" : role);
        sb.append("&serverUrl=").append(serverUrl == null ? "" : serverUrl);
        sb.append("&handlers=").append(joinHandlers(handlers));
        return sb.toString();
    }

    /**
     * 构造处理器清单上报负载。
     *
     * @param handlers 处理器名集合
     * @return 上报负载
     */
    public static String handlers(java.util.Collection<String> handlers) {
        return "handlers=" + joinHandlers(handlers);
    }

    /**
     * 解析注册/清单负载中的处理器名。
     *
     * @param payload 负载
     * @return 处理器名集合（可能为空）
     */
    public static java.util.List<String> parseHandlers(String payload) {
        return parseRegister(payload).handlers();
    }

    /**
     * 注册信息（执行器上线时上报的完整身份）。
     *
     * @param nodeId   节点标识（执行器自述，通常带进程号）
     * @param role     角色
     * @param serverUrl 调度中心长链地址
     * @param handlers 处理器名集合
     */
    public record RegisterInfo(String nodeId, String role, String serverUrl, java.util.List<String> handlers) {

        /**
         * 规范构造器：对处理器名集合做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。处理器名来自负载解析，
         * 已剔除空白项，元素不会为 null，故可直接用 {@link java.util.List#copyOf}。</p>
         *
         * @param nodeId   节点标识（执行器自述，通常带进程号）
         * @param role     角色
         * @param serverUrl 调度中心长链地址
         * @param handlers 处理器名集合
         */
        public RegisterInfo {
            handlers = java.util.List.copyOf(java.util.Objects.requireNonNull(handlers, "handlers 不能为 null"));
        }
    }

    /**
     * 解析注册负载（含 nodeId/role/serverUrl/handlers；旧格式只带 handlers 也能解析）。
     *
     * @param payload 负载
     * @return 注册信息
     */
    public static RegisterInfo parseRegister(String payload) {
        String nodeId = null;
        String role = null;
        String serverUrl = null;
        java.util.List<String> handlers = new java.util.ArrayList<>();
        if (payload != null && !payload.isEmpty()) {
            for (String pair : payload.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1).trim();
                if ("nodeId".equals(key)) {
                    nodeId = value.isEmpty() ? null : value;
                } else if ("role".equals(key)) {
                    role = value.isEmpty() ? null : value;
                } else if ("serverUrl".equals(key)) {
                    serverUrl = value.isEmpty() ? null : value;
                } else if ("handlers".equals(key) && !value.isEmpty()) {
                    for (String name : value.split(",")) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty()) {
                            handlers.add(trimmed);
                        }
                    }
                }
            }
        }
        return new RegisterInfo(nodeId, role, serverUrl, handlers);
    }

    /**
     * 逗号拼接处理器名。
     *
     * @param handlers 处理器名集合
     * @return 拼接文本
     */
    private static String joinHandlers(java.util.Collection<String> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return "";
        }
        return String.join(",", handlers);
    }
}
