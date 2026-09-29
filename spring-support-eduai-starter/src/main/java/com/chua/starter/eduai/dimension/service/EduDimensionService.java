package com.chua.starter.eduai.dimension.service;

import com.chua.starter.common.support.constant.CacheConstant;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.eduai.dimension.entity.*;
import com.chua.starter.eduai.dimension.mapper.*;
import com.chua.starter.eduai.mapper.EduStudentDailyMapper;
import com.chua.starter.eduai.mapper.EduStudentProgressMapper;
import com.chua.starter.eduai.model.EduStudentDaily;
import com.chua.starter.eduai.model.EduStudentProgress;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.StudentPasswordSupport;

import java.time.LocalDate;
import com.chua.common.support.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 维度管理服务。
 *
 * <p>统一管理学段 (PRIMARY/JUNIOR/SENIOR/UNIVERSITY)、学科、年级、班级、学生、教师
 * 5 个维度的 CRUD。其中：</p>
 * <ul>
 *   <li>学科 / 班级 / 学生 / 教师 — 本地独立管理（教育域口径不一致）</li>
 *   <li>学生 / 教师 — 通过 {@code studentUserId} / {@code teacherUserId} 关联
 *       {@code spring-support-common-starter} 的用户体系（避免重复存储）</li>
 *   <li>学段 / 年级 — 由内置枚举 + edu_grade 表组合（仅做查询）</li>
 * </ul>
 *
 * <p>只读接口走 {@code @Cacheable}（按 ownerScope + 过滤条件分键），写路径
 * {@code @CacheEvict} 全量清理，避免多用户串缓存。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduDimensionService {

    /**
     * 学科列表缓存名
     */
    private static final String CACHE_SUBJECTS = "edu:dimension:subjects";
    /**
     * 年级列表缓存名
     */
    private static final String CACHE_GRADES = "edu:dimension:grades";
    /**
     * 字典下拉缓存名
     */
    private static final String CACHE_DROPDOWN = "edu:dimension:dropdown";

    /**
     * 管理员代入学生的默认有效期（秒）
     */
    private static final int DEFAULT_IMPERSONATE_SECONDS = 300;

    /**
     * 管理员代入学生的默认可访问路径前缀：只放开学生端，不放开管理端。
     *
     * <p><b>为什么不止 {@code /v2/edu/student} 一个</b>：学生门户首屏除了
     * {@code student/me}、{@code student/overview} 之外，还要拉
     * {@code eduaiApi.funModules}（趣味学习模块列表，见
     * {@code StudentPortalView} 第 185 行）。那个端点在
     * {@code /v2/edu/fun/modules} 下，早期只放开 {@code /v2/edu/student}
     * 一个前缀，导致代入后首屏直接 403「用户信息不存在/请重新登录」——
     * 学生本人登录不受影响（只有临时令牌走路径校验），所以这个缺口很容易漏掉。</p>
     *
     * <p>匹配方式是 {@code startsWith} 前缀匹配（见
     * {@code TemporaryToken.isPathAllowed}），故每个前缀都不带尾部斜杠。</p>
     */
    private static final List<String> STUDENT_SCOPE_PATHS =
            List.of("/v2/edu/student", "/v2/edu/fun");

    /**
     * 学号生成时的最大重试次数。
     *
     * <p>并发建档时两个请求可能算出同一个顺序号，靠「插入前查重 → 递增重试」收敛。
     * 超过该次数说明当年学生量异常或存在并发风暴，宁可报错也不给一个可能撞号的学号。</p>
     */
    private static final int MAX_STUDENT_NO_ATTEMPTS = 20;

    /**
     * Subjectmapper
     */
    private final EduSubjectMapper subjectMapper;
    /**
     * Grademapper
     */
    private final EduGradeMapper gradeMapper;
    /**
     * Classmapper
     */
    private final EduClassMapper classMapper;
    /**
     * Studentmapper
     */
    private final EduStudentMapper studentMapper;
    /**
     * Teachermapper
     */
    private final EduTeacherMapper teacherMapper;

    /**
     * 学生学习进度：删除学生时连带清理，避免留下孤儿行
     */
    private final EduStudentProgressMapper progressMapper;

    /**
     * 学生每日汇总：删除学生时连带清理
     */
    private final EduStudentDailyMapper dailyMapper;

    // ==================== 学科 ====================

    /**
     * 列表主题
     *
     * @param gradeLevel grade级别
     * @return 列表主题的结果
     */
    @Cacheable(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = CACHE_SUBJECTS,
            key = "T(com.chua.starter.eduai.support.util.EduUserContext).ownerScope().ownerPredicate() + ':' + (#gradeLevel ?: '')",
            unless = "#result == null")
    public List<EduSubject> listSubjects(String gradeLevel) {
        return subjectMapper.listSubjects(gradeLevel, EduUserContext.ownerScope());
    }

    /**
     * 保存学科。
     *
     * @param entity 学科实体
     * @return 学科标识
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = {CACHE_SUBJECTS, CACHE_DROPDOWN}, allEntries = true)
    public Long saveSubject(EduSubject entity) {
        if (!StringUtils.hasText(entity.getSubjectCode()) || !StringUtils.hasText(entity.getSubjectName())) {
            throw new BusinessException("学科编码与名称必填");
        }
        if (entity.getSubjectId() != null && entity.getSubjectId() > 0) {
            EduSubject exists = subjectMapper.selectById(entity.getSubjectId());
            if (exists == null) {
                throw new BusinessException("学科不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            subjectMapper.updateById(entity);
            return entity.getSubjectId();
        }
        if (entity.getSubjectStatus() == null) {
            entity.setSubjectStatus(1);
        }
        if (entity.getSubjectSortOrder() == null) {
            entity.setSubjectSortOrder(0);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        subjectMapper.insert(entity);
        log.info("[EduAi] 新增学科: {} ({})", entity.getSubjectName(), entity.getSubjectCode());
        return entity.getSubjectId();
    }

    /**
     * 删除学科。
     *
     * @param id 学科标识
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = {CACHE_SUBJECTS, CACHE_DROPDOWN}, allEntries = true)
    public boolean deleteSubject(Long id) {
        if (id == null) {
            return false;
        }
        EduSubject exists = subjectMapper.selectById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        int rows = subjectMapper.deleteById(id);
        log.info("[EduAi] 删除学科: id={}, rows={}", id, rows);
        return rows > 0;
    }

    // ==================== 年级 ====================

    /**
     * 列表grades
     *
     * @param gradeLevel grade级别
     * @return 列表grades的结果
     */
    @Cacheable(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = CACHE_GRADES,
            key = "T(com.chua.starter.eduai.support.util.EduUserContext).ownerScope().ownerPredicate() + ':' + (#gradeLevel ?: '')",
            unless = "#result == null")
    public List<EduGrade> listGrades(String gradeLevel) {
        return gradeMapper.listGrades(gradeLevel, EduUserContext.ownerScope());
    }

    /**
     * 保存年级。
     *
     * @param entity 年级实体
     * @return 年级标识
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = {CACHE_GRADES, CACHE_DROPDOWN}, allEntries = true)
    public Long saveGrade(EduGrade entity) {
        if (!StringUtils.hasText(entity.getGradeLevel()) || !StringUtils.hasText(entity.getGradeName())) {
            throw new BusinessException("学段与年级名称必填");
        }
        if (entity.getGradeId() != null && entity.getGradeId() > 0) {
            EduGrade exists = gradeMapper.selectById(entity.getGradeId());
            if (exists == null) {
                throw new BusinessException("年级不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            gradeMapper.updateById(entity);
            return entity.getGradeId();
        }
        if (entity.getGradeOrder() == null) {
            entity.setGradeOrder(0);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        gradeMapper.insert(entity);
        log.info("[EduAi] 新增年级: {} ({})", entity.getGradeName(), entity.getGradeLevel());
        return entity.getGradeId();
    }

    /**
     * 删除年级。
     *
     * @param id 年级标识
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = {CACHE_GRADES, CACHE_DROPDOWN}, allEntries = true)
    public boolean deleteGrade(Long id) {
        if (id == null) {
            return false;
        }
        EduGrade exists = gradeMapper.selectById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        int rows = gradeMapper.deleteById(id);
        log.info("[EduAi] 删除年级: id={}, rows={}", id, rows);
        return rows > 0;
    }

    // ==================== 班级 ====================

    /**
     * 列表类
     *
     * @param gradeLevel grade级别
     * @param teacherId teacherid
     * @return 列表类的结果
     */
    public List<EduClass> listClasses(String gradeLevel, Long teacherId) {
        return classMapper.listClasses(gradeLevel, teacherId, EduUserContext.ownerScope());
    }

    /**
     * 保存班级。
     *
     * @param entity 班级实体
     * @return 班级标识
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveClass(EduClass entity) {
        if (!StringUtils.hasText(entity.getClassName())) {
            throw new BusinessException("班级名称必填");
        }
        // 班级编码允许留空：管理端「新增班级」表单一直写着「留空自动生成」，
        // 但这里原先硬要求非空，导致从界面上建班必然失败（学生端绑定那条路径
        // 自己在别处生成了编码，所以只有手工建班才会踩到）。
        if (!StringUtils.hasText(entity.getClassCode())) {
            entity.setClassCode("CLS-" + System.currentTimeMillis());
        }
        if (entity.getClassId() != null && entity.getClassId() > 0) {
            EduClass exists = classMapper.selectById(entity.getClassId());
            if (exists == null) {
                throw new BusinessException("班级不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            checkClassNameUnique(entity.getClassName().trim(), entity.getClassId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            classMapper.updateById(entity);
            return entity.getClassId();
        }
        if (entity.getClassStatus() == null) {
            entity.setClassStatus(1);
        }
        checkClassNameUnique(entity.getClassName().trim(), null);
        entity.setOwnerUserId(EduUserContext.requireUserId());
        classMapper.insert(entity);
        log.info("[EduAi] 新增班级: {} ({})", entity.getClassName(), entity.getClassCode());
        return entity.getClassId();
    }

    /**
     * 班级名称业务唯一校验。
     *
     * <p>表上有唯一索引 {@code uk_edu_class_name} 兜底，但那只会抛一条看不懂的
     * {@code Duplicate entry}；这里先查一次并给出人话，编辑态要排除自己。</p>
     *
     * @param className  班级名（已 trim）
     * @param excludeId  编辑时要排除的班级标识，新增传 null
     */
    private void checkClassNameUnique(String className, Long excludeId) {
        Long same = classMapper.selectCount(new QueryWrapper<EduClass>()
                .eq("class_name", className)
                .ne(excludeId != null, "class_id", excludeId));
        if (same != null && same > 0) {
            throw new BusinessException("班级名称「" + className + "」已存在，请换一个名称");
        }
    }

    /**
     * 删除班级。
     *
     * @param id 班级标识
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteClass(Long id) {
        if (id == null) {
            return false;
        }
        EduClass exists = classMapper.selectById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        int rows = classMapper.deleteById(id);
        log.info("[EduAi] 删除班级: id={}, rows={}", id, rows);
        return rows > 0;
    }

    // ==================== 学生 ====================

    /**
     * 列表students
     *
     * @param classId 类标识
     * @param gradeLevel grade级别
     * @param grade grade
     * @return 列表students的结果
     */
    public List<EduStudent> listStudents(Long classId, String gradeLevel, String grade) {
        return studentMapper.listStudents(classId, gradeLevel, grade, EduUserContext.ownerScope());
    }

    /**
 * 保存学生。
 *
 * <p><b>学号一律由系统生成，不接受客户端传入</b>：学号是学生端的登录账号
 * （令牌里的 {@code userId} 就是学号），允许手填会带来撞号、
 * 「以为自己改学号结果登不上」等问题。编辑已有学生时，
 * 请求里带的学号会被忽略，一律以库中为准。</p>
 *
 * @param entity 学生实体
 * @return 学生标识
 */
    @Transactional(rollbackFor = Exception.class)
    public Long saveStudent(EduStudent entity) {
        // 家长手机号非必填（没有也能建档，只是学生端不能用手机号登录），但填了就要合法：
        // 后面要拿它当登录凭据，格式错的号码会让家长永远登不进来且查不出原因。
        String phone = normalizeParentPhone(entity.getStudentParentPhone());
        entity.setStudentParentPhone(phone);
        if (entity.getStudentId() != null && entity.getStudentId() > 0) {
            EduStudent exists = studentMapper.selectById(entity.getStudentId());
            if (exists == null) {
                throw new BusinessException("学生不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            // 学号是登录身份，编辑时锁定：前端可能把整行数据回传，
            // 不锁定就会让「改个姓名」把学号也改了，学生立刻登不进来。
            entity.setStudentNo(exists.getStudentNo());
            entity.setStudentAccount(exists.getStudentAccount());
            applyPassword(entity, exists);
            studentMapper.updateById(entity);
            return entity.getStudentId();
        }
        if (entity.getStudentStatus() == null) {
            entity.setStudentStatus(1);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        // 学号由系统生成并保证唯一（库上有 UNIQUE 约束，并发下靠重试规避撞号）
        String studentNo = generateUniqueStudentNo();
        entity.setStudentNo(studentNo);
        // 登录账号默认取学号：学生端令牌里的 userId 就是学号，
        // 两个字段必须一致，否则登录成功了学生端却定位不到自己的档案。
        entity.setStudentAccount(studentNo);
        applyPassword(entity, null);
        studentMapper.insert(entity);
        log.info("[EduAi] 新增学生: {} ({}) 手机号={}", entity.getStudentName(), studentNo,
                phone == null ? "未填" : "已填");
        return entity.getStudentId();
    }

    /**
     * 生成唯一学号。
     *
     * <p>格式：入学年份（4 位）+ 年级/学段序号（2 位）+ 顺序号（3 位），
     * 例如 {@code 2026010001}。顺序号按「当年已存在的最大号 +1」推进，
     * 并用库上 {@code UNIQUE} 约束 + 插入前查重双保险——
     * 唯一键才是并发下的最终保证，查重只是为了让报错更友好。</p>
     *
     * @return 唯一学号
     */
    private String generateUniqueStudentNo() {
        int year = LocalDate.now().getYear();
        String prefix = String.valueOf(year);
        var existing = studentMapper.selectMaxStudentNoByPrefix(prefix);
        int sequence = 1;
        if (StringUtils.hasText(existing) && existing.length() >= prefix.length() + 3) {
            try {
                sequence = Integer.parseInt(existing.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("[EduAi] 学号 {} 非规范格式，顺序号从 1 重新开始", existing);
                sequence = 1;
            }
        }
        // 顺序号溢出（当年超过 999 人）时进位到更长后缀，避免生成重复学号
        for (int attempt = 0; attempt < MAX_STUDENT_NO_ATTEMPTS; attempt++) {
            String candidate = prefix + String.format("%06d", sequence);
            if (!studentMapper.existsByStudentNo(candidate)) {
                return candidate;
            }
            sequence++;
        }
        throw new BusinessException("学号生成失败，请稍后重试");
    }

    /**
     * 管理员重置学生口令。
     *
     * <p>与「学生自助改密」分开：管理员侧不需要校验旧口令，
     * 但要校验归属，避免越权重置他人学生口令。</p>
     *
     * @param studentId 学生标识
     * @param newPassword 新口令明文
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resetStudentPassword(Long studentId, String newPassword) {
        if (studentId == null) {
            throw new BusinessException("学生不能为空");
        }
        EduStudent exists = studentMapper.selectById(studentId);
        if (exists == null) {
            throw new BusinessException("学生不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        StudentPasswordSupport.validate(newPassword);

        var salt = StudentPasswordSupport.newSalt();
        exists.setStudentPwdSalt(salt);
        exists.setStudentPassword(StudentPasswordSupport.hash(newPassword, salt));
        studentMapper.updateById(exists);
        log.info("[EduAi] 管理员重置学生口令 - 学号: {}", exists.getStudentNo());
        return true;
    }

    /**
     * 学生自助修改口令。
     *
     * <p>必须校验旧口令：学生端令牌本身可能因代入、令牌泄漏等原因被他人持有，
     * 不校验旧口令等于任何拿到令牌的人都能改密码。</p>
     *
     * @param studentNo 学号（登录态 userId，即学号）
     * @param oldPassword 旧口令明文
     * @param newPassword 新口令明文
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean changeOwnPassword(String studentNo, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(studentNo)) {
            throw new BusinessException("登录态已失效，请重新登录");
        }
        if (StringUtils.hasText(oldPassword)
                && StringUtils.hasText(newPassword)
                && oldPassword.equals(newPassword)) {
            throw new BusinessException("新口令不能与旧口令相同");
        }
        StudentPasswordSupport.validate(newPassword);

        EduStudent student = studentMapper.selectByStudentNo(studentNo);
        if (student == null) {
            throw new BusinessException("学生档案不存在");
        }
        if (StringUtils.hasText(oldPassword)
                && !StudentPasswordSupport.matches(oldPassword,
                student.getStudentPwdSalt(), student.getStudentPassword())) {
            log.warn("学生自助改密旧口令不匹配 - 学号: {}", studentNo);
            throw new BusinessException("原口令不正确");
        }

        var salt = StudentPasswordSupport.newSalt();
        student.setStudentPwdSalt(salt);
        student.setStudentPassword(StudentPasswordSupport.hash(newPassword, salt));
        studentMapper.updateById(student);
        log.info("[EduAi] 学生自助修改口令 - 学号: {}", studentNo);
        return true;
    }

    /**
     * 管理员代入指定学生：签发一个短期、只读、限定路径的临时令牌。
     *
     * <p>用途是排查「这个学生看到的是什么」：管理员不必索要学生密码，
     * 也不必真的用学生账号登录，避免污染学生的学习记录。</p>
     *
     * <p>默认只允许访问学生端接口（{@link #STUDENT_SCOPE_PATHS}）。
     * 开放管理端路径等于把管理员权限借出去，因此必须由调用方显式传入才会生效。</p>
     *
     * @param studentId     学生标识
     * @param expireSeconds 有效期（秒）；为空用 {@value #DEFAULT_IMPERSONATE_SECONDS} 秒
     * @param allowedUrls   允许访问的路径前缀；为空时收窄到学生端路径
     * @return 含临时令牌的结果；无权、档案不存在或签发失败时返回对应错误码
     */
    public LoginAuthResult impersonateStudent(Long studentId, Integer expireSeconds, List<String> allowedUrls) {
        if (!EduUserContext.isAdmin()) {
            throw new BusinessException("只有管理员可以代入学生身份");
        }
        if (studentId == null) {
            throw new BusinessException("学生不能为空");
        }
        EduStudent student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        // 管理员可越过归属过滤，但归属字段仍要带进令牌，
        // 否则学生端接口按 owner 过滤时会查不到这个学生的数据
        int seconds = expireSeconds == null || expireSeconds <= 0
                ? DEFAULT_IMPERSONATE_SECONDS
                : expireSeconds;
        List<String> scope = allowedUrls == null || allowedUrls.isEmpty()
                ? STUDENT_SCOPE_PATHS
                : allowedUrls;

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("studentId", student.getStudentId());
        ext.put("studentNo", student.getStudentNo());
        ext.put("studentName", student.getStudentName());
        ext.put("studentClassId", student.getStudentClassId());
        ext.put("studentGradeLevel", student.getStudentGradeLevel());
        ext.put("studentGrade", student.getStudentGrade());
        ext.put("accountType", "student");

        var target = new UserResult();
        target.setUserId(student.getStudentNo());
        target.setUsername(student.getStudentNo());
        target.setNickName(StringUtils.defaultString(student.getStudentName(), student.getStudentNo()));
        target.setPhone(student.getStudentParentPhone());
        target.setLoginType("student-impersonate");
        target.setRoles(Set.of(AuthConstant.STUDENT));
        target.setPermission(Collections.emptySet());
        target.setExt(ext);
        // 归属字段随令牌带过去：学生端查进度/错题时按 owner 过滤，缺了就查不到
        if (StringUtils.hasText(student.getOwnerUserId())) {
            ext.put("ownerUserId", student.getOwnerUserId());
        }
        return AuthClientExecute.getInstance().createTemporaryTokenFor(target, seconds, scope);
    }

    /**
     * 处理登录口令：把表单里的明文转成摘要加盐写回实体。
     *
     * <p>更新场景下表单没提交新口令时，必须把库中既有的摘要与盐原样带回：
     * 否则管理员改个姓名就会把学生的登录口令清空，且清空后没有任何提示。</p>
     *
     * @param entity 本次提交的实体
     * @param exists 库中既有档案；新增时为 {@code null}
     */
    private void applyPassword(EduStudent entity, EduStudent exists) {
        String raw = entity.getStudentPwdInput();
        if (!StringUtils.hasText(raw)) {
            if (exists != null) {
                entity.setStudentPassword(exists.getStudentPassword());
                entity.setStudentPwdSalt(exists.getStudentPwdSalt());
            }
            return;
        }
        StudentPasswordSupport.validate(raw);
        String salt = StudentPasswordSupport.newSalt();
        entity.setStudentPwdSalt(salt);
        entity.setStudentPassword(StudentPasswordSupport.hash(raw, salt));
        entity.setStudentPwdInput(null);
    }

    /**
     * 归一化并校验家长手机号。
     *
     * <p>去掉分隔符（空格/短横线）后要求是 11 位、以 1 开头。空值（含全空格）
     * 视为「未填」返回 null，而不是报错 —— 家长手机号是可选增强项，
     * 不该因为没填就建不了档。</p>
     *
     * @param raw 表单原值
     * @return 归一化后的 11 位号码；未填返回 null
     */
    private String normalizeParentPhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[\\s\\-()]", "");
        if (!digits.matches("1\\d{10}")) {
            throw new BusinessException("家长手机号格式不对，应为 11 位手机号");
        }
        return digits;
    }

    /**
     * 删除学生。
     *
     * @param id 学生标识
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStudent(Long id) {
        if (id == null) {
            return false;
        }
        EduStudent exists = studentMapper.selectById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        // 连带清掉该学生的学习数据：进度与每日汇总都按 student_id 关联，
        // 不一起删会留下查不到主人的孤儿行（验收时确实留下过 3 条进度 + 1 条日汇总）。
        progressMapper.delete(new QueryWrapper<EduStudentProgress>().eq("student_id", id));
        dailyMapper.delete(new QueryWrapper<EduStudentDaily>().eq("student_id", id));
        return studentMapper.deleteById(id) > 0;
    }

    // ==================== 教师 ====================

    /**
     * 列表teachers
     *
     * @param subjectCode 主题编码
     * @return 列表teachers的结果
     */
    public List<EduTeacher> listTeachers(String subjectCode) {
        return teacherMapper.listTeachers(subjectCode, EduUserContext.ownerScope());
    }

    /**
     * 保存教师。
     *
     * @param entity 教师实体
     * @return 教师标识
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveTeacher(EduTeacher entity) {
        if (!StringUtils.hasText(entity.getTeacherNo())) {
            throw new BusinessException("工号必填");
        }
        if (entity.getTeacherId() != null && entity.getTeacherId() > 0) {
            EduTeacher exists = teacherMapper.selectById(entity.getTeacherId());
            if (exists == null) {
                throw new BusinessException("教师不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            teacherMapper.updateById(entity);
            return entity.getTeacherId();
        }
        if (entity.getTeacherStatus() == null) {
            entity.setTeacherStatus(1);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        teacherMapper.insert(entity);
        log.info("[EduAi] 新增教师: {} ({})", entity.getTeacherName(), entity.getTeacherNo());
        return entity.getTeacherId();
    }

    /**
     * 删除教师。
     *
     * @param id 教师标识
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeacher(Long id) {
        if (id == null) {
            return false;
        }
        EduTeacher exists = teacherMapper.selectById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        return teacherMapper.deleteById(id) > 0;
    }

    /**
     * 全量字典下拉：返回前端需要的 (学段枚举, 学科, 年级) 三元组。
     * 用于考试/试卷表单的级联下拉。
     * @author CH
     * @since 4.0.0
     * @return 获取dropdown的结果
     */
    @Cacheable(cacheManager = CacheConstant.REDIS_CACHE_MINUTES,
            cacheNames = CACHE_DROPDOWN,
            key = "T(com.chua.starter.eduai.support.util.EduUserContext).ownerScope().ownerPredicate() + ':'",
            unless = "#result == null")
    public DimensionDropdown getDropdown() {
        // 学科 / 年级并行查询，缩短下拉首屏
        java.util.concurrent.CompletableFuture<List<EduSubject>> subjectsF =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> listSubjects(null));
        java.util.concurrent.CompletableFuture<List<EduGrade>> gradesF =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> listGrades(null));

        DimensionDropdown dropdown = new DimensionDropdown();
        dropdown.setGradeLevels(List.of(
                new EnumOption("PRIMARY", "小学"),
                new EnumOption("JUNIOR", "初中"),
                new EnumOption("SENIOR", "高中"),
                new EnumOption("UNIVERSITY", "大学")
        ));
        try {
            dropdown.setSubjects(subjectsF.get(3, java.util.concurrent.TimeUnit.SECONDS));
            dropdown.setGrades(gradesF.get(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dropdown.setSubjects(listSubjects(null));
            dropdown.setGrades(listGrades(null));
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            log.warn("[EduAi] dropdown 并行查询回退串行: {}", e.getMessage());
            dropdown.setSubjects(subjectsF.isDone() && !subjectsF.isCompletedExceptionally()
                    ? subjectsF.getNow(null) : listSubjects(null));
            dropdown.setGrades(gradesF.isDone() && !gradesF.isCompletedExceptionally()
                    ? gradesF.getNow(null) : listGrades(null));
        }
        return dropdown;
    }

    /**
     * 字典下拉数据，封装学段、学科和年级选项。
     */
    public static class DimensionDropdown {
        /**
         * Gradelevels
         */
        private List<EnumOption> gradeLevels;
        /**
         * 主题
         */
        private List<EduSubject> subjects;
        /**
         * Grades
         */
        private List<EduGrade> grades;
        /**
         * 获取grade级别
         *
         * @return 获取grade级别的结果
         */
        public List<EnumOption> getGradeLevels() { return gradeLevels; }
        /**
         * 设置grade级别
         *
         * @param gradeLevels grade级别
         */
        public void setGradeLevels(List<EnumOption> gradeLevels) { this.gradeLevels = gradeLevels; }
        /**
         * 获取主题
         *
         * @return 获取主题的结果
         */
        public List<EduSubject> getSubjects() { return subjects; }
        /**
         * 设置主题
         *
         * @param subjects 主题
         */
        public void setSubjects(List<EduSubject> subjects) { this.subjects = subjects; }
        /**
         * 获取Grades
         *
         * @return 获取Grades的结果
         */
        public List<EduGrade> getGrades() { return grades; }
        /**
         * 设置grades
         *
         * @param grades grades
         * @author CH
         * @since 4.0.0
         */
        public void setGrades(List<EduGrade> grades) { this.grades = grades; }
    }

    /**
     * 字典下拉选项，封装枚举值和显示文本。
     */
    public static class EnumOption {
        /**
         * 值
         */
        private String value;
        /**
         * 标签
         */
        private String label;
        /**
         * 创建空的字典下拉选项。
         */
        public EnumOption() {
        }
        /**
         * 创建字典下拉选项。
         *
         * @param value 选项值
         * @param label 显示文本
         */
        public EnumOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
        /**
         * 获取值
         *
         * @return 获取值的结果
         */
        public String getValue() { return value; }
        /**
         * 设置值
         *
         * @param value 值
         */
        public void setValue(String value) { this.value = value; }
        /**
         * 获取标签
         *
         * @return 获取标签的结果
         */
        public String getLabel() { return label; }
        /**
         * 设置标签
         *
         * @param label 标签
         */
        public void setLabel(String label) { this.label = label; }
    }
}
