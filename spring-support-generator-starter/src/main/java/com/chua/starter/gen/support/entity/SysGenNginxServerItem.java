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
@TableName(value = "sys_gen_nginx_server_item")
public class SysGenNginxServerItem implements Serializable {
    @TableId(value = "server_item_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer serverItemId;

    /**
     * 反向代理(server)id
     */
    @TableField(value = "server_id")
    private Integer serverId;

    /**
     * 监控路径
     */
    @TableField(value = "server_item_path")
    @Size(max = 255,message = "监控路径最大长度要小于 255")
    private String serverItemPath;

    /**
     * 代理类型; dymaic: 动态http, static: 静态html,upstream: 负载均衡
     */
    @TableField(value = "server_item_type")
    @Size(max = 255,message = "代理类型; dymaic: 动态http, static: 静态html,upstream: 负载均衡最大长度要小于 255")
    private String serverItemType;

    /**
     * 是否开启ws; 0:不开启
     */
    @TableField(value = "server_item_websocket")
    private Integer serverItemWebsocket;

    /**
     * 是否开启跨域; 0:不开启
     */
    @TableField(value = "server_item_cors")
    private Integer serverItemCors;

    /**
     * 是否设置$host;0:不开启
     */
    @TableField(value = "server_item_host")
    private Integer serverItemHost;

    /**
     * 额外参数
     */
    @TableField(value = "server_item_param")
    @Size(max = 255,message = "额外参数最大长度要小于 255")
    private String serverItemParam;

    private static final long serialVersionUID = 1L;
}