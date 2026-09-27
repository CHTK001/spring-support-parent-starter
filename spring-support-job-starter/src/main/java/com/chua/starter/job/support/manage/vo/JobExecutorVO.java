package com.chua.starter.job.support.manage.vo;

import lombok.Data;

/**
 * 已注册执行器清单项。
 *
 * <p>取自进程内的处理器注册表, 是「这个任务键能不能真跑起来」的唯一事实来源: 台账里挂了行但
 * 注册表没有同名处理器时, 那一拍会被记成 {@code SKIPPED · handler 未注册}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobExecutorVO {

    /**
     * 来源: 注解扫描 ({@code @Job})
     */
    public static final String SOURCE_ANNOTATION = "ANNOTATION";

    /**
     * 来源: 三方模块运行期编程式注册
     */
    public static final String SOURCE_INJECTED = "INJECTED";

    /**
     * 来源: 在线执行器上报（调度中心节点本地没有该处理器，任务由执行器节点执行）
     */
    public static final String SOURCE_REMOTE = "REMOTE";

    /**
     * 执行器名, 即 {@code sys_job_execute_bean} 要填的值
     */
    private String name;

    /**
     * 执行器描述
     */
    private String desc;

    /**
     * 来源: {@link #SOURCE_ANNOTATION} / {@link #SOURCE_INJECTED} / {@link #SOURCE_REMOTE}
     */
    private String source;

    /**
     * 处理器实现类全名, 用于定位注入方所在模块
     */
    private String handlerClass;
}
