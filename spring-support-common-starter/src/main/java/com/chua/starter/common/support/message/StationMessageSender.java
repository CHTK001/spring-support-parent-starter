package com.chua.starter.common.support.message;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;

/**
 * 平台站内消息投递契约。实现方负责消息持久化与平台消息中心行为。
 */
public interface StationMessageSender {

    /**
     * 将消息投递给指定用户。
     *
     * @param message 消息内容
     * @param targetUserIds 接收用户 ID
     * @return 实际投递条数及消息中心结果
     */
    ReturnResult<Integer> broadcast(StationMessage message, List<Integer> targetUserIds);
}
