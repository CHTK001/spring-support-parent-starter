package com.chua.starter.template.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板提供商实体
 *
 * <p>对应数据库表 {@code template_provider}。一个提供商代表一条模板来源通道
 * （如「阿里云短信」「自建打印服务」），携带调用凭据、接入端点、可承载的模板类型，
 * 以及是否允许在本系统内自定义设计模板。模板通过
 * {@link TemplateInfo#getTemplateInfoProvider()} 归到某个提供商下。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
@TableName("template_provider")
public class TemplateProvider {

    /**
     * 主键 标识
     */
    @TableId(value = "template_provider_id", type = IdType.INPUT)
    private String templateProviderId;

    /**
     * 提供商名称（显示名，如「阿里云短信」）
     */
    @TableField("template_provider_name")
    private String templateProviderName;

    /**
     * 接入方：aliyun / tencent / self
     */
    @TableField("template_provider_vendor")
    private String templateProviderVendor;

    /**
     * Access Key（对外展示前由前端脱敏）
     */
    @TableField("template_provider_ak")
    private String templateProviderAk;

    /**
     * Secret Key（对外展示前由前端脱敏）
     */
    @TableField("template_provider_sk")
    private String templateProviderSk;

    /**
     * 服务端点（域名或自建服务地址）
     */
    @TableField("template_provider_endpoint")
    private String templateProviderEndpoint;

    /**
     * 支持的模板类型：逗号分隔，取值 document / sms / email / label / other
     */
    @TableField("template_provider_types")
    private String templateProviderTypes;

    /**
     * 是否支持自定义模板设计：0 否、1 是
     */
    @TableField("template_provider_custom_design")
    private Boolean templateProviderCustomDesign;

    /**
     * 状态：online 已启用 / offline 已停用
     */
    @TableField("template_provider_status")
    private String templateProviderStatus;

    /**
     * 绑定的来源同步器标识：message / paper / 空（本地提供商，无远端可同步）
     */
    @TableField("template_provider_source")
    private String templateProviderSource;

    /**
     * 最近一次同步时间
     */
    @TableField("template_provider_last_sync_at")
    private LocalDateTime templateProviderLastSyncAt;

    /**
     * 负责人
     */
    @TableField("template_provider_owner")
    private String templateProviderOwner;

    /**
     * 说明
     */
    @TableField("template_provider_remark")
    private String templateProviderRemark;

    /**
     * 创建时间
     */
    @TableField("template_provider_created_at")
    private LocalDateTime templateProviderCreatedAt;

    /**
     * 更新时间
     */
    @TableField("template_provider_updated_at")
    private LocalDateTime templateProviderUpdatedAt;
}
