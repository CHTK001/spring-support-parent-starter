package com.chua.starter.template.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板信息实体
 * <p>对应数据库表 template_info。</p>
 *
 * <h3>来源</h3>
 * <ul>
 *   <li>{@code hiprint} 绘制模板（设计了器产物）</li>
 *   <li>{@code message} 消息模板（短信/邮件/企微）</li>
 *   <li>{@code paper} 组卷模板（试卷标题/页眉页脚/纸张规格）</li>
 * </ul>
 *
 * <h3>模板语言</h3>
 * <ul>
 *   <li>{@code html} HTML</li>
 *   <li>{@code vue} Vue</li>
 *   <li>{@code jsx} JSX</li>
 *   <li>{@code json} JSON</li>
 * </ul>
 *
 * <h3>模板类型</h3>
 * <ul>
 *   <li>{@code document} 单据</li>
 *   <li>{@code sms} 短信</li>
 *   <li>{@code email} 邮件</li>
 *   <li>{@code label} 标签</li>
 *   <li>{@code other} 其他</li>
 * </ul>
 *
 * <h3>模板状态</h3>
 * <ul>
 *   <li>{@code draft} 草稿</li>
 *   <li>{@code online} 已发布</li>
 *   <li>{@code offline} 未发布</li>
 * </ul>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
@TableName("template_info")
public class TemplateInfo {

    /**
     * 主键 标识
     */
    @TableId(value = "template_info_id", type = IdType.ASSIGN_UUID)
    /**
     * Template信息标识
     */
    private String templateInfoId;

    /**
     * 模板名称
     */
    @TableField("template_info_name")
    /**
     * Template信息名称
     */
    private String templateInfoName;

    /**
     * 模板副标题（说明）
     */
    @TableField("template_info_subtitle")
    /**
     * Template信息副标题
     */
    private String templateInfoSubtitle;

    /**
     * 模板语言：html / vue / jsx / json
     */
    @TableField("template_info_language")
    /**
     * Template信息语言
     */
    private String templateInfoLanguage;

    /**
     * 模板类型：document / sms / email / label / other
     */
    @TableField("template_info_type")
    /**
     * Template信息类型
     */
    private String templateInfoType;

    /**
     * 模板状态：draft / online / offline
     */
    @TableField("template_info_status")
    /**
     * Template信息状态
     */
    private String templateInfoStatus;

    /**
     * 模板 JSON 描述（设计器产物，hiprint PrintTemplate JSON）
     */
    @TableField("template_info_content")
    /**
     * Template信息描述
     */
    private String templateInfoContent;

    /**
     * 备注
     */
    @TableField("template_info_remark")
    private String templateInfoRemark;

    /**
     * 创建者
     */
    @TableField("template_info_creator")
    private String templateInfoCreator;

    /**
     * 最后修改者
     */
    @TableField("template_info_modifier")
    private String templateInfoModifier;

    /**
     * 来源：hiprint 绘制模板 / message 消息模板 / paper 组卷模板
     */
    @TableField("template_info_source")
    /**
     * Template信息来源
     */
    private String templateInfoSource;

    /**
     * 来源行 ID（短信模板为 sys_message_template.id，组卷模板为 edu_paper_template.id）
     */
    @TableField("template_info_source_id")
    /**
     * Template信息来源ID
     */
    private String templateInfoSourceId;

    /**
     * 三方模板 ID（来源侧的模板编码，如短信模板的 sys_message_template_code）
     *
     * <p>固定模板（source=message/paper）才有：内容归三方，本系统按这个 ID 回查模板信息。
     * 自定义模板（hiprint）为空。</p>
     */
    @TableField("template_info_source_code")
    private String templateInfoSourceCode;

    /**
     * 来源提供方（消息提供商 ID / 组卷归属用户 ID）
     */
    @TableField("template_info_provider")
    /**
     * Template信息提供方
     */
    private String templateInfoProvider;

    /**
     * 软删除标记：0 正常、1 删除
     */
    @TableField("template_info_deleted")
    private Integer templateInfoDeleted;

    /**
     * 创建时间
     */
    @TableField("template_info_created_at")
    /**
     * Template信息创建时间AT
     */
    private LocalDateTime templateInfoCreatedAt;

    /**
     * 更新时间
     */
    @TableField("template_info_updated_at")
    /**
     * Template信息更新时间AT
     */
    private LocalDateTime templateInfoUpdatedAt;
}
