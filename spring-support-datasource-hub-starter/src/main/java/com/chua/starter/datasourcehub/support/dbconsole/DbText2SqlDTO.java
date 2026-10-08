package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AI 文本生成 SQL 请求。
 *
 * <p>把用户的自然语言查询需求交给已集成的 AI 对话能力（ai-starter）生成 SQL；
 * 生成结果只做围栏剥离，不代替用户执行 —— 执行与否始终由用户在控制台显式点击。</p>
 *
 * @param text         查询需求（自然语言，必填）
 * @param context      数据库结构上下文（可选，如前端已加载的表/列清单），
 *                     非空时注入系统提示词，显著提升生成准确率
 * @param datasourceId 目标数据源 ID（可选），供前端联动当前选中的库
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbText2SqlDTO(
        String text,
        String context,
        Long datasourceId
) {
}
