package com.chua.socket.support;

import com.chua.common.support.lang.json.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
* 消息事件
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsgEvent implements Serializable {

    /** 串行版本UID */
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
    * 事件标识
    */
    private String eventId;

    /**
    * 数据标识（用于前端过滤）
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
    /** 转为字符串 */
    public String toString() {
        return Json.toJSONString(this);
    }
}
