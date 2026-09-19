package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.manage.api.ApiSkillController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 技能目录控制器，为 机器人 绑定等场景提供可用技能名清单。
 *
 * <p>{@code GET /v2/ai/skills/catalog} 聚合本地已安装技能（user + builtin）的名称，
 * 供 机器人managementpage 等前端做技能下拉/选择。缺失时返回空列表而非报错。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Tag(name = "AI 技能目录")
@RestController
@RequestMapping("/v2/ai/skills")
@RequiredArgsConstructor
public class AiSkillCatalogController {

    /**
     * 技能控制器（本地文件系统扫描）
     */
    private final ApiSkillController apiSkillController;

    /**
     * 查询可用技能名目录。
     *
     * @return 技能名列表（去重）
     */
    @Operation(summary = "查询技能名目录")
    @GetMapping("/catalog")
    public List<String> catalog() {
        List<Map<String, Object>> skills = apiSkillController.list().getData();
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .map(s -> s.get("name"))
                .filter(n -> n != null && !String.valueOf(n).isBlank())
                .map(Object::toString)
                .distinct()
                .toList();
    }
}
