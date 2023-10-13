package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.backup.BackupOption;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * @author CH
 */
@Data
@TableName(value = "sys_gen_backup")
public class SysGenBackup implements Serializable {
    @TableId(value = "backup_id", type = IdType.INPUT)
    @NotNull(message = "不能为null")
    private Integer backupId;

    /**
     * 工具ID
     */
    @TableField(value = "gen_id")
    private Integer genId;

    /**
     * 是否开启; 1:开启; 0:暂停
     */
    @TableField(value = "backup_status")
    private Integer backupStatus;

    /**
     * 保存周期, 天
     */
    @TableField(value = "backup_period")
    private Integer backupPeriod;

    /**
     * 策略
     */
    @TableField(value = "backup_strategy")
    @Size(max = 255, message = "策略最大长度要小于 255")
    private String backupStrategy;

    /**
     * 备份目录
     */
    @TableField(value = "backup_path")
    @Size(max = 255, message = "备份目录最大长度要小于 255")
    private String backupPath;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    private static final long serialVersionUID = 1L;

    /**
     * 新备份选项
     *
     * @return {@link BackupOption}
     */
    public BackupOption newBackupOption() {
        return BackupOption.builder()
                .backupPath(backupPath)
                .backupPeriod(backupPeriod)
                .backupStrategy(backupStrategy)
                .build();
    }
}