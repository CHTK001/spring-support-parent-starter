package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 词语库实体。
 *
 * <p>与 {@link EduHanzi} 并列：汉字库是「一个字一条」，词语库是「整词一条」，
 * 维护读音、释义、造句、近义 / 反义 / 同音词，供「词语库」页与趣味模块取材。</p>
 *
 * <p>唯一键是 {@code word_content}（同一个词只有一条），保存时按词查重后更新，
 * 与汉字库同一口径。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_word")
public class EduWord extends SysBase {

    /**
     * 词语标识
     */
    @TableId(type = IdType.AUTO)
    private Long wordId;

    /**
     * 词语内容（整词，如：美丽）
     */
    private String wordContent;

    /**
     * 读音（带声调，如：měi lì）
     */
    private String wordPinyin;

    /**
     * 无声调读音（小写、去分隔，检索用，如：meili）
     *
     * <p>由服务端在保存时派生，客户端不填。拼音检索靠它命中「敲 meili 找 美丽」。</p>
     */
    private String wordPinyinPlain;

    /**
     * 释义
     */
    private String wordMeaning;

    /**
     * 例句 / 造句范例
     */
    private String wordExample;

    /**
     * 近义词 / 相似词语（逗号分隔）
     */
    private String wordSynonyms;

    /**
     * 反义词 / 反向词语（逗号分隔）
     */
    private String wordAntonyms;

    /**
     * 同音词（逗号分隔）
     */
    private String wordHomophones;

    /**
     * 结构 / 词性，如：偏正式、动宾式、名词
     */
    private String wordStructure;

    /**
     * 语料类型：WORD 词语 / XIEHOUYU 歇后语 / PHRASE 短语
     */
    private String wordKind;

    /**
     * 适用学段（中文名）：小学 / 初中 / 高中 / 大学；空表示不限
     */
    private String wordGradeLevel;

    /**
     * 状态：0 停用 / 1 启用
     */
    private Integer wordStatus;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("word_owner_user_id")
    private String ownerUserId;
}
