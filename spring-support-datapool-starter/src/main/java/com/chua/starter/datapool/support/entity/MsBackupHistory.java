package com.chua.starter.datapool.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库备份历史实体
 *
 * <p>对应表 {@code ms_backup_history}：每次数据库备份（成功或失败）落一条审计记录，
 * 字段与 utils 层 {@code BackupHistoryRecord} 对齐，供备份中心页面查询展示。
 *
 * @author CH
 * @since 2026/11/24
 */
@Data
@Schema(description = "数据库备份历史")
@TableName("ms_backup_history")
public class MsBackupHistory implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    /**
     * 备份记录ID(批次标识)
     */
    @Schema(description = "备份记录ID(批次标识)")
    private String recordId;

    /**
     * 数据库名
     */
    @Schema(description = "数据库名")
    private String dbName;

    /**
     * 结果: 1 成功 / 0 失败
     */
    @Schema(description = "结果: 1 成功 / 0 失败")
    private Integer success;

    /**
     * 备份产物路径
     */
    @Schema(description = "备份产物路径")
    private String backupPath;

    /**
     * 备份清单路径
     */
    @Schema(description = "备份清单路径")
    private String manifestPath;

    /**
     * 产物文件数
     */
    @Schema(description = "产物文件数")
    private Integer fileCount;

    /**
     * 产物总大小(字节)
     */
    @Schema(description = "产物总大小(字节)")
    private Long totalSizeBytes;

    /**
     * 耗时(毫秒)
     */
    @Schema(description = "耗时(毫秒)")
    private Long durationMillis;

    /**
     * 开始时间(ISO-8601)
     */
    @Schema(description = "开始时间(ISO-8601)")
    private String startTime;

    /**
     * 结束时间(ISO-8601)
     */
    @Schema(description = "结束时间(ISO-8601)")
    private String endTime;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 落库时间
     */
    @Schema(description = "落库时间")
    private LocalDateTime createTime;
}
