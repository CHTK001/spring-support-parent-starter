package com.chua.webrtc.support.dto;

import lombok.Data;

/**
 * 媒体状态变化请求
 *
 * @author CH
 * @since 4.1.0
 */
@Data
public class MediaStateChangeRequest {
    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 音频是否启用
     */
    private Boolean audioEnabled;

    /**
     * 视频是否启用
     */
    private Boolean videoEnabled;

    /**
     * 是否正在屏幕共享
     */
    private Boolean screenSharing;
}