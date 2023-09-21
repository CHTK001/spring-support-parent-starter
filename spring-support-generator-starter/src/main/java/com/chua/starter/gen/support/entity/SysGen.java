package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName(value = "sys_gen")
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

    private static final long serialVersionUID = 1L;
}