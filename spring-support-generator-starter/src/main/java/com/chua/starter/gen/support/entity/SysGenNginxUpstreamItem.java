package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 *    
 * @author CH
 */     
@Data
@TableName(value = "sys_gen_nginx_upstream_item")
public class SysGenNginxUpstreamItem implements Serializable {
    @TableId(value = "upstream_item_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer upstreamItemId;

    /**
     * 配置名称
     */
    @TableField(value = "upstream_id")
    @Size(max = 255,message = "配置名称最大长度要小于 255")
    private String upstreamId;

    /**
     * IP
     */
    @TableField(value = "upstream_item_ip")
    @Size(max = 255,message = "IP最大长度要小于 255")
    private String upstreamItemIp;

    /**
     * 端口
     */
    @TableField(value = "upstream_item_port")
    private Integer upstreamItemPort;

    /**
     * 权重
     */
    @TableField(value = "upstream_item_weight")
    private Integer upstreamItemWeight;

    /**
     * 最大失败次数
     */
    @TableField(value = "upstream_item_failure")
    private Integer upstreamItemFailure;

    /**
     * 最大连接数
     */
    @TableField(value = "upstream_item_conn")
    private Integer upstreamItemConn;

    /**
     * 策略; down: 停用；backup: 备用;
     */
    @TableField(value = "upstream_item_st")
    @Size(max = 255,message = "策略; down: 停用；backup: 备用;最大长度要小于 255")
    private String upstreamItemSt;

    private static final long serialVersionUID = 1L;
}