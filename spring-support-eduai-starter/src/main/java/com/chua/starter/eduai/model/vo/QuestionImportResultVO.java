package com.chua.starter.eduai.model.vo;

import java.util.List;

/**
 * 题目批量导入结果VO
 *
 * <p>逐条容错导入必须把失败原因带回前端：此前只回成功条数，
 * payload 缺 NOT NULL 字段时前端只能显示「导入成功 0 条」，
 * 用户无从得知缺了什么（根因全在日志里，生产不可达）。</p>
 *
 * @param successCount 成功入库条数
 * @param failedCount 失败条数
 * @param errors 逐条失败原因（第N条+原因；仅失败时非空）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record QuestionImportResultVO(
        int successCount,
        int failedCount,
        List<String> errors
) {
}
