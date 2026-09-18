package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
* 服务器资产系统配置（键-值）
* @author CH
* @since 4.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务器系统配置")
@TableName("ms_server_config")
public class MsServerConfig extends SysBase {

    private static final long serialVersionUID = 1L; // 串行版本uid

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    @TableField("ms_server_config_key")
    @Schema(description = "配置键", example = "guacd.host")
    private String configKey; // 配置键

    @TableField("ms_server_config_value")
    @Schema(description = "配置值(JSON 字符串)", example = "\"true\"")
    private String configValue; // 配置值

    @TableField("ms_server_config_desc")
    @Schema(description = "配置说明")
    private String configDesc; // 配置desc
}
