package com.chua.starter.common.support.notice;

import lombok.Builder;
import lombok.Data;

/**
 * 通知消息
 * @author CH
 */
@Builder
@Data
public class NoticeMessage {
    /**
     * 通知对象唯一的ID
     */
    private String userId;
    /**
     * 通知内容
     */
    private String message;
    /**
     * 类型
     */
    private NoticeType type;

}
