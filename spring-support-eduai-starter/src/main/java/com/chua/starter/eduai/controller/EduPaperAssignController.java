package com.chua.starter.eduai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduClassMapper;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.mapper.EduPaperAssignMapper;
import com.chua.starter.eduai.mapper.EduPaperMapper;
import com.chua.starter.eduai.model.EduPaperAssign;
import com.chua.starter.eduai.model.dto.PaperAssignDTO;
import com.chua.starter.eduai.model.vo.PaperAssignVO;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 班级试卷布置：老师给班级组卷，班内学生可见。
 *
 * <p>归属一律取自**登录态**，不接受客户端传「我是谁」：</p>
 * <ul>
 *   <li>老师侧：必须是已绑定工号的教师账号；只能布置到
 *       {@code class_owner_user_id = 登录账号} 的**自己的班级**。</li>
 *   <li>学生侧：按自己的 {@code student_class_id} 反查本班试卷，
 *       看不到别的班，也不需要传班级 ID。</li>
 * </ul>
 *
 * <p>为什么不放进 {@code EduTeacherCenterController} / {@code EduStudentCenterController}：
 * 本功能同时被两端使用（老师写、学生读），拆成两处会让「布置/可见」的过滤规则
 * （状态 + 试卷已发布）在两个文件里各写一遍，容易走偏。这里一个文件放同一套规则。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/paper-assign")
@RequiredArgsConstructor
@Tag(name = "班级试卷布置")
public class EduPaperAssignController {

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
     * 试卷：校验试卷存在
     */
    private final EduPaperMapper paperMapper;

    /**
     * 老师把一张试卷布置给一个班级。
     *
     * <p>同一 (试卷, 班级) 重复布置走「复活已有行」，不插新行 —— 唯一键在库上，
     * 先撤后布也不会堆出多行。</p>
     *
     * @param dto 布置信息
     * @return 布置记录标识
     */
    @Operation(summary = "老师布置试卷到班级")
    @PostMapping
    public ReturnResult<Long> assign(@RequestBody PaperAssignDTO dto) {
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
            return ReturnResult.ok(exists.getAssignId());
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
        return ReturnResult.ok(row.getAssignId());
    }

    /**
     * 我（老师）布置过的试卷。
     *
     * @return 布置视图列表
     */
    @Operation(summary = "我布置过的试卷")
    @GetMapping("/mine")
    public ReturnResult<List<PaperAssignVO>> mine() {
        String account = requireTeacher();
        return ReturnResult.ok(assignMapper.selectByTeacher(account));
    }

    /**
     * 撤回布置（软删：状态置 0，学生端立即看不到）。
     *
     * @param id 布置标识
     * @return 是否成功
     */
    @Operation(summary = "撤回布置")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> revoke(@PathVariable Long id) {
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
        return ReturnResult.ok(true);
    }

    /**
     * 学生：我所在班级收到的试卷。
     *
     * <p>按登录态定位自己的档案与班级，不接受客户端传班级 ID；
     * 未绑定档案或未分班时返回空列表（不是错误，学生还没登记而已）。</p>
     *
     * @return 布置视图列表
     */
    @Operation(summary = "我班的试卷（学生端）")
    @GetMapping("/my-class")
    public ReturnResult<List<PaperAssignVO>> myClassPapers() {
        String account = EduUserContext.requireUserId();
        EduStudent student = studentMapper.selectOne(new QueryWrapper<EduStudent>()
                .eq("student_account", account)
                .last("LIMIT 1"));
        if (student == null || student.getStudentClassId() == null) {
            return ReturnResult.ok(List.of());
        }
        return ReturnResult.ok(assignMapper.selectByClass(student.getStudentClassId()));
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
     * 确认该班级属于当前老师（class_owner_user_id = 登录账号）。
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
        if (!account.equals(clazz.getOwnerUserId())) {
            throw new BusinessException("只能给自己名下的班级布置试卷");
        }
        return clazz;
    }
}
