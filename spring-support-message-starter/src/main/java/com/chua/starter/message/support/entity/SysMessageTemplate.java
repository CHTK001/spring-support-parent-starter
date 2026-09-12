package com.chua.starter.message.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板实体
 * <p>
   * 对应表 sys_消息_template，管理各渠道消息模板。
 * </p>
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message_template")
public class SysMessageTemplate extends SysBase {

    /**
      * 模板标识 - 主键自增
     */
    @TableId(value = "sys_message_template_id", type = IdType.AUTO)
    /** SYS消息模板标识 */
    private Integer sysMessageTemplateId;

    /**
       * 提供商标识 - 关联sys_消息_提供者表
     */
    private Integer sysMessageProviderId;

    /**
     * 模板编码 - 模板唯一标识
     */
    private String sysMessageTemplateCode;

    /**
     * 模板名称
     */
    private String sysMessageTemplateName;

    /**
     * 模板类型 - EMAIL/SMS/WECHAT等
     */
    private String sysMessageTemplateType;

    /**
     * 子类型 - 短信通知/验证码等
     */
    private Integer sysMessageTemplateCategory;

    /**
     * 模板内容 - 模板内容，变量用${var}表示
     */
    private String sysMessageTemplateContent;

    /**
     * 回调地址 - 邮件状态回调URL
     */
    private String sysMessageTemplateCallbackUrl;

    /**
     * 是否删除 - 0:未删除, 1:已删除
     */
    private Integer sysMessageTemplateDelete;

    /**
     * 是否启用 - 0:禁用, 1:启用
     */
    private Integer sysMessageTemplateStatus;

    /**
     * 模板优先级
     */
    private Integer sysMessageTemplateSort;

    /**
     * 是否默认 - 0:否, 1:是
     */
    private Integer sysMessageTemplateDefault;

    /**
     * 备注说明
     */
    private String sysMessageTemplateRemark;
}
