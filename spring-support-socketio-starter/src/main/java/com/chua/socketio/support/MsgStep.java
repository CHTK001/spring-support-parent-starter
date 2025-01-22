package com.chua.socketio.support;

import com.chua.common.support.json.Json;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 消息步骤
 * @author CH
 */
@Data
@AllArgsConstructor
public class MsgStep {

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤消息
     */
    private String msg;

    /**
     * 步骤
     */
    private int step;

    /**
     * 总数
     */
    private int total;

    public MsgStep(String name, int step) {
        this(name, name, step, 100);
    }

    @Override
    public String toString() {
        return Json.toJSONString(this);
    }
}
