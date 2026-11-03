package com.chua.starter.software.support.realtime;

/**
 * 安装进度发布器
 *
 * <p>software 模块只依赖本接口，不直接依赖 Socket / SSE / WebSocket 类型：</p>
 * <ul>
 *   <li>宿主未引入实时通道时，由 {@code SoftwareRealtimeConfiguration} 装配空实现，模块可独立启动；</li>
 *   <li>引入 {@code spring-support-socket-starter} 后，自动改用 {@link SocketInstallProgressPublisher}，
 *       把进度广播到全局 socket 连接。</li>
 * </ul>
 *
 * <p>实现约定：<b>任何异常都必须吞掉</b>。安装过程中推送失败只意味着页面看不到进度，
 * 不能让已经成功的安装因为「告诉前端失败了」而回滚或报错。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface InstallProgressPublisher {

    /**
     * 发布一帧安装进度
     *
     * @param event 进度事件，不允许为 {@code null}
     */
    void publish(InstallProgressEvent event);
}
