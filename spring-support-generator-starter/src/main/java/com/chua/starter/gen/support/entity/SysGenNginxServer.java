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
@TableName(value = "sys_gen_nginx_server")
public class SysGenNginxServer implements Serializable {
    @TableId(value = "server_id", type = IdType.INPUT)
    @NotNull(message = "不能为null")
    private Integer serverId;

    /**
     * 转发类型; http/https, tcp, udp
     */
    @TableField(value = "server_type")
    @Size(max = 255,message = "转发类型; http/https, tcp, udp最大长度要小于 255")
    private String serverType;

    /**
     * 监听ip端口	
     */
    @TableField(value = "server_address")
    @Size(max = 255,message = "监听ip端口	最大长度要小于 255")
    private String serverAddress;

    /**
     * 监听域名
     */
    @TableField(value = "server_domain")
    @Size(max = 255,message = "监听域名最大长度要小于 255")
    private String serverDomain;

    /**
     * 是否开启ssl; 0：不开启
     */
    @TableField(value = "server_ssl")
    private Integer serverSsl;

    /**
     * 额外参数
     */
    @TableField(value = "server_param")
    @Size(max = 255,message = "额外参数最大长度要小于 255")
    private String serverParam;

    /**
     * 是否启用; 0:停用
     */
    @TableField(value = "server_status")
    private Integer serverStatus;

    /**
     * 负载均衡方式； tcp使用
     */
    @TableField(value = "upstream_id")
    private Integer upstreamId;


    @TableField(exist = false)
    private List<SysGenNginxServerItem> item;
    private static final long serialVersionUID = 1L;
}