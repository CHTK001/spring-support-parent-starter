package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.database.DatabaseConfig;
import com.chua.common.support.database.sqldialect.Dialect;
import com.chua.common.support.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName(value = "sys_gen")
@JsonIgnoreProperties({"genPassowrd", "dbcDriverUrl"})
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
    @TableField(value = "gen_type")
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
     * @return {@link DatabaseConfig}
     */
    public DatabaseConfig newDatabaseConfig() {
        DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setDatabase(genDatabase);
        databaseConfig.setDriver(genDriver);
        databaseConfig.setDatabaseFile(genDatabaseFile);
        databaseConfig.setUser(genUser);
        databaseConfig.setDriverPath(dbcDriverUrl);
        databaseConfig.setPassword(genPassword);
        if (StringUtils.isNotEmpty(genUrl)) {
            databaseConfig.setUrl(genUrl);
        }
        return databaseConfig;
    }
}