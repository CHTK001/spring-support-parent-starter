package com.chua.starter.monitor.job.handler;

/**
 * 作业处理程序
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobHandler {

    /**
     * 执行具体任务的函数。
     * 无参数。
     * 无返回值。
     * 可能抛出异常，需要在调用时进行处理。
     */
    void execute() throws Exception;

    /**
     * 初始化函数，可选地进行一些初始化操作。
     * 无参数。
     * 无返回值。
     * 可能抛出异常，需要在调用时进行处理。
     */
    default void init() throws Exception {
        // do something
    }

    /**
     * 销毁处理器，在JobThread销毁时被调用，用于执行一些清理或释放资源的操作。
     * 无参数。
     * 无返回值。
     * 可能抛出异常，需要在调用时进行处理。
     */
    default void destroy() throws Exception {
    }

}
