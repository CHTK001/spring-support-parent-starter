package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 昆虫深度讲解与测验实体
 *
 * <p>数据来源为开源项目 insect-world 的 guides.zh.ts，与 {@link EduInsect} 按
 * {@code insectCode} 一对一关联。结构化内容以 JSON 文本保存，由服务层解析后下发：
 * {@code guideLesson} 为分步讲解数组，每步可带 {@code anchor}，
 * 用于「读到哪里、镜头对到哪里」；{@code guideQuiz} 为单选题数组。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_insect_guide")
public class EduInsectGuide extends SysBase {

    /**
     * 讲解标识
     */
    @TableId(type = IdType.AUTO)
    private Long guideId;

    /**
     * 物种英文标识（slug），关联 edu_insect.insect_code
     */
    private String insectCode;

    /**
     * 分步讲解（JSON 数组：[{title, body, anchor}]，anchor 可为空）
     */
    private String guideLesson;

    /**
     * 动态演示标题
     */
    private String guideMotionTitle;

    /**
     * 动态演示正文
     */
    private String guideMotionBody;

    /**
     * 单选题（JSON 数组：[{question, options, answer, explain}]）
     */
    private String guideQuiz;

    /**
     * 栖境标题
     */
    private String guideHabitatTitle;

    /**
     * 栖境正文
     */
    private String guideHabitatBody;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("guide_owner_user_id")
    private String ownerUserId;
}
