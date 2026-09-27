package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.StudentBindDTO;
import com.chua.starter.eduai.model.dto.StudyReportDTO;

import java.util.Map;

/**
 * 学生中心服务接口
 *
 * <p>与管理端（{@code /v2/edu/dimension/students}，管理员维护学生档案）的分工：
 * 这里全部是**学生本人视角**的接口 —— 当前登录账号绑定了谁、我的学习数据是什么。
 * 归属一律取自登录态，不接受客户端传入学生 ID，避免越权看别人的数据。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduStudentCenterService {

    /**
     * 当前账号的绑定状态
     *
     * @return bound=false 时前端必须弹出绑定表单；bound=true 时含 student / className 等信息
     */
    Map<String, Object> me();

    /**
     * 绑定当前登录账号到学生档案（学号不存在则建档）
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    Map<String, Object> bind(StudentBindDTO dto);

    /**
     * 学生端首页数据：今日 / 本周 / 累计统计 + 模块进度 + 分类排名 + 今日推荐
     *
     * @return 首页数据；未绑定时 bound=false
     */
    Map<String, Object> overview();

    /**
     * 上报一次学习行为（累加模块进度与当日汇总）
     *
     * @param dto 本次学习数据
     * @return 是否记录成功
     */
    boolean reportStudy(StudyReportDTO dto);
}
