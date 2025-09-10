package com.chua.webrtc.support.dto;

import lombok.Data;

/**
 * 离开房间请求
 *
 * @author CH
 * @since 4.1.0
 */
@Data
public class LeaveRoomRequest {
    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 用户ID
     */
    private String userId;
}