package com.chua.socket.support;

import com.chua.common.support.json.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsgEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件名称
     */
    private String name;

    /**
     * 消息内容
     */
    private String msg;

    /**
     * 数据
     */
    private Serializable data;

    /**
     * 类型
     */
    private String type;

    /**
     * 模块
     */
    private String module;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 数据ID（用于前端过滤）
     */
    private String dataId;

    /**
     * 步骤
     */
    private int step;

    /**
     * 总数
     */
    private int total;

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
