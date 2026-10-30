package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表结构版本快照（DDL 快照 + 回滚）。
 *
 * <p>一套完整可回滚的结构快照 = 「拍快照时刻的建表 DDL（已包含索引/外键）+
 * 列信息 + 表选项 + 字符集」，存成 JSON 落入 {@code ms_schema_snapshot}；
 * 回滚时先删（多表倒序）再建，把表恢复到快照诞生的那一刻。</p>
 *
 * @author CH
 * @since 4.0.0.51
 * @see com.chua.starter.datasourcehub.support.dbconsole.DbSchemaSnapshotService
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "表结构版本快照")
@TableName("ms_schema_snapshot")
public class MsSchemaSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "ms_schema_snapshot_id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 数据源 ID（ms_datasource.id）
     */
    @TableField("ms_schema_snapshot_ds_id")
    @Schema(description = "数据源 ID", example = "20")
    private Long datasourceId;

    /**
     * 数据库名：多图数据源（如 Kafka/ClickHouse）的库；空=当前库
     */
    @TableField("ms_schema_snapshot_database")
    @Schema(description = "数据库名", example = "mysql")
    private String database;

    /**
     * 表名
     */
    @TableField("ms_schema_snapshot_table")
    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    /**
     * 快照名称：手动拍时由用户填写（如 v1.0.3），自动拍时后端生成（如「保存于 2026-10-30 15:30」）
     */
    @TableField("ms_schema_snapshot_name")
    @Schema(description = "快照名称", example = "v1.0.3")
    private String name;

    /**
     * 创建人：手动拍时为当前操作人，自动拍时后端记 SYSTEM
     */
    @TableField("ms_schema_snapshot_creator")
    @Schema(description = "创建人", example = "admin")
    private String creator;

    /**
     * 快照创建时间
     */
    @TableField("ms_schema_snapshot_created")
    @Schema(description = "快照创建时间", example = "2026-10-30 15:30:00")
    private LocalDateTime createTime;

    /**
     * 表结构快照（JSON 文本：ddl/indexes/foreignKeys/columns/options）
     */
    @TableField("ms_schema_snapshot_snapshot")
    @Schema(description = "表结构快照（JSON 文本）")
    private String snapshot;

    /**
     * 逻辑删除 0/1
     */
    @TableField("ms_schema_snapshot_deleted")
    @TableLogic
    @Schema(description = "逻辑删除 0/1", example = "0")
    private Integer deleted;
}
