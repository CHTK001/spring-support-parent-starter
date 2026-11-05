// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.starter.eduai.dimension.mapper.EduTeacherClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.mapper.EduAnswerSheetMapper;
import com.chua.starter.eduai.mapper.EduMarkingResultMapper;
import com.chua.starter.eduai.mapper.EduMarkingTaskMapper;
import com.chua.starter.eduai.model.EduAnswerSheet;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;
import com.chua.starter.eduai.model.vo.HomeworkCompletionVO;
import com.chua.starter.eduai.model.vo.MarkingStatsVO;
import com.chua.starter.eduai.service.IEduTeacherCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教师中心服务实现
 *
 * <p>教师端所有写操作都先做两层授权：
 * ① 账号必须已绑定教师工号（{@link #requireTeacher()}）；
 * ② 目标班级必须已配置给当前账号绑定的教师档案
 * （{@code edu_teacher_class}，见 {@link #requireMyClass(Long)}）。
 * 二者缺一即抛业务异常，因此教师无法通过改 URL 里的 ID 去动别人的班或学生。</p>
 *
 * <p><b>V1.14 数据范围调整</b>：班级由管理员统一维护，
 * 老师端「我的班级」完全来自后台配置（学生管理 → 教师 → 配置班级），
 * 老师端不再新增/删除班级；学生管理仍可在配置给自己的班级内进行。</p>
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

    /**
     * 答题卡 content / 判分明细的 JSON 解析（只读字段，无状态可共享）
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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

    private final EduMarkingResultMapper markingResultMapper;

    /**
     * 答题卡：阅卷列表的学生姓名取自答题卡 content 里卷面识别的姓名
     * （任意卷扫描时从卷头「姓名」栏抽取，任务表只存学号）
     */
    private final EduAnswerSheetMapper answerSheetMapper;

    /**
     * 教师班级配置：老师端数据范围（V1.14 起，班级由管理员配置，
     * 老师端不再按 class_owner_user_id 自建班级）
     */
    private final EduTeacherClassMapper teacherClassMapper;

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
        String no = trimToNull(dto.teacherNo());
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
        if (trimToNull(dto.teacherName()) != null) {
            teacher.setTeacherName(dto.teacherName().trim());
        }
        if (trimToNull(dto.subjectCode()) != null) {
            teacher.setTeacherSubjectCode(dto.subjectCode().trim());
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

        // 带的班级：V1.14 起完全来自后台配置（edu_teacher_class），
        // 老师端数据范围 = 管理员配置的班级。
        List<EduClass> classes = myClasses();
        List<Map<String, Object>> classRows = new ArrayList<>();
        int totalStudents = 0;
        // 批量查所有班级的学生，消除 N+1（1 次查询替代 N 次）
        if (!classes.isEmpty()) {
            List<Long> classIds = classes.stream().map(EduClass::getClassId).toList();
            List<EduStudent> allStudents = studentMapper.selectList(new QueryWrapper<EduStudent>()
                    .in("student_class_id", classIds));
            Map<Long, List<EduStudent>> byClass = allStudents.stream()
                    .collect(java.util.stream.Collectors.groupingBy(EduStudent::getStudentClassId));
            for (EduClass clazz : classes) {
                List<EduStudent> students = byClass.getOrDefault(clazz.getClassId(), Collections.emptyList());
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
        }
        result.put("classes", classRows);
        result.put("classCount", classRows.size());
        result.put("studentCount", totalStudents);
        return result;
    }

    // ==================== 教师端：我的班级 ====================

    @Override
    public List<EduClass> myClasses() {
        EduTeacher teacher = requireTeacher();
        return dimensionService.listTeacherClasses(teacher.getTeacherId());
    }

    @Override
    public Long saveClass(EduClass entity) {
        requireTeacher();
        // V1.14 起班级由管理员统一维护（学生管理 → 班级），
        // 老师端只做「布置」等教学功能，不再新增/修改班级；
        // 老师的数据范围由「学生管理 → 教师 → 配置班级」决定。
        throw new BusinessException("班级由管理员统一维护，老师端不可新增/修改；如需调整请在后台「学生管理」中配置");
    }

    @Override
    public boolean deleteClass(Long classId) {
        requireTeacher();
        // 同 saveClass：班级生命周期归管理员，老师端不可删班。
        throw new BusinessException("班级由管理员统一维护，老师端不可删除；如需调整请在后台「学生管理」中配置");
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
        List<EduMarkingTask> tasks = markingTaskMapper.selectList(new QueryWrapper<EduMarkingTask>()
                .eq("marking_task_owner_user_id", me)
                .orderByDesc("marking_task_id")
                .last("LIMIT " + size));
        fillStudentNames(tasks);
        return tasks;
    }

    @Override
    public PageResult<EduMarkingTask> pageMyMarkingTasks(Integer pageNum, Integer pageSize, Integer teacherFinished) {
        String me = EduUserContext.requireUserId();
        Page<EduMarkingTask> page = new Page<>(PageBounds.pageNum(pageNum), PageBounds.pageSize(pageSize));
        QueryWrapper<EduMarkingTask> query = new QueryWrapper<EduMarkingTask>()
                .eq("marking_task_owner_user_id", me);
        // 完成状态筛选：历史行该列为 NULL（功能上线前完成的任务），
        // 「未完成」必须把它们一起算进来，否则老师永远筛不出这些待办。
        if (Integer.valueOf(1).equals(teacherFinished)) {
            query.eq("marking_task_teacher_finished", 1);
        } else if (Integer.valueOf(0).equals(teacherFinished)) {
            query.and(w -> w.eq("marking_task_teacher_finished", 0)
                    .or().isNull("marking_task_teacher_finished"));
        }
        query.orderByDesc("marking_task_id");
        IPage<EduMarkingTask> result = markingTaskMapper.selectPage(page, query);
        fillStudentNames(result.getRecords());
        return PageResult.<EduMarkingTask>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    public MarkingStatsVO markingStats(String taskId) {
        requireTeacher();
        EduMarkingTask task = markingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("阅卷任务不存在");
        }
        // 归属校验：任务必须是我的
        if (!EduUserContext.currentUserId().equals(task.getOwnerUserId())) {
            throw new BusinessException("无权查看该任务");
        }
        // 取所有结果行计算统计
        List<EduMarkingResult> results = markingResultMapper.selectList(
                new QueryWrapper<EduMarkingResult>()
                        .eq("marking_result_task_id", taskId));
        if (results.isEmpty()) {
            return MarkingStatsVO.builder()
                    .taskId(taskId)
                    .totalCount(0)
                    .scoredCount(0)
                    .avgScore(0.0)
                    .maxScore(null)
                    .minScore(null)
                    .distribution(java.util.Collections.emptyList())
                    .build();
        }
        // 按答题卡去重（一份卷只有一个最终分，取 teacherScore 优先、否则 AI 分）
        Map<Long, Integer> sheetBestScore = new java.util.LinkedHashMap<>();
        for (EduMarkingResult r : results) {
            Long sheetId = r.getMarkingResultAnswerSheetId();
            if (sheetId == null) {
            continue;
        }
            int candidate = r.getMarkingResultTeacherScore() != null
                    ? r.getMarkingResultTeacherScore()
                    : r.getMarkingResultScore() != null ? r.getMarkingResultScore() : 0;
            Integer existing = sheetBestScore.get(sheetId);
            if (existing == null || candidate > existing) {
                sheetBestScore.put(sheetId, candidate);
            }
        }
        List<Integer> scores = new java.util.ArrayList<>(sheetBestScore.values());
        int totalCount = scores.size();
        double avg = scores.stream().mapToInt(Integer::intValue).summaryStatistics().getAverage();
        int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        // 分段统计：0-60, 60-80, 80-100
        Map<String, Integer> rangeCount = new java.util.LinkedHashMap<>();
        rangeCount.put("0-60", 0);
        rangeCount.put("60-80", 0);
        rangeCount.put("80-100", 0);
        for (int s : scores) {
            if (s < 60) {
                rangeCount.computeIfPresent("0-60", (k, v) -> v + 1);
            } else if (s < 80) {
                rangeCount.computeIfPresent("60-80", (k, v) -> v + 1);
            } else {
                rangeCount.computeIfPresent("80-100", (k, v) -> v + 1);
            }
        }
        List<MarkingStatsVO.ScoreRange> distribution = rangeCount.entrySet().stream()
                .map(e -> MarkingStatsVO.ScoreRange.builder().range(e.getKey()).count(e.getValue()).build())
                .toList();
        return MarkingStatsVO.builder()
                .taskId(taskId)
                .totalCount(totalCount)
                .scoredCount(totalCount)
                .avgScore(Math.round(avg * 100.0) / 100.0)
                .maxScore(max)
                .minScore(min)
                .distribution(distribution)
                .build();
    }

    @Override
    public HomeworkCompletionVO homeworkCompletion(Long paperId, Long examId) {
        requireTeacher();
        // 查该试卷关联的阅卷任务（按 examId 或 paperId）
        QueryWrapper<EduMarkingTask> taskQuery = new QueryWrapper<>();
        taskQuery.eq("marking_task_owner_user_id", EduUserContext.currentUserId());
        if (examId != null) {
            taskQuery.eq("marking_task_exam_id", examId);
        }
        if (paperId != null) {
            taskQuery.eq("marking_task_paper_id", paperId);
        }
        List<EduMarkingTask> tasks = markingTaskMapper.selectList(taskQuery);
        if (tasks.isEmpty()) {
            return HomeworkCompletionVO.builder()
                    .paperId(paperId)
                    .totalStudents(0)
                    .submittedCount(0)
                    .markedCount(0)
                    .completionRate(0.0)
                    .classStats(java.util.Collections.emptyList())
                    .build();
        }
        // 统计已提交/已完成人数（按 studentId 去重）
        Set<String> submitted = new java.util.LinkedHashSet<>();
        Set<String> marked = new java.util.LinkedHashSet<>();
        for (EduMarkingTask t : tasks) {
            String sid = trimToNull(t.getMarkingTaskStudentId());
            if (sid != null && !"anonymous".equals(sid)) {
                submitted.add(sid);
                if (Integer.valueOf(EduMarkingTask.STATUS_DONE).equals(t.getMarkingTaskStatus())) {
                    marked.add(sid);
                }
            }
        }
        return HomeworkCompletionVO.builder()
                .paperId(paperId)
                .totalStudents(submitted.size())
                .submittedCount(submitted.size())
                .markedCount(marked.size())
                .completionRate(submitted.isEmpty() ? 0.0 : Math.round((double) marked.size() / submitted.size() * 10000.0) / 10000.0)
                .classStats(java.util.Collections.emptyList())
                .build();
    }

    /**
     * 补阅卷任务的学生姓名（列表展示「学号 + 姓名」）。 <p>姓名不在 {@code edu_marking_task} 上，来源两级： ① 答题卡 content 的 {@code recognizedStudent.name}——任意卷扫描时 模型/正则从卷头「姓名」栏抽取，任务上只存了学号（或匿名标记）； ② 按学号查 {@code edu_student.student_name}（题库/后台建档的学生）。 任意卷的学号常是卷面手写串，② 查不到属正常，此时只显示学号。</p> <p>同页任务按学号合并查一次学生表，避免 N+1；答题卡按 id 逐个取 （一页 10~20 条，且只有有答题卡的任务才查）。</p>
     * @param tasks 参数
     * @return 返回值
    */
    private void fillStudentNames(List<EduMarkingTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<String> pendingNos = new LinkedHashSet<>();
        for (EduMarkingTask task : tasks) {
            String no = trimToNull(task.getMarkingTaskStudentId());
            if (no != null) {
                pendingNos.add(no);
            }
        }
        Map<String, String> byNo = new HashMap<>();
        if (!pendingNos.isEmpty()) {
            List<EduStudent> students = studentMapper.selectList(new QueryWrapper<EduStudent>()
                    .in("student_no", pendingNos));
            for (EduStudent student : students) {
                String name = trimToNull(student.getStudentName());
                String no = trimToNull(student.getStudentNo());
                if (no != null && name != null) {
                    byNo.put(no, name);
                }
            }
        }
        for (EduMarkingTask task : tasks) {
            String no = trimToNull(task.getMarkingTaskStudentId());
            String name = no == null ? null : byNo.get(no);
            if (name == null && task.getMarkingTaskAnswerSheetId() != null) {
                name = recognizedName(task.getMarkingTaskAnswerSheetId());
            }
            task.setMarkingTaskStudentName(name);
        }
    }

    /**
     * 读答题卡 content 里卷面识别的姓名（{@code recognizedStudent.name}）。 <p>与阅卷详情接口同源（{@code getMarkingStructure} 的 {@code recognizedStudent}），此处只取姓名做列表展示。</p>
     * @param answerSheetId 长整数
     * @return 返回值
    */
    private String recognizedName(Long answerSheetId) {
        EduAnswerSheet sheet = answerSheetMapper.selectById(answerSheetId);
        if (sheet == null || !StringUtils.hasText(sheet.getAnswerSheetContent())) {
            return null;
        }
        Map<String, Object> content = parseJsonMap(sheet.getAnswerSheetContent());
        Object recognized = content.get("recognizedStudent");
        if (!(recognized instanceof Map)) {
            return null;
        }
        return trimToNull(String.valueOf(((Map<?, ?>) recognized).get("name")));
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
        // V1.14 起归属判定 = 后台班级配置（edu_teacher_class）：
        // 班级必须配置给当前账号绑定的教师档案，不再比对 class_owner_user_id。
        EduTeacher teacher = requireTeacher();
        List<Long> myClassIds = teacherClassMapper.selectClassIdsByTeacher(teacher.getTeacherId());
        if (!myClassIds.contains(classId)) {
            throw new BusinessException("无权操作该班级");
        }
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
     * 取当前账号绑定的教师档案。
     *
     * <p>先按 {@code teacher_account = 登录态 userId} 找，找不到再按
     * {@code teacher_no = 登录态 userId} 找：教师现在可以「工号 + 口令」直接登录
     * （{@code TeacherCredentialProvider}），令牌里的 userId 可能是工号本身，
     * 而管理员也可能把绑定账号改成别的值。只按绑定列查会让这些教师登不进老师端，
     * 且现象是「明明工号能登录、进去却提示未绑定」，很难自查。</p>
     *
     * @return 教师档案；未登录或未绑定时为 {@code null}
     */
    private EduTeacher findMyTeacher() {
        String account = EduUserContext.currentUserId();
        if (account == null || account.isBlank()) {
            return null;
        }
        var byAccount = teacherMapper.selectOne(new QueryWrapper<EduTeacher>()
                .eq("teacher_account", account).last("LIMIT 1"));
        if (byAccount != null) {
            return byAccount;
        }
        return teacherMapper.selectOne(new QueryWrapper<EduTeacher>()
                .eq("teacher_no", account).last("LIMIT 1"));
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

    /**
     * 从结果 detail JSON 取满分（统计得分率用）。
     *
     * <p>阅卷结果行的 {@code marking_result_detail} 是 JSON 文本，
     * 判分阶段会写入 {@code maxScore}。取不到时返回 0，调用方跳过该行。</p>
     *
     * @param detail 结果详情 JSON
     * @return 满分；缺失/非法时 0
     */
    private int parseDetailMaxScore(String detail) {
        if (!StringUtils.hasText(detail)) {
            return 0;
        }
        Map<String, Object> map = parseJsonMap(detail);
        Object v = map.get("maxScore");
        if (v instanceof Number n) {
            int s = n.intValue();
            return s > 0 ? s : 0;
        }
        return 0;
    }

    /**
     * 把 JSON 文本解析为 Map。
     *
     * @param json JSON 文本
     * @return 键值映射；为空或解析失败时返回空 Map
     */
    private static Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return JSON_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}