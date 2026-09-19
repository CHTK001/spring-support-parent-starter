package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.service.AiModelCatalogService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 统一能力模型清单控制器。
 *
 * <p>聚合系统全部能力门面的模型清单，按能力标签分组（detect/feature/classify/enhance/ocr/
 * 对话/文本-转为-镜像/文本-转为-视频/嵌入 等），供前端按能力筛选与统一配置使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/ai/models")
@RequiredArgsConstructor
@Tag(name = "AI 统一能力模型清单")
public class AiModelCatalogController {

    /**
     * 统一能力模型清单服务
     */
    private final AiModelCatalogService aiModelCatalogService;

    /**
     * 获取按能力分组的所有模型清单。
     *
     * @return 能力 → 模型 标识 列表
     */
    @Permission("admin")
    @Operation(summary = "按能力分组的所有模型清单", description = "聚合本地 + 云端全部能力门面的模型")
    @GetMapping("/capabilities")
    public Map<String, List<String>> listByCapability() {
        return aiModelCatalogService.listModelsByCapability();
    }

    /**
     * 获取指定能力的模型清单。
     *
     * @param capability 能力标签（如 detect / 特征 / 对话 / 文本-转为-镜像）
     * @return 模型 标识 列表
     */
    @Permission("admin")
    @Operation(summary = "指定能力的模型清单")
    @GetMapping("/capability/{capability}")
    public List<String> listModels(@PathVariable String capability) {
        return aiModelCatalogService.listModels(capability);
    }

    /**
     * 获取全部能力标签。
     *
     * @return 能力标签列表
     */
    @Permission("admin")
    @Operation(summary = "全部能力标签")
    @GetMapping("/capability-names")
    public List<String> listCapabilities() {
        return aiModelCatalogService.listCapabilities();
    }
}
