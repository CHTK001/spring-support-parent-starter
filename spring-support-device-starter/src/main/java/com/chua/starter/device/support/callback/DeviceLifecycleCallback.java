package com.chua.starter.device.support.callback;

/**
 * 设备域生命周期回调 SPI。
 *
 * <p>由各业务系统以 Spring Bean 实现（纯后端扩展点，不需要改设备模块代码）。
 * 发布器会遍历容器内全部实现，在虚拟线程里并发调用；实现类自己负责调用
 * 其它服务器、做幂等、以及收到 FAILED 事件时回滚自身已完成的动作。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceLifecycleCallback {

    /**
     * 回调名称（用于日志与手工重放结果识别）。
     *
     * @return 名称，默认取实现类的简单类名
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 是否关心该事件；默认关心全部。
     *
     * @param event 生命周期事件
     * @return true 表示该事件要投递给本实现
     */
    default boolean supports(DeviceLifecycleEvent event) {
        return true;
    }

    /**
     * 处理事件。实现抛出的异常由发布器收敛为失败结果，不会中断其它回调。
     *
     * @param event 生命周期事件
     * @throws Exception 实现方自定义异常
     */
    void onEvent(DeviceLifecycleEvent event) throws Exception;
}
