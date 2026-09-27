package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.TeacherBindDTO;

import java.util.Map;

/**
 * 教师中心服务接口
 *
 * <p>与 {@link IEduStudentCenterService} 对称：管理端维护教师档案，
 * 这里是教师本人视角 —— 当前账号绑定了谁、我带的班级与学生情况。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduTeacherCenterService {

    /**
     * 当前账号的教师绑定状态
     *
     * @return bound=false 时前端弹绑定表单
     */
    Map<String, Object> me();

    /**
     * 绑定当前登录账号到教师档案（工号不存在则建档）
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    Map<String, Object> bind(TeacherBindDTO dto);

    /**
     * 教师工作台数据：我带的班级 + 每班人数与平均进度 + 学生清单
     *
     * @return 工作台数据；未绑定时 bound=false
     */
    Map<String, Object> overview();
}
