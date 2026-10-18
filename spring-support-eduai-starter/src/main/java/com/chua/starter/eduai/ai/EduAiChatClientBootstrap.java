package com.chua.starter.eduai.ai;

import com.chua.starter.ai.support.manage.updater.ChatClientHotUpdater;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时按 AI 控制台配置重建默认对话客户端。
 *
 * <p><b>为什么需要这一步。</b>{@code ChatClientHotUpdater} 只在「默认模型」设置变更
 * 事件（{@code SettingChangedEvent(group=models)}）里重建客户端并
 * {@code ChatClientRegistry.setDefault(...)}；<b>启动链路里没有任何地方补做这件事</b>。
 * 于是 UI 里配好的默认模型只在「保存那一刻」有效，后端一重启就退回
 * {@code AiConfiguration} 启动时登记的聚合客户端 —— 表现为「配置没丢、但就是不生效」，
 * 且没有任何报错可查。</p>
 *
 * <p>这里在容器就绪后补一次刷新（{@link Ordered#LOWEST_PRECEDENCE}，确保晚于
 * {@code AiConfiguration#onApplicationReady} 的注册中心初始化），让 UI 配置在重启后
 * 依然生效。未配置任何默认模型时，刷新内部只打印 WARN 并跳过，不影响启动。</p>
 *
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class EduAiChatClientBootstrap {

    /**
     * 默认模型热更新器（ai-starter 提供，按「厂商表 + Key」构建真实客户端）。
     */
    @Autowired(required = false)
    private ChatClientHotUpdater chatClientHotUpdater;

    /**
     * 容器就绪后，把 AI 控制台里配置的默认模型重新装配成可用客户端。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void refreshConfiguredChatClients(ApplicationReadyEvent event) {
        if (chatClientHotUpdater == null) {
            return;
        }
        try {
            chatClientHotUpdater.refreshAllDefaultChatClients();
            log.info("[EduAi] 已按 AI 控制台「默认模型」配置重建默认对话客户端");
        } catch (Exception e) {
            log.warn("[EduAi] 按 AI 控制台「默认模型」配置重建默认对话客户端失败: {}", e.getMessage());
        }
    }
}
