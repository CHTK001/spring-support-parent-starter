package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 古诗实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_poem")
public class EduPoem extends SysBase {

    /**
     * 古诗标识
     */
    @TableId(type = IdType.AUTO)
    private Long poemId;

    /**
     * 标题
     */
    private String poemTitle;

    /**
     * 作者
     */
    private String poemAuthor;

    /**
     * 朝代（如 唐代/宋代）
     */
    private String poemDynasty;

    /**
     * 正文（整首，句间用换行或标点分隔）
     */
    private String poemContent;

    /**
     * 白话译文
     */
    private String poemTranslation;

    /**
     * 赏析
     */
    private String poemAppreciation;

    /**
     * 注释
     */
    private String poemNotes;

    /**
     * 标签（逗号分隔，如：思乡,写景）
     */
    private String poemTags;

    /**
     * 难度（1-5）
     */
    private Integer poemDifficulty;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("poem_owner_user_id")
    private String ownerUserId;

    /**
     * 适用学段列表（非落库字段，保存时同步 edu_poem_grade）
     */
    @TableField(exist = false)
    private List<String> gradeLevels;

    /**
     * 适用年级列表（非落库字段，保存时同步 edu_poem_grade；为空表示不限定年级）
     */
    @TableField(exist = false)
    private List<String> grades;
}
