package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.mybatis.type.EncryptTypeHandler;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("软件仓库")
@TableName(value = "soft_repository", autoResultMap = true)
public class SoftRepository extends SysBase {

    @TableId(value = "soft_repository_id", type = IdType.AUTO)
    @ApiModelProperty("仓库ID")
    private Integer softRepositoryId;

    @ApiModelProperty("仓库名称")
    private String repositoryName;

    @ApiModelProperty("仓库编码")
    private String repositoryCode;

    @ApiModelProperty("仓库类型")
    private String repositoryType;

    @ApiModelProperty("仓库地址")
    private String repositoryUrl;

    @ApiModelProperty("本地目录")
    private String localDirectory;

    @ApiModelProperty("认证类型")
    private String authType;

    @ApiModelProperty("用户名")
    private String username;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("密码")
    private String password;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("令牌")
    private String token;

    @ApiModelProperty("同步表达式")
    private String syncCron;

    @ApiModelProperty("同步配置")
    private String syncConfig;

    @ApiModelProperty("额外源配置JSON")
    private String sourceConfigsJson;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("最后同步时间")
    private LocalDateTime lastSyncTime;

    @ApiModelProperty("最后同步状态")
    private String lastSyncStatus;

    @ApiModelProperty("最后同步说明")
    private String lastSyncMessage;

    @TableField(exist = false)
    @ApiModelProperty("额外源配置")
    private List<SoftRepositorySource> sourceConfigs;
}
