package com.chua.report.client.starter.job.handler;

/**
 * 作业处理程序接口
 * <p>
 * 定义了任务执行的核心方法，所有任务处理器都需要实现此接口。
 * 支持以下几种类型的任务处理器：
 * <ul>
 *     <li>{@link BeanJobHandler} - Spring Bean方式的任务处理器</li>
 *     <li>{@link GlueJobHandler} - Groovy脚本方式的任务处理器</li>
 *     <li>{@link ScriptJobHandler} - Shell/Python等脚本方式的任务处理器</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobHandler {

    /**
     * 执行具体任务的方法
     * <p>
     * 实现类需要在此方法中编写具体的业务逻辑。
     * 任务参数可通过 {@link com.chua.report.client.starter.job.thread.JobContext} 获取。
     * </p>
     *
     * @throws Exception 任务执行过程中可能抛出的异常
     */
    void execute() throws Exception;

    /**
     * 初始化方法
     * <p>
     * 在任务线程启动时调用，用于执行初始化操作。
     * 可用于初始化资源、连接等。
     * </p>
     *
     * @throws Exception 初始化过程中可能抛出的异常
     */
    default void init() throws Exception {
        // 默认空实现
    }

    /**
     * 销毁方法
     * <p>
     * 在任务线程销毁时调用，用于执行清理操作。
     * 可用于释放资源、关闭连接等。
     * </p>
     *
     * @throws Exception 销毁过程中可能抛出的异常
     */
    default void destroy() throws Exception {
        // 默认空实现
    }

}
