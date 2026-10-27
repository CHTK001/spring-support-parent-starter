package com.chua.starter.job.support.glue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * 代码片段执行上下文（ObjectContext）。
 *
 * <p>对标 XXL-JOB 的 {@code XxlJobHelper}：代码片段（Groovy 脚本 / Java 类片段）本身
 * 不是 Spring Bean，拿不到容器引用。本类把 {@link ApplicationContext} 与本次执行的
 * 任务元信息打包后交给片段，于是片段正文里可以直接取业务 Bean：</p>
 *
 * <pre>{@code
 * // Groovy 脚本：objectContext 是执行器自动绑定的变量
 * def userService = objectContext.getBean('sysUserService')
 *
 * // Java 类片段：入口方法带一个 JobObjectContext 形参即可收到，
 * // 或者字段上写 @Autowired（由 JobScriptHost 经 AutowireCapableBeanFactory 装配）
 * public void execute(JobObjectContext objectContext) {
 *     objectContext.log("开始清理 {}", "缓存");
 * }
 * }</pre>
 *
 * <p>Groovy 侧能拿到本类，是因为 {@code GroovyScriptProvider} 会把 {@code context}
 * 映射的每个条目逐个绑成 Groovy 变量（见 {@code utils-support-groovy-starter}）；
 * Java 侧由 {@code JavaScriptProvider} 按形参类型/名从同一个映射里绑定。
 * 本类因此必须留在 Spring 层——脚本引擎在 utils 层，不能反向依赖 Spring。</p>
 *
 * <p>实例是不可变值对象，每次派发新建一个。</p>
 *
 * @param applicationContext 宿主容器，可能为 null（脱离 Spring 装配时）
 * @param jobKey             任务标识，允许为 null
 * @param jobName            任务显示名，允许为 null
 * @param executeParam       台账执行参数，允许为 null
 * @since 4.0.0.43
 * @author CH
 */
public record JobObjectContext(
        ApplicationContext applicationContext,
        String jobKey,
        String jobName,
        String executeParam) {

    /**
     * 日志门面
     */
    private static final Logger LOG = LoggerFactory.getLogger(JobObjectContext.class);

    /**
     * 构造 脱离容器的 执行上下文（无 bean 可取，用于自检与单测）。
     *
     * @param jobKey       任务标识，允许为 null
     * @param jobName      任务显示名，允许为 null
     * @param executeParam 台账执行参数，允许为 null
     * @return 执行上下文
     */
    public static JobObjectContext detached(String jobKey, String jobName, String executeParam) {
        return new JobObjectContext(null, jobKey, jobName, executeParam);
    }

    /**
     * 按类型取 Spring Bean。
     *
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 容器里的唯一候选
     * @throws IllegalStateException            未装配 Spring 容器
     * @throws NoSuchBeanDefinitionException    容器内无该类型 Bean
     */
    public <T> T getBean(Class<T> type) {
        requireContainer("getBean(Class)");
        return applicationContext.getBean(type);
    }

    /**
     * 按名称与类型取 Spring Bean。
     *
     * @param name Bean 名称
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 命中的 Bean
     * @throws IllegalStateException            未装配 Spring 容器
     * @throws NoSuchBeanDefinitionException    容器内无该 Bean
     */
    public <T> T getBean(String name, Class<T> type) {
        requireContainer("getBean(String, Class)");
        return applicationContext.getBean(name, type);
    }

    /**
     * 按名称取 Spring Bean（不做类型校验，适合拿接口或已标注的实现类）。
     *
     * @param name Bean 名称
     * @return 命中的 Bean
     * @throws IllegalStateException            未装配 Spring 容器
     * @throws NoSuchBeanDefinitionException    容器内无该 Bean
     */
    public Object getBean(String name) {
        requireContainer("getBean(String)");
        return applicationContext.getBean(name);
    }

    /**
     * 取某类型下的全部 Bean。
     *
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return Bean 名称到实例的映射，无候选时返回空映射
     * @throws IllegalStateException 未装配 Spring 容器
     */
    public <T> Map<String, T> getBeansOfType(Class<T> type) {
        requireContainer("getBeansOfType(Class)");
        return applicationContext.getBeansOfType(type);
    }

    /**
     * 是否已接入 Spring 容器。
     *
     * @return 有容器返回 true
     */
    public boolean hasContainer() {
        return applicationContext != null;
    }

    /**
     * 获取宿主容器。
     *
     * @return 宿主容器，未装配时为 null
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 获取任务标识（归一：null → 空串）。
     *
     * @return 任务标识，未设置时为空串
     */
    public String getJobKey() {
        return jobKey == null ? "" : jobKey;
    }

    /**
     * 获取任务显示名（归一：null → 空串）。
     *
     * @return 任务显示名，未设置时为空串
     */
    public String getJobName() {
        return jobName == null ? "" : jobName;
    }

    /**
     * 获取台账执行参数（归一：null → 空串）。
     *
     * @return 执行参数，未设置时为空串
     */
    public String getExecuteParam() {
        return executeParam == null ? "" : executeParam;
    }

    /**
     * 往任务执行日志里打一行（走宿主 slf4j，带统一前缀）。
     *
     * <p>参数用 SLF4J 的 {@code {}} 占位，不要自己拼串。</p>
     *
     * @param format SLF4J 占位串
     * @param args   填充参数
     */
    public void log(String format, Object... args) {
        if (format == null || format.isBlank()) {
            return;
        }
        LOG.info("[job-glue] {} | " + format, prepend(getJobKey()), args);
    }

    /**
     * 容器缺失时给出可读异常，而不是 NPE。
     *
     * @param operation 触发的操作名
     * @throws IllegalStateException 未装配 Spring 容器
     */
    private void requireContainer(String operation) {
        if (applicationContext == null) {
            throw new IllegalStateException("代码片段无法取 Spring Bean：" + operation
                    + " 依赖宿主容器，但本执行器未装配 ApplicationContext");
        }
    }

    /**
     * 拼日志前缀，任务标识为空时只留类型标记。
     *
     * @param key 任务标识
     * @return 日志前缀
     */
    private static String prepend(String key) {
        return key.isEmpty() ? "(无任务标识)" : key;
    }
}
