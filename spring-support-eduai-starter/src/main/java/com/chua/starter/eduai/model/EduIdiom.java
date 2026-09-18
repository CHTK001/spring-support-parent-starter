package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成语实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_idiom")
public class EduIdiom {

    /**
    * 成语标识
    */
    @TableId(type = IdType.AUTO)
    private Long idiomId;

    /**
    * 成语内容（四字）
    */
    private String idiomContent;

    /**
    * 拼音
    */
    private String idiomPinyin;

    /**
    * 释义
    */
    private String idiomMeaning;

    /**
    * 出处
    */
    private String idiomSource;

    /**
    * 例句
    */
    private String idiomExample;

    /**
    * 近义词（逗号分隔）
    */
    private String idiomSynonyms;

    /**
    * 反义词（逗号分隔）
    */
    private String idiomAntonyms;

    /**
    * 难度（1-5）
    */
    private Integer idiomDifficulty;

    /**
    * 标签（逗号分隔，如：动物,自然）
    */
    private String idiomTags;

    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 更新时间
    */
    private LocalDateTime updateTime;
}
