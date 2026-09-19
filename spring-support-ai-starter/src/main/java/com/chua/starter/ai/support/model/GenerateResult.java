package com.chua.starter.ai.support.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 生成结果（返回 URL 或 基础64 数据）
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@AllArgsConstructor
public class GenerateResult {
    /** 生成的资源 URL（或 数据:镜像/png;基础64,...） */
    /** URL */
    private String url;
}
