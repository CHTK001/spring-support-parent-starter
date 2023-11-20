package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */

/**
 * 执行器子项
 */
@Data
@TableName(value = "unified_executer_item")
public class UnifiedExecuterItem implements Serializable {
    @TableId(value = "unified_executer_item_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedExecuterItemId;

    /**
     * 执行器ID
     */
    @TableField(value = "unified_executer_id")
    private Integer unifiedExecuterId;

    /**
     * 地址
     */
    @TableField(value = "unified_executer_item_host")
    @Size(max = 255, message = "地址最大长度要小于 255")
    private String unifiedExecuterItemHost;

    /**
     * 端口
     */
    @TableField(value = "unified_executer_item_port")
    @Size(max = 255, message = "端口最大长度要小于 255")
    private String unifiedExecuterItemPort;

    /**
     * 环境
     */
    @TableField(value = "unified_executer_item_profile")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String unifiedExecuterItemProfile;

    /**
     * 协议
     */
    @TableField(value = "unified_executer_item_protocol")
    @Size(max = 255, message = "协议最大长度要小于 255")
    private String unifiedExecuterItemProtocol;

    /**
     * 订阅数据
     */
    @TableField(value = "unified_executer_item_subscribe")
    @Size(max = 255, message = "订阅数据最大长度要小于 255")
    private String unifiedExecuterItemSubscribe;

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