package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.mapper.EduPaperAssignMapper;
import com.chua.starter.eduai.mapper.EduPaperMapper;
import com.chua.starter.eduai.model.EduPaperAssign;
import com.chua.starter.eduai.model.dto.PaperAssignDTO;
import com.chua.starter.eduai.model.vo.PaperAssignVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.service.IEduPaperAssignService;
import com.chua.starter.eduai.service.IEduPaperService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 班级试卷布置服务实现。
 *
 * <p>由 {@code EduPaperAssignController} 收口而来：控制器只留协议层，
 * 「是否已绑定工号 / 是否我的班 / 布置复活 / 学生按班可见」等规则集中在服务层，
 * 不再让控制器直连 6 个 Mapper 各自为政。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduPaperAssignServiceImpl implements IEduPaperAssignService {

    /**
     * 布置记录
     */
    private final EduPaperAssignMapper assignMapper;

    /**
     * 班级：校验「是不是我的班」
     */
    private final EduClassMapper classMapper;

    /**
     * 学生：学生端按登录态定位自己的班级
     */
    private final EduStudentMapper studentMapper;

    /**
     * 教师：确认当前账号已绑定工号
     */
    private final EduTeacherMapper teacherMapper;

    /**
     * 教师班级配置：老师的班级数据范围（V1.14 起布置以此为准）
     */
    private final EduTeacherClassMapper teacherClassMapper;

    /**
     * 试卷：校验试卷存在
     */
    private final EduPaperMapper paperMapper;

    /**
     * 试卷详情：学生端按「布置关系」授权后取详情（不走归属校验）
     */
    private final IEduPaperService paperService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long assign(PaperAssignDTO dto) {
        String account = requireTeacher();
        if (dto == null || dto.paperId() == null || dto.classId() == null) {
            throw new BusinessException("试卷与班级都不能为空");
        }
        requireMyClass(dto.classId(), account);
        if (paperMapper.selectById(dto.paperId()) == null) {
            throw new BusinessException("试卷不存在");
        }
        EduPaperAssign exists = assignMapper.selectOne(new QueryWrapper<EduPaperAssign>()
                .eq("paper_id", dto.paperId())
                .eq("class_id", dto.classId())
                .last("LIMIT 1"));
        if (exists != null) {
            exists.setAssignStatus(1);
            exists.setAssignRemark(dto.remark());
            exists.setTeacherAccount(account);
            exists.setAssignTime(LocalDateTime.now());
            assignMapper.updateById(exists);
            return exists.getAssignId();
        }
        EduPaperAssign row = new EduPaperAssign();
        row.setPaperId(dto.paperId());
        row.setClassId(dto.classId());
        row.setTeacherAccount(account);
        row.setOwnerUserId(account);
        row.setAssignStatus(1);
        row.setAssignRemark(dto.remark());
        row.setAssignTime(LocalDateTime.now());
        assignMapper.insert(row);
        return row.getAssignId();
    }

    @Override
    public List<PaperAssignVO> mine() {
        String account = requireTeacher();
        return assignMapper.selectByTeacher(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revoke(Long id) {
        String account = requireTeacher();
        EduPaperAssign exists = assignMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("布置记录不存在");
        }
        if (!account.equals(exists.getTeacherAccount())) {
            throw new BusinessException("只能撤回自己布置的试卷");
        }
        exists.setAssignStatus(0);
        assignMapper.updateById(exists);
        return true;
    }

    @Override
    public List<PaperAssignVO> myClassPapers() {
        String account = EduUserContext.requireUserId();
        EduStudent student = studentMapper.selectOne(new QueryWrapper<EduStudent>()
                .eq("student_account", account)
                .last("LIMIT 1"));
        if (student == null || student.getStudentClassId() == null) {
            return List.of();
        }
        return assignMapper.selectByClass(student.getStudentClassId());
    }

    @Override
    public PaperVO studentPaperDetail(Long paperId) {
        String account = EduUserContext.requireUserId();
        EduStudent student = studentMapper.selectOne(new QueryWrapper<EduStudent>()
                .eq("student_account", account)
                .last("LIMIT 1"));
        if (student == null || student.getStudentClassId() == null) {
            throw new BusinessException("你还未分班，看不到班级试卷");
        }
        boolean visible = assignMapper.selectByClass(student.getStudentClassId()).stream()
                .anyMatch(row -> Objects.equals(row.getPaperId(), paperId));
        if (!visible) {
            throw new BusinessException("这张试卷没有布置给你的班级");
        }
        return paperService.getPaperDetailUnchecked(paperId);
    }

    /**
     * 确认当前登录账号是已绑定工号的老师，返回其账号。
     *
     * @return 老师登录账号
     */
    private String requireTeacher() {
        String account = EduUserContext.requireUserId();
        EduTeacher teacher = teacherMapper.selectOne(new QueryWrapper<EduTeacher>()
                .eq("teacher_account", account)
                .last("LIMIT 1"));
        if (teacher == null) {
            throw new BusinessException("请先绑定教师工号");
        }
        return account;
    }

    /**
     * 确认该班级已配置给当前老师（V1.14 起按 {@code edu_teacher_class} 判定，
     * 与教师中心「我的班级」同一口径；不再比对 {@code class_owner_user_id}，
     * 否则会出现「列表看得到、布置报无权」或「列表看不到、接口能布置」的不一致）。
     *
     * @param classId 班级标识
     * @param account 老师登录账号
     * @return 班级
     */
    private EduClass requireMyClass(Long classId, String account) {
        EduClass clazz = classMapper.selectById(classId);
        if (clazz == null) {
            throw new BusinessException("班级不存在");
        }
        EduTeacher teacher = teacherMapper.selectOne(new QueryWrapper<EduTeacher>()
                .eq("teacher_account", account)
                .last("LIMIT 1"));
        if (teacher == null) {
            throw new BusinessException("请先绑定教师工号");
        }
        List<Long> myClassIds = teacherClassMapper.selectClassIdsByTeacher(teacher.getTeacherId());
        if (!myClassIds.contains(classId)) {
            throw new BusinessException("该班级未配置给当前老师，请联系管理员在后台「学生管理 → 教师」配置班级");
        }
        return clazz;
    }
}
