package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.service.SkillCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 技能来源清单控制器：本地工具技能、在线市场/聚合源的配置、检索与本地安装。
 *
 * <p>配合「安装技能」弹窗的「上传 / 检索」双模式：检索模式按来源开关聚合本地与在线结果；
 * 来源开关在系统设置的 Skills 配置中维护，持久化于全局设置 {@code skills} 分组。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/skills/catalog")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - Skills 来源清单")
public class SkillCatalogController {

    private final SkillCatalogService skillCatalogService;

    /**
     * 获取全部来源配置与开关。
     *
     * @return 分组配置
     */
    @Operation(summary = "获取 Skills 来源配置")
    @GetMapping("/config")
    public Map<String, Object> config() {
        return skillCatalogService.getConfig();
    }

    /**
     * 更新来源开关。
     *
     * @param config 键为来源键、值为开关
     * @return 更新后的配置
     */
    @Operation(summary = "更新 Skills 来源开关")
    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody Map<String, Boolean> config) {
        skillCatalogService.updateConfig(config);
        return skillCatalogService.getConfig();
    }

    /**
     * 检索已启用本地工具的技能。
     *
     * @param keyword 过滤关键词
     * @return 本地技能条目
     */
    @Operation(summary = "检索本地技能")
    @GetMapping("/local")
    public List<Map<String, Object>> local(@RequestParam(value = "keyword", required = false) String keyword) {
        return skillCatalogService.listLocal(keyword);
    }

    /**
     * 在已启用在线聚合源中检索。
     *
     * @param keyword 关键词
     * @return 市场清单条目
     */
    @Operation(summary = "检索在线聚合技能")
    @GetMapping("/aggregators")
    public List<Map<String, Object>> aggregators(@RequestParam(value = "keyword", required = false) String keyword) {
        return skillCatalogService.searchAggregators(keyword);
    }

    /**
     * 聚合已启用市场来源（市场 SPI）的技能，含图标、标签与 ZIP/Git 安装地址。
     *
     * @param keyword 过滤关键词，可空
     * @return 市场条目
     */
    @Operation(summary = "检索官网市场技能")
    @GetMapping("/market")
    public List<?> market(@RequestParam(value = "keyword", required = false) String keyword) {
        return skillCatalogService.listMarket(keyword);
    }

    /**
     * 列出内置（默认常用）技能，随系统分发、默认启用。
     *
     * @return 内置技能条目
     */
    @Operation(summary = "列出内置技能")
    @GetMapping("/builtin")
    public List<Map<String, Object>> builtin() {
        return skillCatalogService.listBuiltin();
    }

    /**
     * 安装本地工具技能（复制目录到 skills/user）。
     *
     * @param body 含 tool、name、force
     * @return 安装结果
     */
    @Operation(summary = "安装本地工具技能")
    @PostMapping("/install-local")
    public Map<String, Object> installLocal(@RequestBody Map<String, Object> body) {
        String tool = body.get("tool") == null ? null : String.valueOf(body.get("tool"));
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        boolean force = Boolean.TRUE.equals(body.get("force"));
        return skillCatalogService.installLocal(tool, name, force);
    }
}
