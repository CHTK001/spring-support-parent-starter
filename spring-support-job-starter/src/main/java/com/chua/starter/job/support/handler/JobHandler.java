package com.chua.starter.job.support.handler;

/**
* 任务执行入口接口。
*
* <p>由 {@link JobHandlerFactory} 单例注册表统一维护;
* 调度事件消费侧通过 {@link #execute()} 触发实际逻辑。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface JobHandler {

    /**
    * 处理器唯一名称
    *
    * @return 名称
    */
    String name();

    /**
    * 任务描述
    *
    * @return 描述, 默认空字符串
    */
    default String desc() {
        return "";
    }

    /**
    * 执行任务
    *
    * @throws Exception 执行失败时抛出
    */
    void execute() throws Exception;

    /**
    * 初始化钩子, 首次执行前调用一次
    *
    * @throws Exception 初始化失败时抛出
    */
    default void init() throws Exception {
    }

    /**
    * 销毁钩子, 注销时调用一次
    *
    * @throws Exception 销毁失败时抛出
    */
    default void destroy() throws Exception {
    }
}
