package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("服务器模块能力视图")
public class ServerCapabilityView {

    @ApiModelProperty("是否启用AI能力")
    private boolean aiEnabled;

    @ApiModelProperty("是否启用软件能力")
    private boolean softEnabled;

    @ApiModelProperty("是否启用远程代理能力")
    private boolean remoteGatewayEnabled;

    @ApiModelProperty("是否启用文件追尾能力")
    private boolean fileWatchEnabled;

    @ApiModelProperty("是否启用Socket能力")
    private boolean socketEnabled;

    @ApiModelProperty("是否启用服务自动检测")
    private boolean serviceAutoDetectEnabled;

    @ApiModelProperty("AI提供商")
    private String aiProvider;

    @ApiModelProperty("AI默认提供商")
    private String aiDefaultProvider;

    @ApiModelProperty("AI Provider 数量")
    private Integer aiProviderCount;

    @ApiModelProperty("AI Provider 列表")
    private java.util.List<String> aiProviderNames;

    @ApiModelProperty("AI 配置是否就绪")
    private boolean aiConfigReady;

    @ApiModelProperty("ChatClient 是否装配")
    private boolean aiChatClientReady;

    @ApiModelProperty("AI状态文案")
    private String aiStatusText;

    @ApiModelProperty("AI不可用原因")
    private String aiUnavailableReason;

    @ApiModelProperty("AI不可用原因编码")
    private String aiUnavailableCode;

    @ApiModelProperty("AI Provider 解析来源")
    private String aiProviderResolvedFrom;
}
