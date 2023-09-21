package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * @author CH
 */
@Data
@TableName(value = "sys_gen")
public class SysGen implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "gen_id", type = IdType.INPUT)
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

    private static final long serialVersionUID = 1L;
}