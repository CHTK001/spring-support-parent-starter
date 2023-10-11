package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 *    
 * @author CH
 */     
@Data
@TableName(value = "sys_gen_nginx_upstream")
public class SysGenNginxUpstream implements Serializable {
    @TableId(value = "upstream_id", type = IdType.INPUT)
    @NotNull(message = "不能为null")
    private Integer upstreamId;

    /**
     * 名称
     */
    @TableField(value = "upstream_name")
    @Size(max = 255,message = "名称最大长度要小于 255")
    private String upstreamName;

    /**
     * 描述
     */
    @TableField(value = "upstream_desc")
    @Size(max = 255,message = "描述最大长度要小于 255")
    private String upstreamDesc;

    /**
     * 转发类型; http/https; tcp/udp
     */
    @TableField(value = "upstrean_type")
    @Size(max = 255,message = "转发类型; http/https; tcp/udp最大长度要小于 255")
    private String upstreanType;

    /**
     * 策略; least_conn
     */
    @TableField(value = "upstream_strategy")
    @Size(max = 255,message = "策略; least_conn最大长度要小于 255")
    private String upstreamStrategy;
    @TableField(exist = false)
    private List<SysGenNginxUpstreamItem> item;
    private static final long serialVersionUID = 1L;
}