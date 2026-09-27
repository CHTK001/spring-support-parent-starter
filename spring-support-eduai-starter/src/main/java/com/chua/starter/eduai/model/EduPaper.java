package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 试卷实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_paper")
public class EduPaper extends SysBase {

    /**
     * 试卷标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * paperid
    */
    private Long paperId;

    /**
     * 试卷名称
     */
    private String paperName;

    /**
     * 学科
     */
    private String paperSubject;

    /**
     * 学段
     */
    private String paperGradeLevel;

    /**
     * 年级
     */
    private String paperGrade;

    /**
     * 总分
     */
    private Integer paperTotalScore;

    /**
     * 考试时长
     */
    private Integer paperDuration;

    /**
     * 题目总数
     */
    private Integer paperQuestionCount;

    /**
     * 组卷配置JSON
     */
    private String paperConfig;

    /**
     * 状态
     */
    private Integer paperStatus;

    /**
     * 来源
     */
    private String paperSource;

    /**
     * 试卷唯一编号（业务码，对外标识；手动组卷新建时生成，一旦生成不再变更）
     */
    private String paperNo;

    /**
     * 纸张规格: A4/A3/A5/B5/LETTER
     */
    private String paperPageSize;

    /**
     * 纸张方向: PORTRAIT/LANDSCAPE
     */
    private String paperPageOrientation;

    /**
     * 首页模板标识
     */
    private String paperTemplate;

    /**
     * 页头（富文本）
     */
    private String paperHeader;

    /**
     * 页脚（富文本）
     */
    private String paperFooter;

    /**
     * 当前版本号（每次改动 +1，历史留痕与快照摘要以此为准）
     */
    private Integer paperVersion;

    /**
     * 学科/学段是否锁定: 0-否 1-是
     */
    private Integer paperSubjectLocked;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("paper_owner_user_id")
    private String ownerUserId;
}
