package com.chua.socket.support;

import com.chua.common.support.text.json.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息步骤
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsgStep implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 数据ID
     */
    private String dataId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 步骤
     */
    private int step;

    /**
     * 总数
     */
    private int total;

    /**
     * 百分比
     */
    private int percentage;

    /**
     * 状态
     */
    private String status;

    /**
     * 时间戳
     */
    private long timestamp;

    @Override
    public String toString() {
        return Json.toJSONString(this);
    }
}
