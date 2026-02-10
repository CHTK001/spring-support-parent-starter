package com.chua.starter.sync.pojo.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 同步表状态信息
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "同步表状态信息")
public class SyncTableStatus {

    /**
     * 是否已初始化
     */
    @Schema(description = "是否已初始化")
    private boolean initialized;

    /**
     * 各表存在状态
     */
    @Schema(description = "各表存在状态")
    private List<TableInfo> tables;

    /**
     * 操作消息
     */
    @Schema(description = "操作消息")
    private String message;

    /**
     * 表信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "表信息")
    public static class TableInfo {
        /**
         * 表名
         */
        @Schema(description = "表名")
        private String tableName;

        /**
         * 是否存在
         */
        @Schema(description = "是否存在")
        private boolean exists;

        /**
         * 表描述
         */
        @Schema(description = "表描述")
        private String description;
    }
}
