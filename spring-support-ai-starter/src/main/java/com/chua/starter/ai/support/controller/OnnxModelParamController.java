package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.deeplearning.support.engine.ModelParams;
import com.chua.starter.ai.support.configuration.client.OnnxModelParamService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ONNX 模型级运行参数接口。
 *
 * <p>用于在后台按模型配置推理参数：</p>
 * <ul>
 *   <li>{@code device} —— 推理设备（auto / cpu / gpu），取代全局 {@code deeplearning.device}，
 *       使同一进程内不同模型可使用不同设备；</li>
 *   <li>{@code threshold} / {@code iouThreshold} —— 置信度与 NMS 阈值；</li>
 *   <li>模型自有额外参数 —— 由各 Translator 声明读取，如 inputSize、candidates 等。</li>
 * </ul>
 *
 * <p>参数持久化在 {@code sys_ai_global_setting}（group=onnx-model，field=params:模型标识），
 * 应用启动时自动回填。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/onnx/model-params")
@RequiredArgsConstructor
@Tag(name = "ONNX 模型参数")
public class OnnxModelParamController {

    /**
     * 模型参数服务。
     */
    private final OnnxModelParamService paramService;

    /**
     * 查询某模型当前生效的参数。
     *
     * @param modelId 模型标识
     * @return 参数表
     */
    @GetMapping("/{modelId}")
    @Permission("admin")
    @Operation(summary = "查询模型参数")
    public ReturnResult<?> get(@PathVariable String modelId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("params", paramService.current(modelId));
        result.put("schema", paramService.commonParamSchema());
        result.put("deviceOptions", OnnxModelParamService.DEVICE_OPTIONS);
        result.put("resolvedDevice", ModelParams.resolveDevice(modelId, null));
        var hardware = paramService.hardwareOf(modelId);
        result.put("recommended", hardware != null && hardware.recommended());
        return ReturnResult.success(result);
    }

    /**
     * 覆盖保存某模型的参数。
     *
     * @param modelId 模型标识
     * @param body    参数表
     * @return 保存后的参数
     */
    @PutMapping("/{modelId}")
    @Permission("admin")
    @Operation(summary = "保存模型参数（覆盖）")
    public ReturnResult<?> save(@PathVariable String modelId, @RequestBody Map<String, Object> body) {
        return ReturnResult.success(paramService.save(modelId, body));
    }

    /**
     * 合并保存某模型的参数。
     *
     * @param modelId 模型标识
     * @param body    参数表
     * @return 合并后的参数
     */
    @PostMapping("/{modelId}/merge")
    @Permission("admin")
    @Operation(summary = "保存模型参数（合并）")
    public ReturnResult<?> merge(@PathVariable String modelId, @RequestBody Map<String, Object> body) {
        return ReturnResult.success(paramService.merge(modelId, body));
    }

    /**
     * 清除某模型的全部参数。
     *
     * @param modelId 模型标识
     * @return 清除结果
     */
    @DeleteMapping("/{modelId}")
    @Permission("admin")
    @Operation(summary = "重置模型参数")
    public ReturnResult<?> reset(@PathVariable String modelId) {
        paramService.reset(modelId);
        return ReturnResult.success(true);
    }

    /**
     * 列出所有已配置参数的模型标识。
     *
     * @return 模型标识列表
     */
    @GetMapping("/configured")
    @Permission("admin")
    @Operation(summary = "列出已配置参数的模型")
    public ReturnResult<List<String>> configured() {
        return ReturnResult.success(paramService.configuredModels());
    }

    /**
     * 查询通用参数键说明。
     *
     * @return 键 → 说明
     */
    @GetMapping("/schema")
    @Permission("admin")
    @Operation(summary = "查询通用参数说明")
    public ReturnResult<?> schema() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("common", paramService.commonParamSchema());
        result.put("deviceOptions", OnnxModelParamService.DEVICE_OPTIONS);
        return ReturnResult.success(result);
    }

    /**
     * 查询本机 GPU 可用情况（供前端提示 device 该怎么选）。
     *
     * @return GPU 探测结果
     */
    @GetMapping("/device-status")
    @Permission("admin")
    @Operation(summary = "查询本机 GPU 状态")
    public ReturnResult<?> deviceStatus(@RequestParam(required = false) String modelId) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean usable = com.chua.deeplearning.support.engine.DeviceSelector.isGpuUsable();
        result.put("gpuUsable", usable);
        result.put("deviceOptions", OnnxModelParamService.DEVICE_OPTIONS);
        if (modelId != null && !modelId.isBlank()) {
            result.put("modelId", modelId);
            result.put("resolvedDevice", ModelParams.resolveDevice(modelId, null));
        }
        return ReturnResult.success(result);
    }
}
