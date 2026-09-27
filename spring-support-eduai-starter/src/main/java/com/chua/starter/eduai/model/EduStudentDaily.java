package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生每日学习汇总
 *
 * <p>学生端的打卡连续天数、今日学习分钟、本周正确率、星星与学习积分
 * 都由这张表按日期聚合得出；一个学生一天一行，(student_id, stat_date) 唯一。</p>
 *
 * <p>刻意不继承 SysBase：同 {@link EduStudentProgress}，只落自己声明的列。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_student_daily")
public class EduStudentDaily implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 日汇总ID
     */
    @TableId(type = IdType.AUTO)
    private Long dailyId;

    /**
     * 归属用户 ID
     *
     * <p>必须显式指定列名，理由同 EduStudentProgress：
     * 本表用 {@code daily_owner_user_id}，与字段名 ownerUserId 的默认映射不一致。</p>
     */
    @TableField("daily_owner_user_id")
    private String ownerUserId;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 学习次数（每完成一次学习累加）
     */
    private Integer studyCount;

    /**
     * 学习分钟
     */
    private Integer studyMinutes;

    /**
     * 完成练习题数
     */
    private Integer questionCount;

    /**
     * 答对题数
     */
    private Integer correctCount;

    /**
     * 获得星星
     */
    private Integer stars;

    /**
     * 获得学习积分
     */
    private Integer points;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
