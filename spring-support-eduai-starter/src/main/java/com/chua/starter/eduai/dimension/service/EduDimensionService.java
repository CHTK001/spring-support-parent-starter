package com.chua.starter.eduai.dimension.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.*;
import com.chua.starter.eduai.dimension.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduDimensionService {

    /** Subjectmapper */
    private final EduSubjectMapper subjectMapper;
    /** Grademapper */
    private final EduGradeMapper gradeMapper;
    /** Classmapper */
    private final EduClassMapper classMapper;
    /** Studentmapper */
    private final EduStudentMapper studentMapper;
    /** Teachermapper */
    private final EduTeacherMapper teacherMapper;

    // ==================== 学科 ====================

    /**
    * 列表主题
    *
    * @param gradeLevel grade级别
    * @return 列表主题的结果
    */
    public List<EduSubject> listSubjects(String gradeLevel) {
        LambdaQueryWrapper<EduSubject> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.like(EduSubject::getSubjectGradeLevels, gradeLevel);
        }
        wrapper.eq(EduSubject::getSubjectStatus, 1)
                .orderByAsc(EduSubject::getSubjectSortOrder);
        return subjectMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存主题
    *
    * @param entity 实体
    * @return 保存主题的结果
    */
    public Long saveSubject(EduSubject entity) {
        if (!StringUtils.hasText(entity.getSubjectCode()) || !StringUtils.hasText(entity.getSubjectName())) {
            throw new BusinessException("学科编码与名称必填");
        }
        if (entity.getSubjectId() != null && entity.getSubjectId() > 0) {
            subjectMapper.updateById(entity);
            return entity.getSubjectId();
        }
        if (entity.getSubjectStatus() == null) {
            entity.setSubjectStatus(1);
        }
        if (entity.getSubjectSortOrder() == null) {
            entity.setSubjectSortOrder(0);
        }
        subjectMapper.insert(entity);
        log.info("[EduAi] 新增学科: {} ({})", entity.getSubjectName(), entity.getSubjectCode());
        return entity.getSubjectId();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 删除主题
    *
    * @param id 标识
    * @return 删除主题的结果
    */
    public boolean deleteSubject(Long id) {
        if (id == null) {
            return false;
        }
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
    public List<EduGrade> listGrades(String gradeLevel) {
        LambdaQueryWrapper<EduGrade> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.eq(EduGrade::getGradeLevel, gradeLevel);
        }
        wrapper.orderByAsc(EduGrade::getGradeOrder);
        return gradeMapper.selectList(wrapper);
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
        LambdaQueryWrapper<EduClass> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.eq(EduClass::getClassGradeLevel, gradeLevel);
        }
        if (teacherId != null) {
            wrapper.eq(EduClass::getClassTeacherId, teacherId);
        }
        wrapper.eq(EduClass::getClassStatus, 1)
                .orderByAsc(EduClass::getClassId);
        return classMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存类
    *
    * @param entity 实体
    * @return 保存类的结果
    */
    public Long saveClass(EduClass entity) {
        if (!StringUtils.hasText(entity.getClassCode()) || !StringUtils.hasText(entity.getClassName())) {
            throw new BusinessException("班级编码与名称必填");
        }
        if (entity.getClassId() != null && entity.getClassId() > 0) {
            classMapper.updateById(entity);
            return entity.getClassId();
        }
        if (entity.getClassStatus() == null) {
            entity.setClassStatus(1);
        }
        classMapper.insert(entity);
        log.info("[EduAi] 新增班级: {} ({})", entity.getClassName(), entity.getClassCode());
        return entity.getClassId();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 删除类
    *
    * @param id 标识
    * @return 删除类的结果
    */
    public boolean deleteClass(Long id) {
        if (id == null) {
            return false;
        }
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
        LambdaQueryWrapper<EduStudent> wrapper = new LambdaQueryWrapper<>();
        if (classId != null) {
            wrapper.eq(EduStudent::getStudentClassId, classId);
        }
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.eq(EduStudent::getStudentGradeLevel, gradeLevel);
        }
        if (StringUtils.hasText(grade)) {
            wrapper.eq(EduStudent::getStudentGrade, grade);
        }
        wrapper.eq(EduStudent::getStudentStatus, 1)
                .orderByAsc(EduStudent::getStudentId);
        return studentMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存Student
    *
    * @param entity 实体
    * @return 保存student的结果
    */
    public Long saveStudent(EduStudent entity) {
        if (!StringUtils.hasText(entity.getStudentNo())) {
            throw new BusinessException("学号必填");
        }
        if (entity.getStudentId() != null && entity.getStudentId() > 0) {
            studentMapper.updateById(entity);
            return entity.getStudentId();
        }
        if (entity.getStudentStatus() == null) {
            entity.setStudentStatus(1);
        }
        studentMapper.insert(entity);
        log.info("[EduAi] 新增学生: {} ({})", entity.getStudentName(), entity.getStudentNo());
        return entity.getStudentId();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 删除Student
    *
    * @param id 标识
    * @return 删除student的结果
    */
    public boolean deleteStudent(Long id) {
        if (id == null) {
            return false;
        }
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
        LambdaQueryWrapper<EduTeacher> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(subjectCode)) {
            wrapper.eq(EduTeacher::getTeacherSubjectCode, subjectCode);
        }
        wrapper.eq(EduTeacher::getTeacherStatus, 1)
                .orderByAsc(EduTeacher::getTeacherId);
        return teacherMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存Teacher
    *
    * @param entity 实体
    * @return 保存teacher的结果
    */
    public Long saveTeacher(EduTeacher entity) {
        if (!StringUtils.hasText(entity.getTeacherNo())) {
            throw new BusinessException("工号必填");
        }
        if (entity.getTeacherId() != null && entity.getTeacherId() > 0) {
            teacherMapper.updateById(entity);
            return entity.getTeacherId();
        }
        if (entity.getTeacherStatus() == null) {
            entity.setTeacherStatus(1);
        }
        teacherMapper.insert(entity);
        log.info("[EduAi] 新增教师: {} ({})", entity.getTeacherName(), entity.getTeacherNo());
        return entity.getTeacherId();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
    * 删除Teacher
    *
    * @param id 标识
    * @return 删除teacher的结果
    */
    public boolean deleteTeacher(Long id) {
        if (id == null) {
            return false;
        }
        return teacherMapper.deleteById(id) > 0;
    }

    /**
    * 全量字典下拉：返回前端需要的 (学段枚举, 学科, 年级) 三元组。
    * 用于考试/试卷表单的级联下拉。
    * @author CH
    * @since 4.0.0
    * @return 获取dropdown的结果
    */
    public DimensionDropdown getDropdown() {
        DimensionDropdown dropdown = new DimensionDropdown();
        dropdown.setGradeLevels(List.of(
                new EnumOption("PRIMARY", "小学"),
                new EnumOption("JUNIOR", "初中"),
                new EnumOption("SENIOR", "高中"),
                new EnumOption("UNIVERSITY", "大学")
        ));
        dropdown.setSubjects(listSubjects(null));
        dropdown.setGrades(listGrades(null));
        return dropdown;
    }

    public static class DimensionDropdown {
        /** Gradelevels */
        private List<EnumOption> gradeLevels;
        /** 主题 */
        private List<EduSubject> subjects;
        /** Grades */
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
        * @return 获取grades的结果
        */
        public List<EduGrade> getGrades() { return grades; }
        /**
        * 设置Grades
        *
        * @param grades grades
        * @author CH
        * @since 4.0.0
        */
        public void setGrades(List<EduGrade> grades) { this.grades = grades; }
    }

    public static class EnumOption {
        /** 值 */
        private String value;
        /** 标签 */
        private String label;
        /** 创建 enum期权 实例 */
        public EnumOption() {}
        /**
        * 创建 enum期权 实例
        * @param value 值
        * @param value 字符串
        * @param label 标签
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
