package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.eduai.service.EduAppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用配置控制器（管理员专属）。
 *
 * <p>键值开关的读写通道，供管理页「配置管理」使用
 * （如题库页的「老师上传自动通过」开关）。键名见
 * {@link EduAppConfigService} 常量，值一律字符串。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/app-config")
@RequiredArgsConstructor
@Permission("admin")
@Tag(name = "应用配置")
public class EduAppConfigController {

    private final EduAppConfigService appConfigService;

    /**
     * 读取配置值
     *
     * @param key 配置键
     * @return 配置值；未配置时为 null
     */
    @Operation(summary = "读取配置")
    @GetMapping("/{key}")
    public ReturnResult<String> get(@PathVariable String key) {
        return ReturnResult.ok(appConfigService.get(key));
    }

    /**
     * 写入配置值
     *
     * @param key  配置键
     * @param body 配置值（{value: "..."}）
     * @return 是否成功
     */
    @Operation(summary = "写入配置")
    @PostMapping("/{key}")
    public ReturnResult<Boolean> set(@PathVariable String key, @RequestBody AppConfigValueDTO body) {
        if (body == null || body.value() == null) {
            return ReturnResult.ok(false);
        }
        appConfigService.set(key, body.value());
        return ReturnResult.ok(true);
    }

    /**
     * 配置写入请求体
     *
     * @param value 配置值
     */
    public record AppConfigValueDTO(String value) {
    }
}
