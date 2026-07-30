package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OS 配置应用请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsConfigApplyRequest {
    /** 配置项 key */
    private String key;
    /** 要设置的值 */
    private String value;
}
