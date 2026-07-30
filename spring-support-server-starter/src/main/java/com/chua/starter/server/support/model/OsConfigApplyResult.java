package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OS 配置应用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsConfigApplyResult {
    /** 配置项 key */
    private String key;
    /** 配置项名称 */
    private String name;
    /** 是否成功 */
    private boolean success;
    /** 消息 */
    private String message;
    /** 应用后的新值 */
    private String newValue;
}
