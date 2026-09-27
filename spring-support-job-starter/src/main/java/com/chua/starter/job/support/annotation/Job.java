package com.chua.starter.job.support.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务入口注解。
 *
 * <p>标注在无参方法上,启动期由
 * {@link com.chua.starter.job.support.handler.JobAnnotationScanner} 扫描
 * 并注册到 {@link com.chua.starter.job.support.handler.JobHandlerFactory}。
 * 调度事件经 {@link com.chua.starter.job.support.execute.JobExecutor}
 * 转换为 {@code execute()} 调用。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class DemoHandler {
 *
 *     @Job("demo")
 *     public void run() {
 *         // 任务逻辑
 *     }
 * }
 * }</pre></pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Job {

    /**
     * 任务处理器唯一名称
     * @return 值的结果
     */
    String value();

    /**
     * 任务描述
     * @return desc的结果
     */
    String desc() default "";

    /**
     * cron 表达式 (例如 {@code "0/3 * * * * ?"}). 三选一; 同时配置时优先级 cron > fixed延迟 > fixedrate。
     * @return cron的结果
     */
    String cron() default "";

    /**
     * 固定间隔触发, 单位毫秒。
     * @return fixed延迟的结果
     */
    long fixedDelay() default 0L;

    /**
     * 固定频率触发, 单位毫秒。
     * @return fixedRate的结果
     */
    long fixedRate() default 0L;

    /**
     * 初始化方法名,与 执行 方法同处一个类, 无参
     * @return 初始化的结果
     */
    String init() default "";

    /**
     * 任务销毁方法名,与 执行 方法同处一个类, 无参
     * @return 销毁的结果
     */
    String destroy() default "";

    /**
     * 调度分组 (对应 {@code sys_job_group})。
     *
     * <p>留空时按 {@code default} 落库,与本属性引入前的行为完全一致。
     * 需要显式指定时,本机的 {@code plugin.job.cluster.group} 必须与之一致:
     * Leader 单秒扫描只捞本分组 ({@code sys_job_group = plugin.job.cluster.group}),
     * 两者不一致会导致任务登记了却永远不被派发。</p>
     *
     * @return 分组名, 留空表示使用 default
     */
    String group() default "";
}
