package com.chua.starter.ai.support.model;

/**
 * 生成结果（返回 URL 或 基础64 数据）
 *
 * @param url 生成的资源 URL（或 数据:镜像/png;基础64,...）
 *
 * @author CH
 * @since 4.0.0
 */
public record GenerateResult(
        String url
) {
}
