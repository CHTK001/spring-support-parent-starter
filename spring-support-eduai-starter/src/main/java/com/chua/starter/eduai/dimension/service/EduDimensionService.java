package com.chua.starter.eduai.dimension.service;

import com.chua.starter.common.support.constant.CacheConstant;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.*;
import com.chua.starter.eduai.dimension.mapper.*;
import com.chua.starter.eduai.mapper.EduStudentDailyMapper;
import com.chua.starter.eduai.mapper.EduStudentProgressMapper;
import com.chua.starter.eduai.model.EduStudentDaily;
import com.chua.starter.eduai.model.EduStudentProgress;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.common.support.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
            entity.setOwnerUserId(exists.getOwnerUserId());
            classMapper.updateById(entity);
            return entity.getClassId();
        }
        if (entity.getClassStatus() == null) {
            entity.setClassStatus(1);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        classMapper.insert(entity);
        log.info("[EduAi] 新增班级: {} ({})", entity.getClassName(), entity.getClassCode());
        return entity.getClassId();
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
     * @param entity 学生实体
     * @return 学生标识
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveStudent(EduStudent entity) {
        if (!StringUtils.hasText(entity.getStudentNo())) {
            throw new BusinessException("学号必填");
        }
        if (entity.getStudentId() != null && entity.getStudentId() > 0) {
            EduStudent exists = studentMapper.selectById(entity.getStudentId());
            if (exists == null) {
                throw new BusinessException("学生不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            studentMapper.updateById(entity);
            return entity.getStudentId();
        }
        if (entity.getStudentStatus() == null) {
            entity.setStudentStatus(1);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        studentMapper.insert(entity);
        log.info("[EduAi] 新增学生: {} ({})", entity.getStudentName(), entity.getStudentNo());
        return entity.getStudentId();
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
            key = "T(com.chua.starter.eduai.support.util.EduUserContext).ownerScope().ownerPredicate()",
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
