package com.chua.webrtc.support.dto;

import lombok.Data;

/**
 * 加入房间请求
 *
 * @author CH
 * @since 4.1.0
 */
@Data
public class JoinRoomRequest {
    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;
}