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
 * @author CH
 */
@Data
@TableName(value = "sys_gen_nginx_http_config")
public class SysGenNginxHttpConfig implements Serializable {
    @TableId(value = "http_config_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer httpConfigId;

    /**
     * 配置名称
     */
    @TableField(value = "http_config_name")
    @Size(max = 255, message = "配置名称最大长度要小于 255")
    private String httpConfigName;

    /**
     * 配置值
     */
    @TableField(value = "http_config_value")
    @Size(max = 255, message = "配置值最大长度要小于 255")
    private String httpConfigValue;

    /**
     * 状态; 0禁用
     */
    @TableField(value = "http_config_status")
    private Integer httpConfigStatus;

    @TableField(value = "gen_id")
    private Integer genId;

    private static final long serialVersionUID = 1L;
}