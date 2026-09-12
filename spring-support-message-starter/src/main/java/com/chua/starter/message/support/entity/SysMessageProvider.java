package com.chua.starter.message.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息提供商实体
 * <p>
   * 对应表 sys_消息_提供者，管理短信、邮件、微信、钉钉等消息服务商配置。
 * </p>
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message_provider")
public class SysMessageProvider extends SysBase {

    /**
      * 提供商标识 - 主键自增
     */
    @TableId(value = "sys_message_provider_id", type = IdType.AUTO)
    /** SYS消息提供者标识 */
    private Integer sysMessageProviderId;

    /**
      * 提供商编码 - 唯一标识，如 阿里云/腾讯云/smtp
     */
    private String sysMessageProviderCode;

    /**
     * 提供商名称 - 显示名称
     */
    private String sysMessageProviderName;

    /**
     * 提供商类型 - EMAIL/SMS/WECHAT/DINGDING/FEISHU等
     */
    private String sysMessageProviderType;

    /**
     * 厂家编码 - 适配的扩展厂家标识
     */
    private String sysMessageProviderVender;

    /**
      * 应用标识 - 用于API认证
     */
    private String sysMessageProviderAppId;

    /**
     * 应用密钥 - 用于API认证
     */
    private String sysMessageProviderAppSecret;

    /**
     * 签名 - 短信/接口签名
     */
    private String sysMessageProviderSign;

    /**
     * 端点地址 - API服务端点URL
     */
    private String sysMessageProviderEndpoint;

    /**
     * API地址 - 接口服务基础地址
     */
    private String sysMessageProviderApiAddress;

    /**
     * SMTP主机 - 邮件服务器主机地址
     */
    private String sysMessageProviderSmtpHost;

    /**
     * SMTP端口 - 邮件服务器端口号
     */
    private Integer sysMessageProviderSmtpPort;

    /**
     * SMTP密码 - 邮件服务器授权码
     */
    private String sysMessageProviderSmtpPassword;

    /**
     * SMTP发件人 - 发件人邮箱地址
     */
    private String sysMessageProviderSmtpFrom;

    /**
     * Webhook地址 - 用于钉钉、飞书等机器人
     */
    private String sysMessageProviderWebhookUrl;

    /**
     * 是否删除 - 0:未删除, 1:已删除
     */
    private Integer sysMessageProviderDelete;

    /**
     * 是否启用 - 0:禁用, 1:启用
     */
    private Integer sysMessageProviderStatus;

    /**
     * 重复消息间隔 - 分钟，间隔内同一接收人相同模板/内容不重复发送，默认1分钟，0表示不限制
     */
    private Integer sysMessageProviderInterval = 1;

    /**
     * 排序号 - 列表显示顺序
     */
    private Integer sysMessageProviderSort;

    /**
     * 描述 - 提供商用途说明
     */
    private String sysMessageProviderRemark;
}
