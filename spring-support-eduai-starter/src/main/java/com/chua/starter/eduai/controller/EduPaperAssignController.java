package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.PaperAssignDTO;
import com.chua.starter.eduai.model.vo.PaperAssignVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.service.IEduPaperAssignService;
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

import java.util.List;

/**
 * 班级试卷布置：老师给班级组卷，班内学生可见。
 *
 * <p>归属一律取自**登录态**，不接受客户端传「我是谁」：</p>
 * <ul>
 *   <li>老师侧：必须是已绑定工号的教师账号；只能布置到
 *       后台「学生管理 → 教师 → 配置班级」配置的班级
 *       （{@code edu_teacher_class}，与教师中心数据范围同一口径）。</li>
 *   <li>学生侧：按自己的 {@code student_class_id} 反查本班试卷，
 *       看不到别的班，也不需要传班级 ID。</li>
 * </ul>
 *
 * <p>为什么不放进 {@code EduTeacherCenterController} / {@code EduStudentCenterController}：
 * 本功能同时被两端使用（老师写、学生读），拆成两处会让「布置/可见」的过滤规则
 * （状态 + 试卷已发布）在两个文件里各写一遍，容易走偏。这里一个文件放同一套规则。</p>
 *
 * <p>业务规则（布置复活、数据范围校验、按班可见）在
 * {@link IEduPaperAssignService} 实现，控制器只做协议转换。</p>
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
     * 班级试卷布置服务（布置/撤回/可见性规则）
     */
    private final IEduPaperAssignService paperAssignService;

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
        return ReturnResult.ok(paperAssignService.assign(dto));
    }

    /**
     * 我（老师）布置过的试卷。
     *
     * @return 布置视图列表
     */
    @Operation(summary = "我布置过的试卷")
    @GetMapping("/mine")
    public ReturnResult<List<PaperAssignVO>> mine() {
        return ReturnResult.ok(paperAssignService.mine());
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
        return ReturnResult.ok(paperAssignService.revoke(id));
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
        return ReturnResult.ok(paperAssignService.myClassPapers());
    }

    /**
     * 学生：查看布置给本班的某张试卷详情（含题目）。
     *
     * <p>为什么不能复用 {@code GET /v2/edu/paper/{id}}：那个接口按**试卷归属人**鉴权
     * （{@code EduUserContext.checkReadable}），学生永远不是归属人，实测返回
     * 400 {@code B0500S0001} —— 结果就是学生能看到卷名、点「查看试卷」却是空的。
     * 这里改用**布置关系**授权：只有「已布置给我班 + 该卷已发布」才放行。</p>
     *
     * @param paperId 试卷标识
     * @return 试卷详情（含题目）
     */
    @Operation(summary = "学生：查看布置给本班的试卷详情")
    @GetMapping("/paper/{paperId}")
    public ReturnResult<PaperVO> studentPaperDetail(@PathVariable Long paperId) {
        return ReturnResult.ok(paperAssignService.studentPaperDetail(paperId));
    }
}
