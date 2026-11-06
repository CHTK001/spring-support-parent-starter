package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 汉字基础数据实体。
 *
 * <p>「基础数据维护」用：一个字一条，维护它的**读音**（拼音）、**笔顺**（书写顺序）、
 * **字画**（图片路径或字形图）与释义、部首、结构等，供识字/写字类模块消费。</p>
 *
 * <p>唯一键是 {@code hanzi_char}（一个字只能有一条），因此保存时以「按字查重 → 更新」实现幂等。</p>
 *
 * <p><b>读音口径</b>：{@code hanzi_pinyin} 允许存**多读音逗号分隔**
 * （如 {@code xíng,háng}）。多音字只存一个读音是采集源的缺口，不是设计意图；
 * 检索侧对读音做包含匹配，天然命中任一读音，不需要额外的多音列。
 * 无声调检索（敲 {@code zhong} 命中 {@code zhòng}）在查询侧归一化，
 * 见 {@code EduHanziServiceImpl#pageQuery} 与 {@code support/util/Pinyins}。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_hanzi")
public class EduHanzi extends SysBase {

    /**
     * 汉字标识
     */
    @TableId(type = IdType.AUTO)
    private Long hanziId;

    /**
     * 汉字（单个字）
     */
    private String hanziChar;

    /**
     * 读音（拼音，可带声调；多音字用逗号分隔，如 xíng,háng）
     */
    private String hanziPinyin;

    /**
     * 笔画数
     */
    private Integer hanziStrokes;

    /**
     * 部首
     */
    private String hanziRadical;

    /**
     * 结构：独体 / 上下 / 左右 / 半包围 / 全包围
     */
    private String hanziStructure;

    /**
     * 笔顺（按书写顺序排列的笔画名，如 一丨丿丶）
     */
    private String hanziStrokeOrder;

    /**
     * 释义 / 常用组词
     */
    private String hanziMeaning;

    /**
     * 字画（图片路径或字形图，可空）
     */
    private String hanziImage;

    /**
     * 适用学段：PRIMARY / JUNIOR / SENIOR；空表示不限
     */
    private String hanziGradeLevel;

    /**
     * 状态：0 停用 / 1 启用
     */
    private Integer hanziStatus;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("hanzi_owner_user_id")
    private String ownerUserId;

    /**
     * 通假字 / 借字（逗号分隔，如 悦,说）
     *
     * <p>不落 {@code edu_hanzi} 表：通假是「本字 → 多个借字」的一对多关系，
     * 存逗号串没法建索引、也没法反查「悦 是哪个字的通假」，
     * 故单独落 {@code edu_hanzi_tongjia}。本字段是**读写用的传输字段**：
     * 列表查询时由服务端按页批量回填，保存时由服务端拆开同步到关联表。</p>
     */
    @TableField(exist = false)
    private String hanziTongjia;
}
