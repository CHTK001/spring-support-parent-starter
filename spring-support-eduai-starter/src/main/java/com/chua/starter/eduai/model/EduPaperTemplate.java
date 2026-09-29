package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 组卷模板实体
 *
 * <p>保存手动组卷时使用的大标题风格、页眉页脚（富文本）与纸张规格/方向，
 * 试卷通过 {@code edu_paper.paper_template_id} 引用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_paper_template")
public class EduPaperTemplate extends SysBase {

    /**
     * 模板标识
     */
    @TableId(type = IdType.AUTO)
    private Long templateId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 大标题风格: classic/modern/simple/exam
     */
    private String templateStyle;

    /**
     * 页眉（富文本）
     */
    private String templateHeader;

    /**
     * 页脚（富文本）
     */
    private String templateFooter;

    /**
     * 纸张规格: A4/A3/A5/B5/LETTER
     */
    private String templatePageSize;

    /**
     * 纸张方向: PORTRAIT/LANDSCAPE
     */
    private String templateOrientation;

    /**
     * 状态: 0-停用 1-启用
     */
    private Integer templateStatus;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("template_owner_user_id")
    private String ownerUserId;
}
