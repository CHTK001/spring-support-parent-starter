package com.chua.starter.job.support.handler;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 任务处理器注册表 (单例)。
 *
 * <p>Spring 容器启动后由 {@link JobAnnotationScanner} 注册 {@link BeanJobHandler};
 * 运行时调度事件消费侧通过 {@link #get(String)} 查找处理器。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JobHandlerFactory {

    /**
     * 注册表实例
     */
    private static final JobHandlerFactory INSTANCE = new JobHandlerFactory();

    /**
      * 处理器存储, 钥匙 为处理器名称
     */
    private final ConcurrentMap<String, JobHandler> handlers = new ConcurrentHashMap<>(32);

    /** 创建 作业处理器工厂 实例 */
    private JobHandlerFactory() {
    }

    /**
     * 获取单例实例。
     *
     * @return 工厂实例
     */
    public static JobHandlerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 注册处理器。 同名处理器已存在时覆盖并返回旧值。
     *
     * @param handler 处理器实例
     * @return 被覆盖的旧处理器, 允许为 空
     */
    public JobHandler register(JobHandler handler) {
        if (handler == null) {
            /**
             * illegal参数异常。
             * @param 不能为空" 不能为空"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("handler 不能为空");
        }
        return handlers.put(handler.name(), handler);
    }

    /**
     * 按名称注销处理器。
     *
     * @param name 处理器名称
     * @return 被注销的处理器, 允许为 空
     */
    public JobHandler deregister(String name) {
        return handlers.remove(name);
    }

    /**
     * 按名称获取处理器。
     *
     * @param name 处理器名称
     * @return 处理器实例, 不存在时为 空
     */
    public JobHandler get(String name) {
        return handlers.get(name);
    }

    /**
     * 是否已注册。
     *
     * @param name 处理器名称
     * @return 已注册返回 true
     */
    public boolean contains(String name) {
        return handlers.containsKey(name);
    }

    /**
     * 所有处理器名称。
     *
     * @return 名称集合
     */
    public Set<String> names() {
        return Set.copyOf(handlers.keySet());
    }

    /**
     * 所有处理器实例 (只读快照)。
     *
     * @return 处理器集合
     */
    public Collection<JobHandler> all() {
        return java.util.Collections.unmodifiableCollection(handlers.values());
    }

    /**
     * 已注册数量。
     *
     * @return 数量
     */
    public int size() {
        return handlers.size();
    }

    /**
     * 清空注册表, 测试场景使用。
     */
    public void clear() {
        handlers.clear();
    }
}
