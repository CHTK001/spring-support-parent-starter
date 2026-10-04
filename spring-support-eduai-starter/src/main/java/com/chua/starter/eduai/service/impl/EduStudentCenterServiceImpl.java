package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.mapper.EduStudentDailyMapper;
import com.chua.starter.eduai.mapper.EduStudentProgressMapper;
import com.chua.starter.eduai.mapper.EduWrongQuestionMapper;
import com.chua.starter.eduai.model.EduStudentDaily;
import com.chua.starter.eduai.model.EduStudentProgress;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.StudentBindDTO;
import com.chua.starter.eduai.model.dto.StudyReportDTO;
import com.chua.starter.eduai.service.IEduStudentCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        String no = trimToNull(dto.getStudentNo());
        String name = trimToNull(dto.getStudentName());
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
        String level = normalizeLevel(dto.getGradeLevel());
        if (level != null) {
            student.setStudentGradeLevel(level);
        }
        if (trimToNull(dto.getGrade()) != null) {
            student.setStudentGrade(dto.getGrade().trim());
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
        daily.setStudyMinutes(daily.getStudyMinutes() + nz(dto.getStudyMinutes()));
        daily.setQuestionCount(daily.getQuestionCount() + nz(dto.getQuestionCount()));
        daily.setCorrectCount(daily.getCorrectCount() + nz(dto.getCorrectCount()));
        daily.setStars(daily.getStars() + nz(dto.getStars()));
        daily.setPoints(daily.getPoints() + pointsOf(dto));
        if (daily.getDailyId() == null) {
            dailyMapper.insert(daily);
        } else {
            dailyMapper.updateById(daily);
        }

        // ② 模块进度：同样有则累加
        String moduleCode = trimToNull(dto.getModuleCode());
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
            int next = (progress.getProgress() == null ? 0 : progress.getProgress()) + nz(dto.getProgressDelta());
            progress.setProgress(Math.min(100, next));
            progress.setStudyMinutes(progress.getStudyMinutes() + nz(dto.getStudyMinutes()));
            progress.setQuestionCount(progress.getQuestionCount() + nz(dto.getQuestionCount()));
            progress.setCorrectCount(progress.getCorrectCount() + nz(dto.getCorrectCount()));
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
        out.put("stage", student.getStudentGradeLevel());
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
        String name = trimToNull(dto.getClassName());
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
        clazz.setClassGrade(trimToNull(dto.getGrade()));
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
        return "AUTO-" + account + "-" + System.currentTimeMillis();
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
        int explicit = nz(dto.getPoints());
        if (explicit > 0) {
            return explicit;
        }
        return nz(dto.getCorrectCount()) * 2 + nz(dto.getStudyMinutes());
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
