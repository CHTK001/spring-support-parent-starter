package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;
import com.chua.starter.eduai.service.IEduTeacherCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
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
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduTeacherCenterServiceImpl implements IEduTeacherCenterService {

    private final EduTeacherMapper teacherMapper;

    private final EduClassMapper classMapper;

    private final EduStudentMapper studentMapper;

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
