package com.chua.starter.device.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * webhook 推送入库结果。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class WebhookPushResult {

    /** 主题：access / alarm */
    private String topic;

    /** 收到的条目数 */
    private int received;

    /** 新落库条数 */
    private int created;

    /** 幂等跳过条数 */
    private int skipped;

    /** 单条失败原因（不阻断整批） */
    private List<String> errors = new ArrayList<>();
}
