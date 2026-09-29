package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.model.dto.EduChangePasswordDTO;
import com.chua.starter.eduai.model.dto.StudentBindDTO;
import com.chua.starter.eduai.model.dto.StudyReportDTO;
import com.chua.starter.eduai.service.IEduStudentCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
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
 * 学生中心控制器
 *
 * <p>学生端（{@code /student}）专用。与管理端 {@code /v2/edu/dimension/students} 的区别：
 * 这里所有归属都取自登录态，不接受客户端传入学生 ID，因此不存在越权查看他人数据的可能。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/student")
@RequiredArgsConstructor
@Tag(name = "学生中心")
public class EduStudentCenterController {

    private final IEduStudentCenterService studentCenterService;

    /**
     * 档案与口令服务：改密需要复用它的摘要与归属校验，不另起一份实现
     */
    private final EduDimensionService dimensionService;

    /**
     * 当前账号的绑定状态（学生端进入时必须先调这个）
     *
     * @return bound=false 表示还没绑定，前端要弹绑定表单
     */
    @Operation(summary = "当前账号的学生绑定状态")
    @GetMapping("/me")
    public ReturnResult<Map<String, Object>> me() {
        return ReturnResult.ok(studentCenterService.me());
    }

    /**
     * 绑定当前登录账号到学生档案
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    @Operation(summary = "绑定学生档案")
    @PostMapping("/bind")
    public ReturnResult<Map<String, Object>> bind(@RequestBody StudentBindDTO dto) {
        return ReturnResult.ok(studentCenterService.bind(dto));
    }

    /**
     * 学生端首页数据
     *
     * @return 今日/本周/累计统计 + 模块进度 + 班级排名 + 今日推荐
     */
    @Operation(summary = "学生端首页数据")
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview() {
        return ReturnResult.ok(studentCenterService.overview());
    }

    /**
     * 上报一次学习行为
     *
     * @param dto 本次学习数据
     * @return 是否记录成功
     */
    @Operation(summary = "上报学习行为")
    @PostMapping("/study/report")
    public ReturnResult<Boolean> report(@RequestBody StudyReportDTO dto) {
        return ReturnResult.ok(studentCenterService.reportStudy(dto));
    }

    /**
     * 学生自助修改登录口令。
     *
     * <p>放在学生中心而非管理端：登录态中的 {@code userId} 就是学号，
     * 由服务端自行解析，学生改不了别人的口令。管理员重置他人口令走
     * 管理端 {@code /v2/edu/dimension/students/reset-password}。</p>
     *
     * @param dto 旧口令与新口令
     * @return 是否修改成功
     */
    @Operation(summary = "学生自助修改登录口令")
    @PostMapping("/change-password")
    public ReturnResult<Boolean> changePassword(@RequestBody EduChangePasswordDTO dto) {
        return ReturnResult.ok(dimensionService.changeOwnPassword(
                EduUserContext.requireUserId(), dto.oldPassword(), dto.newPassword()));
    }
}
