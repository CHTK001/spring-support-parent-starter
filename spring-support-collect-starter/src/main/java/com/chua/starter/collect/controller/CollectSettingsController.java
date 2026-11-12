package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.service.CollectSettingsService;
import com.chua.starter.collect.vo.CollectSettingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 采集平台运行参数接口。
 *
 * <p>把 yml 里的 {@code plugin.collect.*} 暴露成可读可写的配置项：GET 返回全部定义与当前值，
 * PUT 保存并立即生效，POST /reset 恢复 yml 缺省。写值失败由全局异常处理转成 400，
 * 控制器不重复包装。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "数据采集-运行参数")
@RestController
@RequestMapping("/v2/collect/settings")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectSettingsController {

    /**
     * 运行参数服务。
     */
    private final CollectSettingsService settingsService;

    /**
     * 全部运行参数（含类型、范围、缺省值与是否重启生效）。
     *
     * @return 参数项列表
     */
    @Operation(summary = "运行参数列表")
    @GetMapping("/list")
    public ReturnResult<List<CollectSettingVO>> list() {
        return ReturnResult.ok(settingsService.listSettings());
    }

    /**
     * 保存运行参数，只传要改的键；校验通过后写库并立即生效。
     *
     * @param values 配置键到值
     * @return 保存后的全部参数项
     */
    @Operation(summary = "保存运行参数")
    @PutMapping
    public ReturnResult<List<CollectSettingVO>> save(@RequestBody Map<String, String> values) {
        return ReturnResult.ok(settingsService.saveSettings(values));
    }

    /**
     * 恢复缺省：传键则只恢复这些键，不传或传空数组则恢复全部。
     *
     * @param keys 要恢复的配置键
     * @return 恢复后的全部参数项
     */
    @Operation(summary = "恢复缺省运行参数")
    @PostMapping("/reset")
    public ReturnResult<List<CollectSettingVO>> reset(@RequestBody(required = false) List<String> keys) {
        return ReturnResult.ok(settingsService.resetSettings(keys));
    }
}
