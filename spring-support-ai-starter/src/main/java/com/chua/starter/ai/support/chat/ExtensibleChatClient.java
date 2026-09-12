package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;

import java.util.List;

/**
   * 可扩展 对话客户端 接口，支持链式配置模型、系统提示词与历史记录。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface ExtensibleChatClient extends ChatClient {

    /**
     * 模型。
     * @param model 模型
     * @return 模型的结果
     */
    ChatClient model(String model);

    /**
     * 系统。
     * @param system 系统
     * @return 系统的结果
     */
    ChatClient system(String system);

    /**
     * temperature。
     * @param temperature temperature
     * @return temperature的结果
     */
    ChatClient temperature(Double temperature);

    /**
     * 最大令牌。
     * @param maxTokens 最大令牌
     * @return 最大令牌的结果
     */
    ChatClient maxTokens(Integer maxTokens);

    /**
     * 添加assistant历史。
     * @param history 历史
     * @return 添加assistant历史的结果
     */
    ChatClient addAssistantHistory(String history);

    /**
     * 添加用户历史。
     * @param history 历史
     * @return 添加用户历史的结果
     */
    ChatClient addUserHistory(String history);

    /**
     * 添加镜像。
     * @param imageUrl 镜像url
     * @return 添加镜像的结果
     */
    ChatClient addImage(String imageUrl);

    /**
     * 获取镜像urls。
     * @return 获取镜像urls的结果
     */
    List<String> getImageUrls();

    /**
     * 获取temperature。
     * @return 获取temperature的结果
     */
    Double getTemperature();

    /**
     * 获取最大令牌。
     * @return 获取最大令牌的结果
     */
    Integer getMaxTokens();
}
