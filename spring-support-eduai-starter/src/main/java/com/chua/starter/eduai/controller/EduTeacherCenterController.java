package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;
import com.chua.starter.eduai.service.IEduTeacherCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教师中心控制器
 *
 * <p>教师端（{@code /teacher}）专用；管理端的教师档案维护仍在
 * {@code /v2/edu/dimension/teachers}。归属取自登录态。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/teacher")
@RequiredArgsConstructor
@Tag(name = "教师中心")
public class EduTeacherCenterController {

    private final IEduTeacherCenterService teacherCenterService;

    /**
     * 当前账号的教师绑定状态
     *
     * @return bound=false 表示还没绑定
     */
    @Operation(summary = "当前账号的教师绑定状态")
    @GetMapping("/me")
    public ReturnResult<Map<String, Object>> me() {
        return ReturnResult.ok(teacherCenterService.me());
    }

    /**
     * 绑定当前登录账号到教师档案
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    @Operation(summary = "绑定教师档案")
    @PostMapping("/bind")
    public ReturnResult<Map<String, Object>> bind(@RequestBody TeacherBindDTO dto) {
        return ReturnResult.ok(teacherCenterService.bind(dto));
    }

    /**
     * 教师工作台数据
     *
     * @return 我带的班级 + 每班人数 + 学生清单
     */
    @Operation(summary = "教师工作台数据")
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview() {
        return ReturnResult.ok(teacherCenterService.overview());
    }
}
