package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 生成结果（返回 URL 或 Base64 数据）
 *
 * @author CH
 */
@Data
@AllArgsConstructor
public class GenerateResult {
    /** 生成的资源 URL（或 data:image/png;base64,...） */
    /** URL */
    private String url;
}
