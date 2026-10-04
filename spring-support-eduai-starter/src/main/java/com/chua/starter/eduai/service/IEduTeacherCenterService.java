package com.chua.starter.eduai.service;

import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;

import java.util.List;
import java.util.Map;

/**
 * 教师中心服务接口
 *
 * <p>与 {@link IEduStudentCenterService} 对称：管理端维护教师档案，
 * 这里是教师本人视角 —— 当前账号绑定了谁、我带的班级与学生情况，
 * 以及教师端三大能力：<b>管理我自己的班级学生 / 组卷 / 阅卷</b>。</p>
 *
 * <p><b>归属口径</b>：教师端所有数据都以「当前登录账号」为归属，不接受客户端传教师 ID。
 * 班级归属见 {@code edu_class.class_owner_user_id}；学生归属见
 * {@code edu_student.student_owner_user_id}，但教师端在读写学生时以「该学生所在班级是否属于我」
 * 作为授权依据 —— 这样即使某个学生当初由管理员建在别的班，只要后来转进我的班，
 * 我也能管理它，而不需要把归属字段改来改去。</p>
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

    /**
     * 我带的班级列表（归属取自登录态）
     *
     * @return 我创建的班级，按班级 ID 升序；未绑定教师时抛业务异常
     */
    List<EduClass> myClasses();

    /**
     * 新建或修改我自己的班级
     *
     * @param entity 班级实体；带 classId 时按「我的班级」校验归属
     * @return 班级标识
     */
    Long saveClass(EduClass entity);

    /**
     * 删除我自己的班级
     *
     * <p>班级下仍有学生时拒绝删除，避免留下查不到主人的孤儿学生。</p>
     *
     * @param classId 班级标识
     * @return 是否删除成功
     */
    boolean deleteClass(Long classId);

    /**
     * 我某个班的学生清单
     *
     * @param classId 班级标识，必须属于我
     * @return 该班学生列表
     */
    List<EduStudent> classStudents(Long classId);

    /**
     * 在「我的班级」下新增或修改学生。
     *
     * <p>学段 / 年级 / 班级一律随所选班级，禁止客户端自行指定，避免学生与班级不一致。
     * 学号仍由系统生成（复用管理端同一套逻辑），编辑时锁定不变。</p>
     *
     * @param entity 学生实体，studentClassId 必须指向我的班级
     * @return 学生标识
     */
    Long saveStudent(EduStudent entity);

    /**
     * 删除我班里的学生（连同其学习进度 / 每日汇总一起清理）
     *
     * @param studentId 学生标识；其所在班级必须属于我
     * @return 是否删除成功
     */
    boolean deleteStudent(Long studentId);

    /**
     * 重置我班学生的登录口令（无需旧口令，但校验班级归属）
     *
     * @param studentId   学生标识
     * @param newPassword 新口令明文
     * @return 是否成功
     */
    boolean resetStudentPassword(Long studentId, String newPassword);

    /**
     * 教师端字典下拉（学段 + 学科 + 年级），供班级 / 学生 / 组卷表单级联使用。
     *
     * <p>管理端的 {@code /v2/edu/dimension/dropdown} 是管理员专属，教师令牌会 403，
     * 因此教师端需要一条自己的只读通道；数据口径与归属过滤复用同一服务，
     * 不会放宽可见范围。</p>
     *
     * @return 学段 / 学科 / 年级三元组
     */
    EduDimensionService.DimensionDropdown dropdown();

    /**
     * 我发起的阅卷任务（按创建时间倒序）
     *
     * @param limit 最多返回条数；非正数按 20、上限 100
     * @return 阅卷任务列表
     */
    List<EduMarkingTask> myMarkingTasks(int limit);
}
