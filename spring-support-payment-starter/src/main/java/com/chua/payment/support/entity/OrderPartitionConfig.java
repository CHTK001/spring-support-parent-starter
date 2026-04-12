package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("payment_partition_config")
public class OrderPartitionConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private String sourceTable;
    private String partitionPrefix;
    private String partitionGranularity;
    private Integer retentionDays;
    private Integer createAheadDays;
    private Integer migrateBeforeDays;
    private Boolean autoCreateEnabled;
    private Boolean autoMigrateEnabled;
    private Boolean keepSourceData;
    private String createTaskKey;
    private String migrateTaskKey;
    private String lastPartitionTable;
    private LocalDateTime lastPartitionAt;
    private LocalDateTime lastMigrateAt;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
