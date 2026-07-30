package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务器配置项")
@TableName("server_setting")
public class ServerSetting extends SysBase {

    @TableId(value = "server_setting_id", type = IdType.AUTO)
    @ApiModelProperty("配置项ID")
    private Integer serverSettingId;

    @TableField("server_setting_key")
    @ApiModelProperty("配置项键")
    private String settingKey;

    @TableField("server_setting_value")
    @ApiModelProperty("配置项值")
    private String settingValue;
}
