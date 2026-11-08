package com.chua.starter.monitor.support.config;

import com.chua.starter.control.controller.ControlController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 远控模块（{@code spring-support-control-starter}）的 REST 接口装配。
 *
 * <p>该 starter 的自动配置只注册了 {@code WebRtcServer}（信令端口 + SPI 能力），
 * 控制器 {@link ControlController} 在 {@code com.chua.starter.control.controller} 包下，
 * 不在本应用的组件扫描范围（{@code com.chua.starter.monitor.support}）内，
 * 若不在宿主侧显式导入，{@code GET /control/sessions} 会落到静态资源兜底，
 * 返回 500 "No static resource control/sessions" —— 前端 {@code @pages/control}
 * 的会话/屏幕源/配置三块全部拿不到数据。</p>
 *
 * <p>这里只在 {@code control.webrtc.enabled=true}（默认）时导入：
 * 控制器构造依赖 {@code WebRtcServer} Bean，关闭远控时该 Bean 不存在，
 * 无条件导入会让整个应用启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@AutoConfiguration
@ConditionalOnClass(ControlController.class)
@ConditionalOnProperty(prefix = "control.webrtc", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(ControlController.class)
public class ControlRestConfiguration {
}
