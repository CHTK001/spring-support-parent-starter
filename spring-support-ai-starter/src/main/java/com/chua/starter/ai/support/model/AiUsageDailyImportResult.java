package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 用量统计 CSV 导入结果。
 *
 * <p>校验先行：任何一行非法都整文件拒绝入库（importedRows 为 0），
 * 避免日汇总表被写进一半的统计。全部合法时按
 * 「日期 + 分组 + 服务商 + 模型」维度覆盖写入。</p>
 *
 * @param totalRows    CSV 中的数据行总数（不含表头）
 * @param importedRows 实际写入日汇总表的行数
 * @param failedRows   校验失败的行数；大于 0 时整文件拒绝入库
 * @param errors       校验失败明细（行号 + 原因），最多返回前 20 条
 * @since 4.0.0.52
 * @author CH
 */
@Schema(description = "用量统计 CSV 导入结果")
public record AiUsageDailyImportResult(
        @Schema(description = "CSV 数据行总数（不含表头）") int totalRows,
        @Schema(description = "实际写入日汇总表的行数") int importedRows,
        @Schema(description = "校验失败行数；大于 0 时整文件拒绝入库") int failedRows,
        @Schema(description = "校验失败明细（行号 + 原因）") List<String> errors) implements Serializable {

    private static final long serialVersionUID = 1L;
}
