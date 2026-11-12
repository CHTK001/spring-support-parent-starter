package com.chua.starter.server.support.ai;

import java.util.Set;

/**
 * 发布管理的 AI 能力门面。
 *
 * <p>宿主装了 ai-starter 并且注册了可用的对话客户端时，本接口由
 * {@code ReleaseAiConfiguration} 提供实现；没装时容器里没有这个 Bean，
 * 页面据此隐藏 AI 入口，而不是报错或显示一个点了没反应的按钮。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface ReleaseAiCapability {

    /**
     * 是否有可用的对话客户端。
     *
     * @return true 表示可以发起对话
     */
    boolean available();

    /**
     * 已注册的提供者。
     *
     * @return 提供者名集合
     */
    Set<String> providers();

    /**
     * 已注册的模型。
     *
     * @return 模型名集合
     */
    Set<String> models();

    /**
     * 同步对话。
     *
     * @param prompt 提示词
     * @return 模型回复
     */
    String chat(String prompt);
}
