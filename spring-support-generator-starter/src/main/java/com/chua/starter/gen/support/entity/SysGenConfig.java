package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * sys gen配置
 *
 * @author CH
 * @since 2023/09/28
 */
@Data
@TableName(value = "sys_gen_config")
public class SysGenConfig implements Serializable {
    @TableId(value = "dbc_id", type = IdType.AUTO)
    private Integer dbcId;

    /**
     * 是否有日志
     */
    @TableField(value = "dbc_log")
    private String dbcLog;

    /**
     * JDBC,REDIS
     */
    @TableField(value = "dbc_type")
    private String dbcType;

    /**
     * FILE,NONE
     */
    @TableField(value = "dbc_database")
    private String dbcDatabase;

    /**
     * 驱动下载地址
     */
    @TableField(value = "dbc_driver_link")
    private String dbcDriverLink;

    /**
     * 驱动类型,多个逗号分割
     */
    @TableField(value = "dbc_driver")
    private String dbcDriver;

    /**
     * 驱动文件地址,服务器生成
     */
    @TableField(value = "dbc_driver_url")
    private String dbcDriverUrl;

    /**
     * 控制台地址
     */
    @TableField(value = "dbc_console_url")
    private String dbcConsoleUrl;

    /**
     * 数据库名称
     */
    @TableField(value = "dbc_name")
    private String dbcName;

    /**
     * 是否开启, 1: 开启
     */
    @TableField(value = "dbc_status")
    private Short dbcStatus;

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
}