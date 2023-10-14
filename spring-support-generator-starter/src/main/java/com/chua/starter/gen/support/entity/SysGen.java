package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.database.DatabaseOptions;
import com.chua.common.support.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName(value = "sys_gen")
@JsonIgnoreProperties({ "dbcDriverUrl"})
public class SysGen implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "gen_id", type = IdType.AUTO)
    private Integer genId;

    /**
     * 名称
     */
    @TableField(value = "gen_name")
    private String genName;

    /**
     * url
     */
    @TableField(value = "gen_url")
    private String genUrl;

    /**
     * 用户名
     */
    @TableField(value = "gen_user")
    private String genUser;

    /**
     * 密码
     */
    @TableField(value = "gen_password")
    private String genPassword;

    /**
     * 驱动包
     */
    @TableField(value = "gen_driver")
    private String genDriver;

    /**
     * 数据库
     */
    @TableField(value = "gen_database")
    private String genDatabase;

    /**
     * 数据库类型
     */
    @TableField(exist = false)
    private String genType;

    /**
     * 配置ID
     */
    @TableField(value = "dbc_id")
    private Integer dbcId;

    /**
     * 数据库文件目录
     */
    @TableField(value = "gen_database_file")
    private String genDatabaseFile;

    /**
     * 0:未启动
     */
    @TableField(value = "gen_backup_status")
    private Integer genBackupStatus;

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
    /**
     * 创建人
     */
    @TableField(value = "create_by")
    private String createBy;

    private static final long serialVersionUID = 1L;
    /**
     * 选项卡名称
     */
    @TableField(exist = false)
    private String tabName;


    /**
     * 是否有日志
     */
    @TableField(exist = false)
    private String dbcLog;


    /**
     * 是否有日志
     */
    @TableField(exist = false)
    private String dbcName;

    /**
     * 是否有备份
     */
    @TableField(exist = false)
    private Boolean backup;

    /**
     * 驱动文件地址,服务器生成
     */
    @TableField(exist = false)
    private String dbcDriverUrl;
    /**
     * 控制台地址
     */
    @TableField(exist = false)
    private String dbcConsoleUrl;
    /**
     * 制表符备注
     */
    @TableField(exist = false)
    private String tabDesc;

    /**
     * 新数据库配置
     *
     * @return {@link DatabaseOptions}
     */
    public DatabaseOptions newDatabaseOptions() {
        DatabaseOptions databaseOptions = new DatabaseOptions();
        databaseOptions.setDatabase(genDatabase);
        databaseOptions.setDriver(genDriver);
        databaseOptions.setDatabaseFile(genDatabaseFile);
        databaseOptions.setUser(genUser);
        databaseOptions.setDriverPath(dbcDriverUrl);
        databaseOptions.setPassword(genPassword);
        databaseOptions.setGenType(genType);
        if (StringUtils.isNotEmpty(genUrl)) {
            databaseOptions.setUrl(genUrl);
        }
        return databaseOptions;
    }
}