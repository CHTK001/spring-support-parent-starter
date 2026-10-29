package com.chua.starter.monitor.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * CSV 批量导入（快速模式）结果。
 *
 * <p>与前端 {@code BulkImportResult} 契约一致：整体状态 + 每个输出目标的行数/耗时/错误。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Data
@Schema(description = "CSV 批量导入结果")
public class BulkImportResultVO {

    /**
     * 整体状态：SUCCESS 全部成功 / FAILED 存在失败
     */
    @Schema(description = "整体状态 SUCCESS/FAILED")
    private String status;

    /**
     * 读取行数（源文件数据行数）
     */
    @Schema(description = "读取行数")
    private long readCount;

    /**
     * 写入行数（各目标成功行数之和）
     */
    @Schema(description = "写入行数（各目标成功行数之和）")
    private long writeCount;

    /**
     * 总耗时（毫秒）
     */
    @Schema(description = "总耗时（毫秒）")
    private long costMs;

    /**
     * 汇总说明或错误
     */
    @Schema(description = "汇总说明或错误")
    private String message;

    /**
     * 每个输出目标的结果
     */
    @Schema(description = "各输出目标结果")
    private List<BulkImportOutputResultVO> outputs;

    /**
     * 单个输出目标的结果
     */
    @Data
    @Schema(description = "单个输出目标结果")
    public static class BulkImportOutputResultVO {

        /**
         * 目标数据源 ID
         */
        @Schema(description = "目标数据源 ID")
        private Long targetDatasourceId;

        /**
         * 目标表名
         */
        @Schema(description = "目标表名")
        private String targetTable;

        /**
         * 状态：SUCCESS / FAILED
         */
        @Schema(description = "状态 SUCCESS/FAILED")
        private String status;

        /**
         * 读取行数
         */
        @Schema(description = "读取行数")
        private long readCount;

        /**
         * 写入行数
         */
        @Schema(description = "写入行数")
        private long writeCount;

        /**
         * 耗时（毫秒）
         */
        @Schema(description = "耗时（毫秒）")
        private long costMs;

        /**
         * 该目标说明或错误
         */
        @Schema(description = "该目标说明或错误")
        private String message;
    }
}