package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 试卷题目关联实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_paper_question")
public class EduPaperQuestion extends SysBase {

    /**
     * 关联标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * paperquestionid
    */
    private Long paperQuestionId;

    /**
     * 试卷标识
     */
    private Long paperQuestionPaperId;

    /**
     * 题目标识
     */
    private Long paperQuestionQuestionId;

    /**
     * 题目顺序
     */
    private Integer paperQuestionOrder;

    /**
     * 题目分值
     */
    private Integer paperQuestionScore;

    /**
     * 试卷题唯一编号（二维码内容，AI 凭此定位题目；一旦生成不再变更）
     */
    private String paperQuestionNo;

    /**
     * 所属试卷版本号（随试卷每次改动同步为最新版本）
     */
    private Integer paperQuestionVersion;

    /**
     * 题组/大题标识（可选）
     */
    private String paperQuestionSection;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("paper_question_owner_user_id")
    private String ownerUserId;
}
