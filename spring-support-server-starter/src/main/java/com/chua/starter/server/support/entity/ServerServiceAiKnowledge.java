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
@ApiModel("服务器服务AI知识")
@TableName("server_service_ai_knowledge")
public class ServerServiceAiKnowledge extends SysBase {

    @TableId(value = "server_service_ai_knowledge_id", type = IdType.AUTO)
    @ApiModelProperty("服务器服务AI知识ID")
    private Integer serverServiceAiKnowledgeId;

    @TableField("server_knowledge_key")
    @ApiModelProperty("知识键")
    private String knowledgeKey;

    @TableField("server_service_name")
    @ApiModelProperty("服务名称")
    private String serviceName;

    @TableField("server_service_type")
    @ApiModelProperty("服务类型")
    private String serviceType;

    @TableField("server_type")
    @ApiModelProperty("接入类型")
    private String serverType;

    @TableField("server_os_type")
    @ApiModelProperty("操作系统")
    private String osType;

    @TableField("server_ai_reason")
    @ApiModelProperty("失败原因")
    private String reason;

    @TableField("server_ai_solution")
    @ApiModelProperty("处理方案")
    private String solution;

    @TableField("server_ai_fix_script")
    @ApiModelProperty("修复脚本")
    private String fixScript;

    @TableField("server_ai_provider")
    @ApiModelProperty("AI提供商")
    private String provider;

    @TableField("server_ai_model")
    @ApiModelProperty("AI模型")
    private String model;

    @TableField("server_sample_output")
    @ApiModelProperty("样例输出")
    private String sampleOutput;

    @TableField("server_metadata_json")
    @ApiModelProperty("扩展元数据")
    private String metadataJson;
}
