package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.backup.BackupOptions;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName(value = "sys_gen_backup")
public class SysGenBackup implements Serializable {
    @TableId(value = "backup_id", type = IdType.AUTO)
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
     * 过滤
     */
    @TableField(value = "backup_filter")
    private String backupFilter;

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
     * 动作多个,分隔; CREATE, UPDATE,DELETE
     */
    @TableField(value = "backup_action")
    @Size(max = 255, message = "动作多个,分隔; CREATE, UPDATE,DELETE最大长度要小于 255")
    private String backupAction;

    /**
     * 忽略
     */
    @TableField(value = "backup_ignore")
    @Size(max = 255, message = "忽略最大长度要小于 255")
    private String backupIgnore;

    /**
     * 备份目录
     */
    @TableField(value = "backup_path")
    @Size(max = 255, message = "备份目录最大长度要小于 255")
    private String backupPath;

    /**
     * 备份驱动名称
     */
    @TableField(value = "backup_driver")
    @Size(max = 255, message = "备份驱动名称")
    private String backupDriver;

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
     * @return {@link BackupOptions}
     */
    public BackupOptions newBackupOption() {
        return BackupOptions.builder()
                .backupPath(backupPath)
                .backupIgnore(backupIgnore)
                .backupFilter(backupFilter)
                .backupDriver(backupDriver)
                .backupPeriod(backupPeriod)
                .backupStrategy(backupStrategy)
                .build();
    }
}