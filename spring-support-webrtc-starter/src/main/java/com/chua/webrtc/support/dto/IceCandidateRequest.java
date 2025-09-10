package com.chua.webrtc.support.dto;

import lombok.Data;

/**
 * ICE候选请求
 *
 * @author CH
 * @since 4.1.0
 */
@Data
public class IceCandidateRequest {
    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 发送者用户ID
     */
    private String fromUserId;

    /**
     * 接收者用户ID
     */
    private String toUserId;

    /**
     * ICE候选信息
     */
    private Object candidate;
}