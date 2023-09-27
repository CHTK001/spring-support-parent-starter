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
@JsonIgnoreProperties("genPassword")
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
     * 驱动包路径
     */
    @TableField(value = "gen_driver_file")
    private String genDriverFile;
    /**
     * 数据路径
     */
    @TableField(value = "gen_database_file")
    private String genDatabaseFile;

    /**
     * 数据库类型
     */
    @TableField(value = "gen_type")
    private String genType;

    /**
     * UId
     */
    @TableField(value = "gen_uid")
    private String genUid;

    /**
     * 数据库
     */
    @TableField(value = "gen_database")
    private String genDatabase;
    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;
    /**
     * 数据库
     */
    @TableField(value = "gen_database_type")
    private String genDatabaseType;
    /**
     * 选项卡名称
     */
    @TableField(exist = false)
    private String tabName;
    /**
     * 制表符备注
     */
    @TableField(exist = false)
    private String tabDesc;

    private static final long serialVersionUID = 1L;

    /**
     * 新数据库配置
     *
     * @return {@link DatabaseConfig}
     */
    public DatabaseConfig newDatabaseConfig() {
        DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setDatabase(genDatabase);
        databaseConfig.setDriverPath(getGenDriverFile());
        databaseConfig.setDriver(genDriver);
        databaseConfig.setDatabaseFile(genDatabaseFile);
        databaseConfig.setUser(genUser);
        databaseConfig.setPassword(genPassword);
        databaseConfig.setUrl(StringUtils.defaultString(genUrl, Dialect.create(this.genType).getUrl(databaseConfig)));
        return databaseConfig;
    }
}