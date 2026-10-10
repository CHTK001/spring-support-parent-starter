package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.mapper.EduMarkingTaskMapper;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;
import com.chua.starter.eduai.service.IEduTeacherCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师中心服务实现
 *
 * <p>教师端所有写操作都先做两层授权：
 * ① 账号必须已绑定教师工号（{@link #requireTeacher()}）；
 * ② 目标班级的 {@code class_owner_user_id} 必须等于当前账号（{@link #requireMyClass(Long)}）。
 * 二者缺一即抛业务异常，因此教师无法通过改 URL 里的 ID 去动别人的班或学生。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduTeacherCenterServiceImpl implements IEduTeacherCenterService {

    /**
     * 阅卷任务默认返回条数
     */
    private static final int DEFAULT_TASK_LIMIT = 20;

    /**
     * 阅卷任务最大返回条数
     */
    private static final int MAX_TASK_LIMIT = 100;

    private final EduTeacherMapper teacherMapper;

    private final EduClassMapper classMapper;

    private final EduStudentMapper studentMapper;

    /**
     * 教师端复用管理端的学生 / 班级写逻辑：学号生成、口令加盐、手机号校验、级联清理
     * 都在 {@link EduDimensionService} 里，重复实现会与用户体系分叉。
     */
    private final EduDimensionService dimensionService;

    /**
     * 阅卷任务：教师端「阅卷」页要列出自己发起过的任务
     */
    private final EduMarkingTaskMapper markingTaskMapper;

    @Override
    public Map<String, Object> me() {
        Map<String, Object> result = new HashMap<>();
        EduTeacher teacher = findMyTeacher();
        if (teacher == null) {
            result.put("bound", false);
            return result;
        }
        result.put("bound", true);
        result.put("teacher", describeTeacher(teacher));
        return result;
    }

    @Override
    public Map<String, Object> bind(TeacherBindDTO dto) {
        if (dto == null) {
            throw new BusinessException("绑定信息不能为空");
        }
        String account = EduUserContext.requireUserId();
        String no = trimToNull(dto.getTeacherNo());
        if (no == null) {
            throw new BusinessException("请填写工号");
        }
        EduTeacher teacher = teacherMapper.selectOne(
                new QueryWrapper<EduTeacher>().eq("teacher_no", no).last("LIMIT 1"));
        if (teacher == null) {
            teacher = new EduTeacher();
            teacher.setTeacherNo(no);
            teacher.setOwnerUserId(account);
        } else if (teacher.getTeacherAccount() != null && !teacher.getTeacherAccount().equals(account)) {
            throw new BusinessException("该工号已被其它账号绑定，请联系管理员处理");
        }
        teacher.setTeacherAccount(account);
        if (trimToNull(dto.getTeacherName()) != null) {
            teacher.setTeacherName(dto.getTeacherName().trim());
        }
        if (trimToNull(dto.getSubjectCode()) != null) {
            teacher.setTeacherSubjectCode(dto.getSubjectCode().trim());
        }
        teacher.setTeacherStatus(1);
        if (teacher.getTeacherId() == null) {
            teacherMapper.insert(teacher);
        } else {
            teacherMapper.updateById(teacher);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("teacher", describeTeacher(teacher));
        return out;
    }

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        EduTeacher teacher = findMyTeacher();
        if (teacher == null) {
            result.put("bound", false);
            return result;
        }
        result.put("bound", true);
        result.put("teacher", describeTeacher(teacher));

        // 带的班级：班主任列（class_teacher_id 是数字 userId，与字符串账号不同源），
        // 因此这里按班级创建者归属。列名要用 class_owner_user_id ——
        // 实体字段叫 ownerUserId，默认映射出的 owner_user_id 在库里不存在。
        List<EduClass> classes = classMapper.selectList(new QueryWrapper<EduClass>()
                .eq("class_owner_user_id", EduUserContext.requireUserId()));
        List<Map<String, Object>> classRows = new ArrayList<>();
        int totalStudents = 0;
        for (EduClass clazz : classes) {
            List<EduStudent> students = studentMapper.selectList(new QueryWrapper<EduStudent>()
                    .eq("student_class_id", clazz.getClassId()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classId", clazz.getClassId());
            row.put("className", clazz.getClassName());
            row.put("gradeLevel", clazz.getClassGradeLevel());
            row.put("grade", clazz.getClassGrade());
            row.put("studentCount", students.size());
            row.put("boundCount", students.stream().filter(s -> s.getStudentAccount() != null).count());
            classRows.add(row);
            totalStudents += students.size();
        }
        result.put("classes", classRows);
        result.put("classCount", classRows.size());
        result.put("studentCount", totalStudents);
        return result;
    }

    // ==================== 教师端：我的班级 ====================

    @Override
    public List<EduClass> myClasses() {
        requireTeacher();
        return classMapper.selectList(new QueryWrapper<EduClass>()
                .eq("class_owner_user_id", EduUserContext.requireUserId())
                .orderByAsc("class_id"));
    }

    @Override
    public Long saveClass(EduClass entity) {
        requireTeacher();
        if (entity == null) {
            throw new BusinessException("班级信息不能为空");
        }
        if (entity.getClassId() != null && entity.getClassId() > 0) {
            // 编辑：必须是自己的班级；owner 在 dimensionService 里会沿用库中值
            requireMyClass(entity.getClassId());
        }
        return dimensionService.saveClass(entity);
    }

    @Override
    public boolean deleteClass(Long classId) {
        requireTeacher();
        EduClass exists = requireMyClass(classId);
        Long studentCount = studentMapper.selectCount(new QueryWrapper<EduStudent>()
                .eq("student_class_id", exists.getClassId()));
        if (studentCount != null && studentCount > 0) {
            throw new BusinessException("班级下还有 " + studentCount + " 名学生，请先转出或删除后再删班");
        }
        return dimensionService.deleteClass(classId);
    }

    // ==================== 教师端：我的班级学生 ====================

    @Override
    public List<EduStudent> classStudents(Long classId) {
        requireTeacher();
        EduClass clazz = requireMyClass(classId);
        return studentMapper.selectList(new QueryWrapper<EduStudent>()
                .eq("student_class_id", clazz.getClassId())
                .orderByAsc("student_id"));
    }

    @Override
    public Long saveStudent(EduStudent entity) {
        requireTeacher();
        if (entity == null) {
            throw new BusinessException("学生信息不能为空");
        }
        EduClass target = requireMyClass(entity.getStudentClassId());
        if (entity.getStudentId() != null && entity.getStudentId() > 0) {
            EduStudent exists = studentMapper.selectById(entity.getStudentId());
            if (exists == null) {
                throw new BusinessException("学生不存在");
            }
            // 学生当前所在班也必须是自己的，防止把别人的学生「拉」进我的班
            requireMyClass(exists.getStudentClassId());
        }
        // 学段 / 年级 / 班级随班级，不接受客户端自行指定，避免学生与班级对不上
        entity.setStudentGradeLevel(target.getClassGradeLevel());
        entity.setStudentGrade(target.getClassGrade());
        entity.setStudentClassId(target.getClassId());
        return dimensionService.saveStudent(entity);
    }

    @Override
    public boolean deleteStudent(Long studentId) {
        requireTeacher();
        if (studentId == null) {
            return false;
        }
        EduStudent exists = studentMapper.selectById(studentId);
        if (exists == null) {
            return false;
        }
        requireMyClass(exists.getStudentClassId());
        return dimensionService.deleteStudent(studentId);
    }

    @Override
    public boolean resetStudentPassword(Long studentId, String newPassword) {
        requireTeacher();
        if (studentId == null) {
            throw new BusinessException("学生不能为空");
        }
        EduStudent exists = studentMapper.selectById(studentId);
        if (exists == null) {
            throw new BusinessException("学生不存在");
        }
        requireMyClass(exists.getStudentClassId());
        return dimensionService.resetStudentPassword(studentId, newPassword);
    }

    @Override
    public EduDimensionService.DimensionDropdown dropdown() {
        // 只读字典，归属过滤由 dimensionService 内部的 ownerScope 保证，不放宽可见范围
        return dimensionService.getDropdown();
    }

    // ==================== 教师端：我的阅卷任务 ====================

    @Override
    public List<EduMarkingTask> myMarkingTasks(int limit) {
        String me = EduUserContext.requireUserId();
        int size = limit <= 0 ? DEFAULT_TASK_LIMIT : Math.min(limit, MAX_TASK_LIMIT);
        // size 是经上限裁剪的 int，非用户可控字符串，不存在注入面（AGENTS.md 禁的是拼接 SQL 片段）
        return markingTaskMapper.selectList(new QueryWrapper<EduMarkingTask>()
                .eq("marking_task_owner_user_id", me)
                .orderByDesc("marking_task_id")
                .last("LIMIT " + size));
    }

    @Override
    public PageResult<EduMarkingTask> pageMyMarkingTasks(Integer pageNum, Integer pageSize) {
        String me = EduUserContext.requireUserId();
        Page<EduMarkingTask> page = new Page<>(PageBounds.pageNum(pageNum), PageBounds.pageSize(pageSize));
        IPage<EduMarkingTask> result = markingTaskMapper.selectPage(page, new QueryWrapper<EduMarkingTask>()
                .eq("marking_task_owner_user_id", me)
                .orderByDesc("marking_task_id"));
        return PageResult.<EduMarkingTask>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    // ==================== 内部工具 ====================

    /**
     * 要求当前账号已绑定教师工号
     *
     * @return 教师档案
     * @throws BusinessException 未绑定时抛出
     */
    private EduTeacher requireTeacher() {
        EduTeacher teacher = findMyTeacher();
        if (teacher == null) {
            throw new BusinessException("请先在老师端绑定教师工号");
        }
        return teacher;
    }

    /**
     * 取「我的班级」，顺带做归属校验
     *
     * @param classId 班级标识
     * @return 班级实体
     * @throws BusinessException 班级不存在或不属于当前账号时抛出
     */
    private EduClass requireMyClass(Long classId) {
        if (classId == null) {
            throw new BusinessException("请选择班级");
        }
        EduClass clazz = classMapper.selectById(classId);
        if (clazz == null) {
            throw new BusinessException("班级不存在");
        }
        assertOwned(EduUserContext.requireUserId(), clazz.getOwnerUserId());
        return clazz;
    }

    /**
     * 归属守卫：当前账号必须等于数据行的归属账号，否则拒绝。
     *
     * <p>抽成包级静态方法是为了能被单元测试直接覆盖（{@code TeacherOwnershipGuardTest}）——
     * 这是教师端隔离的核心断言，不能只靠集成测试。</p>
     *
     * @param currentAccount 当前登录账号
     * @param ownerAccount   数据行归属账号
     * @throws BusinessException 两者不一致（含任一为 null）时抛出
     */
    static void assertOwned(String currentAccount, String ownerAccount) {
        if (currentAccount == null || !currentAccount.equals(ownerAccount)) {
            throw new BusinessException("无权操作该班级");
        }
    }

    /**
     * 取当前账号绑定的教师档案
     *
     * @return 教师档案，未绑定时为 null
     */
    private EduTeacher findMyTeacher() {
        String account = EduUserContext.currentUserId();
        if (account == null || account.isBlank()) {
            return null;
        }
        return teacherMapper.selectOne(new QueryWrapper<EduTeacher>()
                .eq("teacher_account", account).last("LIMIT 1"));
    }

    /**
     * 教师档案的前端字段
     *
     * @param teacher 教师档案
     * @return 字段表
     */
    private Map<String, Object> describeTeacher(EduTeacher teacher) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("teacherId", teacher.getTeacherId());
        t.put("teacherNo", teacher.getTeacherNo());
        t.put("teacherName", teacher.getTeacherName());
        t.put("subjectCode", teacher.getTeacherSubjectCode());
        return t;
    }

    /**
     * 空白串归一成 null
     *
     * @param s 原串
     * @return 去空白后的串或 null
     */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
