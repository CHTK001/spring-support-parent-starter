package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
* AI 模型定义控制器，提供模型的增删改查和连通性测试。
*
* @author CH
* @since 4.0.0
 */
@RestController
@RequestMapping("/v2/models")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 模型定义")
public class AiModelController {

    private final AiModelService modelService;

    /**
     * 列出。
     *
     * @return 结果列表，无数据时为空列表
     */
    @Permission("admin")
    @Operation(summary = "查询所有模型")
    @GetMapping
    public List<AiModelDTO> list() {
        return modelService.list();
    }

    /**
     * 创建。
     *
     * @param model 模型，不允许为 null
     * @return Ai模型DTO 对象
     */
    @Permission("admin")
    @Operation(summary = "新建模型")
    @PostMapping
    public AiModelDTO create(@RequestBody AiModelDTO model) {
        return modelService.create(model);
    }

    @Permission("admin")
    @Operation(summary = "更新模型")
    @PutMapping("/{modelId}")
    public AiModelDTO update(@Parameter(description = "模型主键") @PathVariable Long modelId,
                             @RequestBody AiModelDTO model) {
        return modelService.update(modelId, model);
    }

    /**
     * 删除。
     *
     * @param modelId 模型ID，不允许为 null
     */
    @Permission("admin")
    @Operation(summary = "删除模型")
    @DeleteMapping("/{modelId}")
    public void delete(@Parameter(description = "模型主键") @PathVariable Long modelId) {
        modelService.delete(modelId);
    }

    /**
     * 测试：Latency。
     *
     * @param modelId 模型ID，不允许为 null
     * @return Ai模型DTO 对象
     */
    @Permission("admin")
    @Operation(summary = "测试模型连通性延迟（非本地模型）")
    @PostMapping("/{modelId}/test-latency")
    public AiModelDTO testLatency(@Parameter(description = "模型主键") @PathVariable Long modelId) {
        return modelService.testLatency(modelId);
    }
}
