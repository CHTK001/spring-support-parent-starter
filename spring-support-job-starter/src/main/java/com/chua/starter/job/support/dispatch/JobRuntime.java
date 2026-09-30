package com.chua.starter.job.support.dispatch;

import lombok.Data;

/**
 * 任务运行态快照 (调度配置 + 触发时刻 + 本节点处理器注册情况)。
 *
 * <p>供"下次执行时间/倒计时"这类展示读数使用: {@code nextFireMs} 由触发器现算,
 * 不依赖重启前落库的旧值; {@code lastFireMs} 取落库的派发时刻, 内存实现退化为进程内记录,
 * 因此只作参考, 业务侧的权威执行历史仍以日志表为准。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobRuntime {

    /**
     * 业务编号 ({@code sys_job_no})
     */
    private String jobNo;

    /**
     * 任务键 ({@code sys_job_execute_bean}), 与处理器名一致
     */
    private String jobKey;

    /**
     * 任务显示名
     */
    private String jobName;

    /**
     * 任务描述
     */
    private String desc;

    /**
     * 分组
     */
    private String group;

    /**
     * 调度类型: CRON / FIXED / DELAY / NONE
     */
    private String scheduleType;

    /**
     * 调度配置: cron 表达式或毫秒间隔
     */
    private String scheduleTime;

    /**
     * 是否启用自动调度
     */
    private boolean enabled;

    /**
     * 执行参数
     */
    private String executeParam;

    /**
     * 分发模式: LOCAL-本节点执行 REMOTE-下发注册节点
     */
    private String dispatchMode;

    /**
     * 远程执行器地址 (仅远程分发有意义)
     */
    private String remoteExecutorAddress;

    /**
     * 负责人
     */
    private String author;

    /**
     * 失败重试次数
     */
    private Integer failRetry;

    /**
     * 重试间隔秒数, 0 为立即重试
     */
    private Integer retryInterval;

    /**
     * 执行超时秒数, 0 为不限
     */
    private Integer executeTimeout;

    /**
     * 错过触发策略
     */
    private String misfireStrategy;

    /**
     * 并发策略: SKIP=上一轮未结束则跳过; ALLOW=允许并发重入
     */
    private String concurrentPolicy;

    /**
     * 乐观锁版本: 编辑保存时回传, 用于检测台账是否已被他人修改
     */
    private Long version;

    /**
     * 执行类型: BEAN=本机处理器; 其它值为脚本引擎名 (如 groovy)
     */
    private String glueType;

    /**
     * 脚本正文, 执行类型为 BEAN 时为空
     */
    private String glueSource;

    /**
     * 脚本版本时刻 (毫秒), 0 表示无
     */
    private long glueUpdatetimeMs;

    /**
     * 重试前回调处理器名
     */
    private String retryCallbackBean;

    /**
     * 异常回调处理器名
     */
    private String exceptionCallbackBean;

    /**
     * 失败告警目标：邮箱（逗号分隔）或 webhook 地址
     */
    private String alarmEmail;

    /**
     * 失败告警类型：EMAIL / WEBHOOK，空按 EMAIL
     */
    private String alarmType;

    /**
     * 最近一次派发时刻 (毫秒时间戳, 0 表示无记录); 落库实现优先取库中值, 跨重启可读
     */
    private long lastFireMs;

    /**
     * 下一次触发时刻 (毫秒时间戳, -1 表示未配置或表达式非法)
     */
    private long nextFireMs;

    /**
     * 本节点是否已注册同名处理器; false 时派发到本节点会被记为"处理器 未注册"
     */
    private boolean handlerRegistered;
}
