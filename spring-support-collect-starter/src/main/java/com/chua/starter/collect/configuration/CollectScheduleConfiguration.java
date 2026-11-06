package com.chua.starter.collect.configuration;

import com.chua.starter.collect.job.JobFlowSchedulePort;
import com.chua.starter.collect.schedule.CollectFlowSchedulePort;
import com.chua.starter.collect.schedule.LocalOnlyFlowSchedulePort;
import com.chua.starter.collect.service.CollectExecutionService;
import com.chua.starter.job.support.operate.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 采集排期端口的装配。
 *
 * <p>采集本体不依赖 job-starter：排期能力收口在 {@link CollectFlowSchedulePort}，
 * 本配置按容器里"有没有 job 台账"选实现：</p>
 *
 * <ul>
 * <li>有 {@code JobOperator}（引入了 job-starter 且未关闭）→ 台账实现，流程排期落到 sys_job，
 * 行为与以前一致；</li>
 * <li>没有 → {@link LocalOnlyFlowSchedulePort} 兜底：流程照常设计/手动执行/看结果，
 * 只是没有定时触发，启用排期时由上层给出明确提示。</li>
 * </ul>
 *
 * <p><b>为什么用一个 Bean 在运行期判断，而不是 {@code @ConditionalOnBean} + 两个配置类：</b></p>
 *
 * <p>踩过两个坑：① {@code @ConditionalOnBean} 依赖装配顺序，而 {@code JobOperator} 是
 * job-starter 自己的自动装配创建的，顺序不保证在本配置之前，实测会误判成"没有 job"
 * 从而静默降级；② 把 job 类型写进 {@code @Bean} 方法签名或条件注解，会让 Spring 反射内省
 * 参数类型时触发类加载，类路径上真没有 job-starter 时会 {@code NoClassDefFoundError}。</p>
 *
 * <p>所以这里方法签名只用中立类型，job 类型通过 {@link #findJobOperator(ApplicationContext)}
 * 用类名在运行期查（容器启动完毕、所有 Bean 定义都已注册），找不到就兜底；
 * 方法体里对 job 类型的引用只在命中分支执行，JVM 惰性解析，天然安全。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
public class CollectScheduleConfiguration {

    /**
     * job 台账入口的全限定名。
     */
    private static final String JOB_OPERATOR_CLASS = "com.chua.starter.job.support.operate.JobOperator";

    /**
     * 排期端口：有 job 台账用台账实现，没有则本地兜底（定时不可用）。
     *
     * @param context                  容器，用于运行期按类名找 job 台账
     * @param executionServiceProvider 执行服务（延迟取，避免启动期循环依赖）
     * @return 排期端口
     */
    @Bean
    public CollectFlowSchedulePort collectFlowSchedulePort(
            ApplicationContext context,
            ObjectProvider<CollectExecutionService> executionServiceProvider) {
        Object jobOperator = findJobOperator(context);
        if (jobOperator == null) {
            return new LocalOnlyFlowSchedulePort();
        }
        return newJobFlowSchedulePort(jobOperator, executionServiceProvider);
    }

    /**
     * 把运行期查到的 job 台账包成排期实现。
     *
     * <p>单独成方法是为了让 job 类型的引用只在"确实有 job"这条路径上被解析。</p>
     *
     * @param jobOperator              job 台账入口（运行期按类名取到，非空）
     * @param executionServiceProvider 执行服务提供者
     * @return 基于 job 台账的排期端口
     */
    private CollectFlowSchedulePort newJobFlowSchedulePort(
            Object jobOperator,
            ObjectProvider<CollectExecutionService> executionServiceProvider) {
        return new JobFlowSchedulePort((JobOperator) jobOperator, executionServiceProvider);
    }

    /**
     * 运行期查找 job 台账 Bean；类路径上没有 job-starter 时返回 null。
     *
     * @param context 容器
     * @return JobOperator 实例，或 null
     */
    private Object findJobOperator(ApplicationContext context) {
        try {
            Class<?> type = Class.forName(JOB_OPERATOR_CLASS);
            return context.getBeanProvider(type).getIfAvailable();
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}
