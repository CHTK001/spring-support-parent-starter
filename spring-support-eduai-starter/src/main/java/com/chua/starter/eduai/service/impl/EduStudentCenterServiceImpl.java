// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.mapper.EduExamMapper;
import com.chua.starter.eduai.mapper.EduPaperAssignMapper;
import com.chua.starter.eduai.mapper.EduPaperMapper;
import com.chua.starter.eduai.mapper.EduStudentDailyMapper;
import com.chua.starter.eduai.mapper.EduStudentProgressMapper;
import com.chua.starter.eduai.mapper.EduWrongQuestionMapper;
import com.chua.starter.eduai.mapper.EduMarkingTaskMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.EduStudentDaily;
import com.chua.starter.eduai.model.EduStudentProgress;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.dto.StudentBindDTO;
import com.chua.starter.eduai.model.dto.StudentPaperSubmitDTO;
import com.chua.starter.eduai.model.dto.StudyReportDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.PaperAssignVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.model.vo.QuestionVO;
import com.chua.starter.eduai.model.vo.StudentWrongQuestionVO;
import com.chua.starter.eduai.service.IEduMarkingService;
import com.chua.starter.eduai.service.IEduPaperService;
import com.chua.starter.eduai.service.IEduStudentCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学生中心服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduStudentCenterServiceImpl implements IEduStudentCenterService {

    /**
     * 已掌握状态（edu_wrong_question.wrong_question_mastery_level = 2）
     */
    private static final int MASTERY_DONE = 2;

    /**
     * 错题有效状态（0 为已删除）
     */
    private static final int WRONG_STATUS_VALID = 1;

    /**
     * 连续打卡最多回溯天数：正常最多几十天，取 400 足够覆盖一年且不会无限查
     */
    private static final int STREAK_LOOKBACK = 400;

    private final EduStudentMapper studentMapper;

    private final EduClassMapper classMapper;

    private final EduTeacherMapper teacherMapper;

    private final EduStudentProgressMapper progressMapper;

    private final EduStudentDailyMapper dailyMapper;

    private final EduWrongQuestionMapper wrongQuestionMapper;

    private final EduMarkingTaskMapper markingTaskMapper;

    private final IEduMarkingService markingService;

    private final EduQuestionMapper questionMapper;

    /**
     * 试卷布置：学生按「布置给我班」授权作答与提交
     */
    private final EduPaperAssignMapper assignMapper;

    /**
     * 考试：在线作答按（试卷，班级）定位/补建考试，供阅卷与防重复提交挂靠
     */
    private final EduExamMapper examMapper;

    /**
     * 试卷：补建考试时取卷名等信息
     */
    private final EduPaperMapper paperMapper;

    /**
     * 试卷详情：学生端按布置关系授权后取题（不走归属校验）
     */
    private final IEduPaperService paperService;

    @Override
    public List<EduMarkingTask> myMarkingTasks(int limit) {
        String me = EduUserContext.requireUserId();
        int size = limit <= 0 ? 20 : Math.min(limit, 100);
        // 学生端按任务上的学生标识过滤（不是任务归属人——那是上传答卷的老师）
        return markingTaskMapper.selectList(new QueryWrapper<EduMarkingTask>()
                .eq("marking_task_student_id", me)
                .orderByDesc("marking_task_id")
                .last("LIMIT " + size));
    }

    @Override
    public MarkingResultVO myMarkingResult(String taskId) {
        return markingService.getResultForStudent(taskId, EduUserContext.requireUserId());
    }

    @Override
    public List<StudentWrongQuestionVO> myWrongQuestions(String subject, Integer masteryLevel) {
        String me = EduUserContext.requireUserId();
        List<EduWrongQuestion> rows = wrongQuestionMapper.selectList(new QueryWrapper<EduWrongQuestion>()
                .eq("wrong_question_student_id", me)
                .eq("wrong_question_status", WRONG_STATUS_VALID)
                .eq(masteryLevel != null, "wrong_question_mastery_level", masteryLevel)
                .orderByDesc("wrong_question_last_wrong_time"));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查题目，消除 N+1
        List<Long> questionIds = rows.stream()
                .map(EduWrongQuestion::getWrongQuestionQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, EduQuestion> questionMap = questionIds.isEmpty()
                ? Collections.emptyMap()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .collect(java.util.stream.Collectors.toMap(EduQuestion::getQuestionId, q -> q, (a, b) -> a));
        List<StudentWrongQuestionVO> out = new ArrayList<>();
        for (EduWrongQuestion wrong : rows) {
            EduQuestion question = questionMap.get(wrong.getWrongQuestionQuestionId());
            if (subject != null && !subject.isBlank()
                    && (question == null || !subject.equals(question.getQuestionSubject()))) {
                continue;
            }
            out.add(new StudentWrongQuestionVO(
                    wrong.getWrongQuestionId(),
                    wrong.getWrongQuestionQuestionId(),
                    question == null ? null : question.getQuestionType(),
                    question == null ? null : question.getQuestionSubject(),
                    question == null ? null : question.getQuestionGradeLevel(),
                    question == null ? null : question.getQuestionGrade(),
                    question == null ? null : question.getQuestionDifficulty(),
                    question == null ? null : question.getQuestionStem(),
                    question == null ? null : question.getQuestionOptions(),
                    question == null ? null : question.getQuestionAnswer(),
                    wrong.getWrongQuestionWrongCount(),
                    wrong.getWrongQuestionMasteryLevel(),
                    wrong.getWrongQuestionReviewCount(),
                    wrong.getWrongQuestionLastWrongTime(),
                    wrong.getWrongQuestionCorrected()));
        }
        return out;
    }

    @Override
    public boolean updateMyWrongMastery(Long wrongId, Integer masteryLevel) {
        if (wrongId == null) {
            throw new BusinessException("错题记录不能为空");
        }
        if (masteryLevel == null || masteryLevel < 0 || masteryLevel > MASTERY_DONE) {
            throw new BusinessException("掌握度只能是 0 / 1 / 2");
        }
        EduWrongQuestion wrong = wrongQuestionMapper.selectById(wrongId);
        if (wrong == null) {
            throw new BusinessException("错题记录不存在");
        }
        if (!EduUserContext.requireUserId().equals(wrong.getWrongQuestionStudentId())) {
            throw new BusinessException("无权操作该错题");
        }
        wrong.setWrongQuestionMasteryLevel(masteryLevel);
        wrong.setWrongQuestionLastReviewTime(LocalDateTime.now());
        wrong.setWrongQuestionReviewCount(
                (wrong.getWrongQuestionReviewCount() == null ? 0 : wrong.getWrongQuestionReviewCount()) + 1);
        wrongQuestionMapper.updateById(wrong);
        return true;
    }

    @Override
    public boolean markMyWrongCorrected(Long wrongId) {
        if (wrongId == null) {
            throw new BusinessException("错题记录不能为空");
        }
        EduWrongQuestion wrong = wrongQuestionMapper.selectById(wrongId);
        if (wrong == null) {
            throw new BusinessException("错题记录不存在");
        }
        if (!EduUserContext.requireUserId().equals(wrong.getWrongQuestionStudentId())) {
            throw new BusinessException("无权操作该错题");
        }
        wrong.setWrongQuestionCorrected(1);
        wrong.setWrongQuestionCorrectTime(LocalDateTime.now());
        wrongQuestionMapper.updateById(wrong);
        return true;
    }

    // ==================== 我的在线作答 ====================

    @Override
    public PaperVO myAnswerPaper(Long paperId) {
        EduStudent student = requireBoundStudent();
        requireAssignedPaper(paperId, student);
        PaperVO paper = paperService.getPaperDetailUnchecked(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在或已删除");
        }
        // 脱敏：在线作答只发题干与选项，标准答案/解析必须抹掉，
        // 否则学生打开接口就能拿到答案，作答就失去意义
        List<QuestionVO> safeQuestions = paper.questions() == null ? Collections.emptyList()
                : paper.questions().stream()
                        .map(q -> new QuestionVO(q.id(), q.type(), q.stem(), q.options(),
                                null, null, q.score(), q.questionNo(), q.order(), q.section()))
                        .toList();
        return paper.withQuestions(safeQuestions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitMyPaperAnswer(Long paperId, StudentPaperSubmitDTO dto) {
        if (paperId == null) {
            throw new BusinessException("试卷标识不能为空");
        }
        if (dto == null || dto.answers() == null || dto.answers().isEmpty()) {
            throw new BusinessException("作答内容不能为空");
        }
        EduStudent student = requireBoundStudent();
        PaperAssignVO assign = requireAssignedPaper(paperId, student);
        String me = EduUserContext.requireUserId();

        // 老师布置即视为一场班级测验：考试按（试卷，班级）就位，首次有学生作答时补建。
        // 考试与后续阅卷任务归属记为布置老师，学生交的卷子才会出现在老师的阅卷列表里。
        Long examId = resolveOrCreateExam(paperId, student.getStudentClassId(), assign.getTeacherAccount());

        // 学生标识用登录账号：与教师端阅卷台按学号录入同一口径（学生端任务按登录账号过滤）
        MarkingSubmitDTO submit = new MarkingSubmitDTO(examId, me, dto.content(), dto.answers());
        return markingService.submitAnswerSheet(submit, assign.getTeacherAccount());
    }

    /**
     * 取当前登录账号绑定的学生档案（未绑定或未分班直接拒绝）
     *
     * @return 学生档案
     */
    private EduStudent requireBoundStudent() {
        EduStudent student = findMyStudent();
        if (student == null) {
            throw new BusinessException("尚未绑定学生信息，请先完成绑定");
        }
        if (student.getStudentClassId() == null) {
            throw new BusinessException("尚未加入班级，请联系老师完成分班");
        }
        return student;
    }

    /**
     * 校验试卷确实布置给了该生所在班级（已布置 + 试卷已发布），并返回布置信息
     *
     * @param paperId 试卷标识
     * @param student 学生档案
     * @return 布置视图
     */
    private PaperAssignVO requireAssignedPaper(Long paperId, EduStudent student) {
        if (paperId == null) {
            throw new BusinessException("试卷标识不能为空");
        }
        List<PaperAssignVO> assigned = assignMapper.selectByClass(student.getStudentClassId());
        return assigned.stream()
                .filter(a -> paperId.equals(a.getPaperId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该试卷未布置给你的班级"));
    }

    /**
     * 按（试卷，班级）取考试，没有则补建一场「班级测验」。
     *
     * <p>并发窗口内可能重复建行：数据库没有 (paper_id, class_id) 唯一键，这里不加锁；
     * 重复行的代价只是多一场同名考试，不影响判分正确性 —— 答题卡唯一键挂在
     * (exam_id, student_id) 上，同一学生仍只有一份答卷。</p>
     *
     * @param paperId        试卷标识
     * @param classId        班级标识
     * @param teacherAccount 布置老师（考试归属人）
     * @return 考试标识
     */
    private Long resolveOrCreateExam(Long paperId, Long classId, String teacherAccount) {
        String classKey = String.valueOf(classId);
        EduExam exam = examMapper.selectOne(new QueryWrapper<EduExam>()
                .eq("exam_paper_id", paperId)
                .eq("exam_class_id", classKey)
                .last("LIMIT 1"));
        if (exam != null) {
            return exam.getExamId();
        }
        EduPaper paper = paperMapper.selectById(paperId);
        EduExam created = new EduExam();
        created.setExamPaperId(paperId);
        created.setExamName((paper == null || paper.getPaperName() == null ? "试卷" : paper.getPaperName())
                + "·班级测验");
        created.setExamClassId(classKey);
        created.setExamStartTime(LocalDateTime.now());
        if (paper != null && paper.getPaperDuration() != null) {
            created.setExamDuration(paper.getPaperDuration());
        }
        created.setExamStatus(1);
        created.setOwnerUserId(teacherAccount);
        examMapper.insert(created);
        return created.getExamId();
    }

    @Override
    public Map<String, Object> me() {
        Map<String, Object> result = new HashMap<>();
        EduStudent student = findMyStudent();
        if (student == null) {
            // 也可以绑定老师档案：老师登录学生端时给出提示，而不是当成「学生没绑定」
            result.put("bound", false);
            result.put("teacherBound", findMyTeacher() != null);
            return result;
        }
        result.put("bound", true);
        result.putAll(describe(student));
        return result;
    }

    @Override
    public Map<String, Object> bind(StudentBindDTO dto) {
        if (dto == null) {
            throw new BusinessException("绑定信息不能为空");
        }
        String account = EduUserContext.requireUserId();
        String no = trimToNull(dto.studentNo());
        String name = trimToNull(dto.studentName());
        if (no == null && name == null) {
            throw new BusinessException("请填写学号或姓名");
        }

        EduStudent student = no == null ? null : studentMapper.selectOne(
                new QueryWrapper<EduStudent>().eq("student_no", no).last("LIMIT 1"));
        if (student == null) {
            student = new EduStudent();
            student.setStudentNo(no == null ? generateStudentNo(account) : no);
            student.setOwnerUserId(account);
        } else if (student.getStudentAccount() != null && !student.getStudentAccount().equals(account)) {
            // 学号被别人绑走了：这是数据归属问题，不能让后来者静默夺走
            throw new BusinessException("该学号已被其它账号绑定，请联系老师处理");
        }
        student.setStudentAccount(account);
        if (name != null) {
            student.setStudentName(name);
        }
        String level = normalizeLevel(dto.gradeLevel());
        if (level != null) {
            student.setStudentGradeLevel(level);
        }
        if (trimToNull(dto.grade()) != null) {
            student.setStudentGrade(dto.grade().trim());
        }
        student.setStudentStatus(1);
        Long classId = resolveClass(dto, level, account);
        if (classId != null) {
            student.setStudentClassId(classId);
        }

        if (student.getStudentId() == null) {
            studentMapper.insert(student);
        } else {
            studentMapper.updateById(student);
        }
        return describe(student);
    }

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        EduStudent student = findMyStudent();
        if (student == null) {
            result.put("bound", false);
            return result;
        }
        result.put("bound", true);
        result.putAll(describe(student));

        LocalDate today = LocalDate.now();
        LocalDate weekFrom = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Long sid = student.getStudentId();

        Map<String, Object> todaySum = num(dailyMapper.sumRange(sid, today, today));
        Map<String, Object> weekSum = num(dailyMapper.sumRange(sid, weekFrom, today));
        // 累计不设起点：用「很久以前」代替，避免两套 SQL
        Map<String, Object> totalSum = num(dailyMapper.sumRange(sid, LocalDate.of(1970, 1, 2), today));

        result.put("today", todaySum);
        // 正确率在这里算好，前端三个学段都要用，避免各写一遍除法
        weekSum.put("accuracy", accuracy(weekSum));
        result.put("week", weekSum);
        result.put("total", totalSum);
        result.put("streakDays", streakDays(sid, today));
        result.put("wrongPending", countWrongPending(sid));

        List<EduStudentProgress> rows = progressMapper.selectList(
                new QueryWrapper<EduStudentProgress>().eq("student_id", sid));
        List<Map<String, Object>> modules = new ArrayList<>();
        int mastered = 0;
        for (EduStudentProgress row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moduleCode", row.getModuleCode());
            m.put("progress", row.getProgress() == null ? 0 : row.getProgress());
            m.put("studyMinutes", row.getStudyMinutes());
            m.put("questionCount", row.getQuestionCount());
            m.put("correctCount", row.getCorrectCount());
            m.put("lastStudyTime", row.getLastStudyTime());
            modules.add(m);
            if (row.getProgress() != null && row.getProgress() >= 100) {
                mastered++;
            }
        }
        result.put("modules", modules);
        result.put("modulesMastered", mastered);
        result.put("modulesStarted", modules.size());

        int[] rank = classRank(student, weekFrom, today);
        result.put("classRank", rank[0]);
        result.put("classSize", rank[1]);
        result.put("recommend", recommendModule(modules));
        return result;
    }

    @Override
    public boolean reportStudy(StudyReportDTO dto) {
        if (dto == null) {
            return false;
        }
        EduStudent student = findMyStudent();
        if (student == null) {
            throw new BusinessException("尚未绑定学生信息，无法记录学习数据");
        }
        Long sid = student.getStudentId();
        String account = EduUserContext.requireUserId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // ① 当日汇总：有则累加，无则新建
        EduStudentDaily daily = dailyMapper.selectOne(new QueryWrapper<EduStudentDaily>()
                .eq("student_id", sid).eq("stat_date", today).last("LIMIT 1"));
        if (daily == null) {
            daily = new EduStudentDaily();
            daily.setStudentId(sid);
            daily.setStatDate(today);
            daily.setOwnerUserId(account);
            daily.setStudyCount(0);
            daily.setStudyMinutes(0);
            daily.setQuestionCount(0);
            daily.setCorrectCount(0);
            daily.setStars(0);
            daily.setPoints(0);
        }
        // 每上报一次就算一次学习
        daily.setStudyCount(daily.getStudyCount() + 1);
        daily.setStudyMinutes(daily.getStudyMinutes() + nz(dto.studyMinutes()));
        daily.setQuestionCount(daily.getQuestionCount() + nz(dto.questionCount()));
        daily.setCorrectCount(daily.getCorrectCount() + nz(dto.correctCount()));
        daily.setStars(daily.getStars() + nz(dto.stars()));
        daily.setPoints(daily.getPoints() + pointsOf(dto));
        if (daily.getDailyId() == null) {
            dailyMapper.insert(daily);
        } else {
            dailyMapper.updateById(daily);
        }

        // ② 模块进度：同样有则累加
        String moduleCode = trimToNull(dto.moduleCode());
        if (moduleCode != null) {
            EduStudentProgress progress = progressMapper.selectOne(new QueryWrapper<EduStudentProgress>()
                    .eq("student_id", sid).eq("module_code", moduleCode).last("LIMIT 1"));
            if (progress == null) {
                progress = new EduStudentProgress();
                progress.setStudentId(sid);
                progress.setModuleCode(moduleCode);
                progress.setOwnerUserId(account);
                progress.setProgress(0);
                progress.setStudyMinutes(0);
                progress.setQuestionCount(0);
                progress.setCorrectCount(0);
            }
            // 进度封顶 100：前端进度条与「已攻克」判定都按 100 算
            int next = (progress.getProgress() == null ? 0 : progress.getProgress()) + nz(dto.progressDelta());
            progress.setProgress(Math.min(100, next));
            progress.setStudyMinutes(progress.getStudyMinutes() + nz(dto.studyMinutes()));
            progress.setQuestionCount(progress.getQuestionCount() + nz(dto.questionCount()));
            progress.setCorrectCount(progress.getCorrectCount() + nz(dto.correctCount()));
            progress.setLastStudyTime(now);
            if (progress.getProgressId() == null) {
                progressMapper.insert(progress);
            } else {
                progressMapper.updateById(progress);
            }
        }
        return true;
    }

    /**
     * 取当前账号绑定的学生档案
     *
     * @return 学生档案，未绑定时为 null
     */
    private EduStudent findMyStudent() {
        String account = EduUserContext.currentUserId();
        if (account == null || account.isBlank()) {
            return null;
        }
        return studentMapper.selectOne(new QueryWrapper<EduStudent>()
                .eq("student_account", account).last("LIMIT 1"));
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
     * 把学生档案整理成前端需要的字段
     *
     * @param student 学生档案
     * @return student / className / stage 等字段
     */
    private Map<String, Object> describe(EduStudent student) {
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("studentId", student.getStudentId());
        s.put("studentNo", student.getStudentNo());
        s.put("studentName", student.getStudentName());
        s.put("gradeLevel", student.getStudentGradeLevel());
        s.put("grade", student.getStudentGrade());
        s.put("classId", student.getStudentClassId());
        out.put("student", s);
        String className = null;
        if (student.getStudentClassId() != null) {
            EduClass clazz = classMapper.selectById(student.getStudentClassId());
            className = clazz == null ? null : clazz.getClassName();
        }
        out.put("className", className);
        // 学段统一归一化成 PRIMARY / JUNIOR / SENIOR 再下发：库里可能是中文「小学」
        // （管理员建班时手填），前端按 === 'PRIMARY' 判断学段，原样下发会让小学学生
        // 落到高中版式（实测用户反馈「不显示小学的」）。
        out.put("stage", normalizeLevel(student.getStudentGradeLevel()));
        // 首次登录改密引导：管理员设/重置过初始口令且学生还没自己改过时为 true。
        // 学生端进入 /student 时据此弹一次性引导（改完服务端会置 0，不再弹）。
        out.put("mustChangePassword",
                student.getStudentPwdMustChange() != null && student.getStudentPwdMustChange() == 1);
        return out;
    }

    /**
     * 学段归一化：把中英文写法统一成 PRIMARY / JUNIOR / SENIOR
     *
     * @param level 原始学段值
     * @return 归一化结果；无法识别时原样返回
     */
    private String normalizeLevel(String level) {
        String v = trimToNull(level);
        if (v == null) {
            return null;
        }
        return switch (v.toUpperCase(java.util.Locale.ROOT)) {
            case "PRIMARY", "小学" -> "PRIMARY";
            case "JUNIOR", "MIDDLE", "初中" -> "JUNIOR";
            case "SENIOR", "HIGH", "高中" -> "SENIOR";
            default -> v;
        };
    }

    /**
     * 解析（必要时创建）班级
     *
     * @param dto    绑定信息
     * @param level  归一化后的学段
     * @param userId 当前账号
     * @return 班级 ID；未提供班级名时为 null
     */
    private Long resolveClass(StudentBindDTO dto, String level, String userId) {
        String name = trimToNull(dto.className());
        if (name == null) {
            return null;
        }
        EduClass exists = classMapper.selectOne(new QueryWrapper<EduClass>()
                .eq("class_name", name)
                .eq(level != null, "class_grade_level", level)
                .last("LIMIT 1"));
        if (exists != null) {
            return exists.getClassId();
        }
        EduClass clazz = new EduClass();
        clazz.setClassName(name);
        clazz.setClassCode("CLS-" + System.currentTimeMillis());
        clazz.setClassGradeLevel(level);
        clazz.setClassGrade(trimToNull(dto.grade()));
        clazz.setClassStatus(1);
        clazz.setOwnerUserId(userId);
        classMapper.insert(clazz);
        return clazz.getClassId();
    }

    /**
     * 学号兜底生成（未填学号时用账号生成，保证 NOT NULL 与唯一约束）
     *
     * @param account 登录账号
     * @return 学号
     */
    private String generateStudentNo(String account) {
        // 使用 UUID 替代时间戳，避免高并发下学号重复
        return "AUTO-" + account + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 连续打卡天数：从今天（或昨天）往回数，相邻日期必须连续
     *
     * @param studentId 学生ID
     * @param today     今天
     * @return 连续天数
     */
    private int streakDays(Long studentId, LocalDate today) {
        List<LocalDate> dates = dailyMapper.selectActiveDates(studentId, today, STREAK_LOOKBACK);
        if (dates == null || dates.isEmpty()) {
            return 0;
        }
        // 今天还没学不算断：从昨天起算仍然连续
        LocalDate cursor = dates.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate d : dates) {
            if (d.equals(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else if (d.isBefore(cursor)) {
                break;
            }
        }
        return streak;
    }

    /**
     * 待复习错题数：有效且未掌握
     *
     * @param studentId 学生ID
     * @return 条数
     */
    private long countWrongPending(Long studentId) {
        Long count = wrongQuestionMapper.selectCount(new QueryWrapper<EduWrongQuestion>()
                .eq("wrong_question_student_id", String.valueOf(studentId))
                .eq("wrong_question_status", WRONG_STATUS_VALID)
                .ne("wrong_question_mastery_level", MASTERY_DONE));
        return count == null ? 0 : count;
    }

    /**
     * 班级排名（按区间积分，降序）
     *
     * @param student 学生档案
     * @param from    起始日期
     * @param to      结束日期
     * @return 长度为 2 的数组：[我的名次, 班级人数]；无班级或不参评时为 [0, 0]
     */
    private int[] classRank(EduStudent student, LocalDate from, LocalDate to) {
        if (student.getStudentClassId() == null) {
            return new int[] {0, 0};
        }
        List<EduStudent> classmates = studentMapper.selectList(
                new QueryWrapper<EduStudent>().eq("student_class_id", student.getStudentClassId()));
        if (classmates.isEmpty()) {
            return new int[] {0, 0};
        }
        List<Long> ids = classmates.stream().map(EduStudent::getStudentId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return new int[] {0, 0};
        }
        List<Map<String, Object>> sums = dailyMapper.sumPointsByStudents(ids, from, to);
        Map<Long, Integer> points = new HashMap<>();
        for (Map<String, Object> row : sums) {
            Object sid = row.get("studentId");
            Object pts = row.get("points");
            if (sid instanceof Number n) {
                points.put(n.longValue(), pts instanceof Number p ? p.intValue() : 0);
            }
        }
        int mine = points.getOrDefault(student.getStudentId(), 0);
        // 比我高分的算在我前面；同分并列同名次
        int ahead = 0;
        for (Long id : ids) {
            if (points.getOrDefault(id, 0) > mine) {
                ahead++;
            }
        }
        return new int[] {ahead + 1, ids.size()};
    }

    /**
     * 今日推荐：进度最低且未攻克的模块，全部攻克则给第一个
     *
     * @param modules 模块进度列表
     * @return 模块编码；无模块时为 null
     */
    private String recommendModule(List<Map<String, Object>> modules) {
        String best = null;
        int lowest = Integer.MAX_VALUE;
        for (Map<String, Object> m : modules) {
            int p = ((Number) m.getOrDefault("progress", 0)).intValue();
            if (p >= 100) {
                continue;
            }
            if (p < lowest) {
                lowest = p;
                best = (String) m.get("moduleCode");
            }
        }
        if (best == null && !modules.isEmpty()) {
            best = (String) modules.get(0).get("moduleCode");
        }
        return best;
    }

    /**
     * 把聚合查询结果里的 BigInteger/BigDecimal 统一成 int，避免前端拿到字符串
     *
     * @param raw 聚合行
     * @return 统一成整数的统计表
     */
    private Map<String, Object> num(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            raw = Map.of();
        }
        for (String key : List.of("studyCount", "studyMinutes", "questionCount", "correctCount", "stars", "points")) {
            out.put(key, asInt(raw.get(key)));
        }
        return out;
    }

    /**
     * 正确率（百分比，整数）
     *
     * @param sum 统计表，需含 questionCount / correctCount
     * @return 0-100
     */
    private int accuracy(Map<String, Object> sum) {
        int total = asInt(sum.get("questionCount"));
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(asInt(sum.get("correctCount")) * 100.0 / total);
    }

    /**
     * 积分：优先用上报值；未上报时按题数折算，保证积分不是一直为 0
     *
     * @param dto 上报数据
     * @return 积分
     */
    private int pointsOf(StudyReportDTO dto) {
        int explicit = nz(dto.points());
        if (explicit > 0) {
            return explicit;
        }
        return nz(dto.correctCount()) * 2 + nz(dto.studyMinutes());
    }

    /**
     * null 安全取整
     *
     * @param v 值
     * @return v 或 0
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 任意数值对象转 int
     *
     * @param v 值
     * @return int
     */
    private int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
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