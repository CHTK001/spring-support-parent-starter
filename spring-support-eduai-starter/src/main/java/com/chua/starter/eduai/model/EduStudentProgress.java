package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生模块学习进度
 *
 * <p>学生端首页每张功能卡片上的「已学 xx%」与进度条都读这里；
 * 一个学生一个模块一行，(student_id, module_code) 唯一。</p>
 *
 * <p>刻意不继承 SysBase：本表只落自己声明的列，
 * 避免 MyBatis-Plus 按实体拼列清单时带上库里没有的 SysBase 列。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_student_progress")
public class EduStudentProgress implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 进度ID
     */
    @TableId(type = IdType.AUTO)
    private Long progressId;

    /**
     * 归属用户 ID
     *
     * <p>必须显式指定列名：本表沿仓库约定用 {@code <表名>_owner_user_id}，
     * 而实体字段叫 ownerUserId，不加注解会被映射成不存在的 owner_user_id。</p>
     */
    @TableField("progress_owner_user_id")
    private String ownerUserId;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 模块编码，如 idiom-game / fun-math
     */
    private String moduleCode;

    /**
     * 已学进度：0-100
     */
    private Integer progress;

    /**
     * 累计学习分钟
     */
    private Integer studyMinutes;

    /**
     * 累计练习题数
     */
    private Integer questionCount;

    /**
     * 累计答对题数
     */
    private Integer correctCount;

    /**
     * 最近学习时间
     */
    private LocalDateTime lastStudyTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
