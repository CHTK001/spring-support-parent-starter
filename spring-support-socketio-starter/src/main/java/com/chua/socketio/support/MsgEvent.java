package com.chua.socketio.support;

import com.chua.common.support.json.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 消息步骤
 *
 * @author CH
 */
@Data
@AllArgsConstructor
@Builder
public class MsgEvent {

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤消息
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
     * 步骤
     */
    private int step;

    /**
     * 总数
     */
    private int total;

    /**
     * 时间戳
     */
    private long timestamp;


    @Override
    public String toString() {
        return Json.toJSONString(this);
    }
}
