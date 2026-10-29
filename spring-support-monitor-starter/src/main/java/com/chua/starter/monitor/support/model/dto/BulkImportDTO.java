package com.chua.starter.monitor.support.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * CSV 批量导入（快速模式）请求体。
 *
 * <p>对应前端 {@code BulkImportDialog}：同一份 CSV/Excel 文件可并行灌入多个输出目标
 * （数据源 + 表 + 字段映射）。后端把文件统一转成规范 CSV 后，按目标库方言走
 * LOAD/COPY 直灌（与 datasync 逐行引擎分离）。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Data
@Schema(description = "CSV 批量导入请求")
public class BulkImportDTO {

    /**
     * 任务名（仅展示用途，可空）
     */
    @Schema(description = "任务名（展示用，可空）")
    private String taskName;

    /**
     * 源文件路径（服务器本地绝对路径，csv / excel）
     */
    @Schema(description = "源文件路径（服务器本地绝对路径，csv/excel）")
    private String filePath;

    /**
     * 定界符（CSV 源默认逗号）
     */
    @Schema(description = "定界符（CSV 源默认逗号）")
    private String delimiter;

    /**
     * 文件字符集
     */
    @Schema(description = "文件字符集，默认 UTF-8")
    private String encoding;

    /**
     * 首行是否为表头
     */
    @Schema(description = "首行是否为表头")
    private boolean hasHeader;

    /**
     * 输出目标列表（同一文件可并行灌入多个目标）
     */
    @Schema(description = "输出目标列表")
    private List<BulkImportOutputDTO> outputs;

    /**
     * 单个输出目标
     */
    @Data
    @Schema(description = "批量导入输出目标")
    public static class BulkImportOutputDTO {

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
         * 字段映射（源列 → 目标列）
         */
        @Schema(description = "字段映射（源列 → 目标列）")
        private List<BulkImportMappingDTO> columnMapping;
    }

    /**
     * 字段映射项
     */
    @Data
    @Schema(description = "批量导入字段映射")
    public static class BulkImportMappingDTO {

        /**
         * 源列名（CSV 表头或 Excel 表头）
         */
        @Schema(description = "源列名（文件表头）")
        private String source;

        /**
         * 目标列名
         */
        @Schema(description = "目标列名")
        private String target;
    }
}