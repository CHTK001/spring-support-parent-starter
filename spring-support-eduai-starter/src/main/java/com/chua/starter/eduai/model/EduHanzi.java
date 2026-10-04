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
     * 读音（拼音，可带声调）
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
}
