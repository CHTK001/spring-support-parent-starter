package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.eduai.model.vo.MarkingSettingVO;
import com.chua.starter.eduai.service.MarkingSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅卷运行时设置控制器（管理员专属）。
 *
 * <p>「系统配置」弹窗的读写通道：维护模型调用重试次数/退避曲线与任意卷识别策略。
 * 与 {@link EduAppConfigController} 共用 {@code edu_app_config} 键值表，
 * 只是这里按「阅卷设置」这一组语义装成一个整体，前端一次读写即可。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/marking-settings")
@RequiredArgsConstructor
@Permission("admin")
@Tag(name = "阅卷设置")
public class EduMarkingSettingController {

    private final MarkingSettingService markingSettingService;

    /**
     * 读取阅卷运行时设置
     *
     * @return 当前生效的设置（DB 覆盖 > YAML 默认）
     */
    @Operation(summary = "读取阅卷运行时设置")
    @GetMapping
    public ReturnResult<MarkingSettingVO> get() {
        return ReturnResult.ok(markingSettingService.resolved());
    }

    /**
     * 保存阅卷运行时设置
     *
     * @param setting 设置
     * @return 保存后重新读出的设置（已按兜底规则纠正）
     */
    @Operation(summary = "保存阅卷运行时设置")
    @PostMapping
    public ReturnResult<MarkingSettingVO> save(@RequestBody MarkingSettingVO setting) {
        markingSettingService.save(setting);
        return ReturnResult.ok(markingSettingService.resolved());
    }
}
