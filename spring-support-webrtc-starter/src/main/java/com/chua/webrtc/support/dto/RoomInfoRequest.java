package com.chua.webrtc.support.dto;

import lombok.Data;

/**
 * 获取房间信息请求
 *
 * @author CH
 * @since 4.1.0
 */
@Data
public class RoomInfoRequest {
    /**
     * 房间ID
     */
    private String roomId;
}