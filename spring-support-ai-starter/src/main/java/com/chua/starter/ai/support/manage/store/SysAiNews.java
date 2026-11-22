package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * AIHOT 精选资讯（RSS 同步，按 link 去重）。
 *
 * <p>数据由 {@code SysAiNewsService#syncFromAiHot()} 从
 * {@code https://aihot.news/feed.xml} 同步落库；前端模型管理页右侧的
 * 资讯面板按发布时间倒序展示。同一条资讯（同 link）反复同步只更新，不重复插入。</p>
 *
 * @since 4.0.0.70
 * @author CH
 */
@Data
@TableName("sys_ai_news")
public class SysAiNews implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主键
     */
    @TableId(value = "sys_ai_news_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
     * 资讯标题
     */
    @TableField("sys_ai_news_title")
    private String title; // 标题

    /**
     * AIHOT 站内链接（唯一去重键）
     */
    @TableField("sys_ai_news_link")
    private String link; // 站内链接

    /**
     * 原文链接（描述里解析出的站外地址，空=无原文）
     */
    @TableField("sys_ai_news_origin_url")
    private String originUrl; // 原文链接

    /**
     * 摘要（纯文本，去除 HTML 标签）
     */
    @TableField("sys_ai_news_summary")
    private String summary; // 摘要

    /**
     * 分类（RSS category）
     */
    @TableField("sys_ai_news_category")
    private String category; // 分类

    /**
     * 来源标识（aihot）
     */
    @TableField("sys_ai_news_source")
    private String source; // 来源

    /**
     * 发布时间（毫秒，取 RSS pubDate）
     */
    @TableField("sys_ai_news_published_at")
    private Long publishedAt; // 发布时间

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime; // 创建时间

    /**
     * 更新时间（毫秒）
     */
    @TableField("update_time")
    private Long updateTime; // 更新时间
}
