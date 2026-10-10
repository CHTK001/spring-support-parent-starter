package com.chua.starter.ai.support.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.model.KnowledgeGraphExtractionDTO;
import com.chua.starter.ai.support.model.vo.KnowledgeGraphViewVO;
import com.chua.starter.ai.support.service.KnowledgeGraphService;
import com.chua.starter.ai.support.service.impl.KnowledgeGraphExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱接口：为前端关系图谱（X6 {@code ReGraphRelation}）提供数据，并支持手动重新抽取。
 *
 * <p>返回结构里的 {@code nodes} / {@code edges} 字段与前端 {@code ReGraphData} 契约一致，
 * 前端可直接透传给 {@code @repo/ui/re-graph-relation}，不需要再做字段映射。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Tag(name = "AI 知识图谱", description = "知识点实体关系抽取与关系图谱")
@RestController
@RequestMapping("/v2/rag/graph")
@RequiredArgsConstructor
public class KnowledgeGraphController {

    /**
     * 知识图谱服务
     */
    private final KnowledgeGraphService knowledgeGraphService;
    /**
     * 关系抽取服务
     */
    private final KnowledgeGraphExtractionService knowledgeGraphExtractionService;

    /**
     * 查询关系图谱。
     *
     * @param knowledgeBaseId 知识库ID
     * @param docPath 知识点相对路径，为空时返回整个知识库的图谱
     * @return 图谱视图（nodes + edges）
     */
    @Operation(summary = "查询关系图谱", description = "按知识库查询关系图谱；传 docPath 则只返回该知识点的子图")
    @GetMapping
    public ApiResult<KnowledgeGraphViewVO> graph(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam(value = "doc_path", required = false) String docPath) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "知识库ID不能为空");
        }
        KnowledgeGraphViewVO view = StringUtils.hasText(docPath)
                ? knowledgeGraphService.getKnowledgePointGraph(knowledgeBaseId, docPath)
                : knowledgeGraphService.getKnowledgeBaseGraph(knowledgeBaseId);
        return ApiResult.ok(view);
    }

    /**
     * 查询知识图谱的实体与关系数量。
     *
     * @param knowledgeBaseId 知识库ID
     * @return 含 knowledge_base_id / entity_count / relation_count 的结果
     */
    @Operation(summary = "关系图谱统计", description = "返回知识库的实体数与关系数，用于页签徽标")
    @GetMapping("/count")
    public ApiResult<Map<String, Object>> count(
            @RequestParam("knowledge_base_id") String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "知识库ID不能为空");
        }
        int[] counts = knowledgeGraphService.countKnowledgeBaseGraph(knowledgeBaseId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("knowledge_base_id", knowledgeBaseId);
        result.put("entity_count", counts[0]);
        result.put("relation_count", counts[1]);
        return ApiResult.ok(result);
    }

    /**
     * 立即抽取指定文本的关系（只抽取不落库），用于排障时观察大模型输出。
     *
     * @param text 待抽取文本
     * @return 抽取结果
     */
    @Operation(summary = "试抽取关系", description = "只跑大模型抽取、不写库，用于验证提示词效果")
    @PostMapping("/extract")
    public ApiResult<KnowledgeGraphExtractionDTO> extract(@RequestParam("text") String text) {
        if (!StringUtils.hasText(text)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "文本不能为空");
        }
        return ApiResult.ok(knowledgeGraphExtractionService.extractOnly(text));
    }

    /**
     * 关系提取开关的当前状态。
     *
     * @return 含 enabled 的结果
     */
    @Operation(summary = "关系提取开关状态", description = "返回上传知识点时是否抽取关系")
    @GetMapping("/enabled")
    public ApiResult<Map<String, Object>> enabled() {
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("enabled", knowledgeGraphExtractionService.isEnabled());
        return ApiResult.ok(result);
    }

    // ==================== 手工维护：增 / 改 / 删 ====================

    /**
     * 手工新增实体。
     *
     * @param knowledgeBaseId 知识库ID
     * @param name            实体名称
     * @param type            实体类型（person/location/event/organization/other）
     * @param description     实体说明，可为空
     * @param logo            实体图片地址，可为空；已存在同名实体时带图会覆盖
     * @return 含 uid 的结果
     */
    @Operation(summary = "手工新增实体", description = "同名同类型会复用已有节点，不会产生重复")
    @PostMapping("/entity")
    public ApiResult<Map<String, Object>> addEntity(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("knowledge_base_name") String knowledgeBaseName,
            @RequestParam("name") String name,
            @RequestParam(value = "type", required = false, defaultValue = "other") String type,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "logo", required = false) String logo) {
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "实体名称不能为空");
        }
        String uid = knowledgeGraphService.addManualEntity(knowledgeBaseId, knowledgeBaseName,
                name, type, description, logo);
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("uid", uid);
        return ApiResult.ok(result);
    }

    /**
     * 手工新增关系。端点实体不存在时会按名称自动建 other 类型的占位实体。
     *
     * @param knowledgeBaseId 知识库ID
     * @param source          源实体名称
     * @param target          目标实体名称
     * @param type            关系类型
     * @return 含 uid 的结果
     */
    @Operation(summary = "手工新增关系", description = "端点实体不存在时自动建占位实体；自环会被拒绝")
    @PostMapping("/relation")
    public ApiResult<Map<String, Object>> addRelation(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("knowledge_base_name") String knowledgeBaseName,
            @RequestParam("source") String source,
            @RequestParam("target") String target,
            @RequestParam(value = "type", required = false, defaultValue = "相关") String type) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "关系的源与目标实体名称都不能为空");
        }
        String uid = knowledgeGraphService.addManualRelation(knowledgeBaseId, knowledgeBaseName,
                source, target, type);
        if (uid == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "关系的源与目标不能是同一个实体");
        }
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("uid", uid);
        return ApiResult.ok(result);
    }

    /**
     * 修改实体（名称/类型/说明/图片，null 表示该项不改）。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uid             实体 uid
     * @return 是否更新成功
     */
    @Operation(summary = "修改实体", description = "只传要改的字段；logo 空串表示清除图片")
    @PostMapping("/entity/update")
    public ApiResult<Map<String, Object>> updateEntity(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("uid") String uid,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "logo", required = false) String logo) {
        boolean ok = knowledgeGraphService.updateEntity(knowledgeBaseId, uid, name, type, description, logo);
        if (!ok) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "实体不存在: " + uid);
        }
        return ApiResult.ok(Map.of("updated", true));
    }

    /**
     * 删除实体（连带删掉指向它的关系与不再被引用的实体）。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uid             实体 uid
     * @return 是否删除成功
     */
    @Operation(summary = "删除实体", description = "级联删除其关联与相关边")
    @PostMapping("/entity/delete")
    public ApiResult<Map<String, Object>> deleteEntity(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("uid") String uid) {
        boolean ok = knowledgeGraphService.deleteEntity(knowledgeBaseId, uid);
        if (!ok) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "实体不存在: " + uid);
        }
        return ApiResult.ok(Map.of("deleted", true));
    }

    /**
     * 批量删除实体。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uids            逗号分隔的实体 uid 列表
     * @return 实际删除数量
     */
    @Operation(summary = "批量删除实体", description = "uids 逗号分隔；级联删除关联与相关边")
    @PostMapping("/entity/batch-delete")
    public ApiResult<Map<String, Object>> deleteEntities(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("uids") String uids) {
        List<String> list = splitUids(uids);
        if (list.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "uids 不能为空");
        }
        int n = knowledgeGraphService.deleteEntities(knowledgeBaseId, list);
        return ApiResult.ok(Map.of("deleted", n, "requested", list.size()));
    }

    /**
     * 删除单条关系。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uid             关系 uid
     * @return 是否删除成功
     */
    @Operation(summary = "删除关系", description = "同时回收只被这条边引用的端点实体")
    @PostMapping("/relation/delete")
    public ApiResult<Map<String, Object>> deleteRelation(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("uid") String uid) {
        boolean ok = knowledgeGraphService.deleteRelation(knowledgeBaseId, uid);
        if (!ok) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "关系不存在: " + uid);
        }
        return ApiResult.ok(Map.of("deleted", true));
    }

    /**
     * 批量删除关系。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uids            逗号分隔的关系 uid 列表
     * @return 实际删除数量
     */
    @Operation(summary = "批量删除关系", description = "uids 逗号分隔；同时回收只被这些边引用的端点实体")
    @PostMapping("/relation/batch-delete")
    public ApiResult<Map<String, Object>> deleteRelations(
            @RequestParam("knowledge_base_id") String knowledgeBaseId,
            @RequestParam("uids") String uids) {
        List<String> list = splitUids(uids);
        if (list.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "uids 不能为空");
        }
        int n = knowledgeGraphService.deleteRelations(knowledgeBaseId, list);
        return ApiResult.ok(Map.of("deleted", n, "requested", list.size()));
    }

    /**
     * 收敛重复节点与重复边（数据修复入口）。
     *
     * <p>早期实体按「名称+类型」去重，大模型类型判断有波动时会建出多个同名节点，
     * 指向它们的边也随之重复。调用本端点把它们合并成一条，并删除完全重复的边。</p>
     *
     * @param knowledgeBaseId 知识库ID
     * @return 被合并或删除的实体行数
     */
    @Operation(summary = "收敛重复图谱", description = "合并同名实体、重指向关系端点、删除重复边")
    @PostMapping("/collapse-duplicates")
    public ApiResult<Map<String, Object>> collapseDuplicates(
            @RequestParam("knowledge_base_id") String knowledgeBaseId) {
        int removed = knowledgeGraphService.collapseDuplicateGraph(knowledgeBaseId);
        int[] counts = knowledgeGraphService.countKnowledgeBaseGraph(knowledgeBaseId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("merged_entities", removed);
        result.put("entity_count", counts[0]);
        result.put("relation_count", counts[1]);
        return ApiResult.ok(result);
    }

    /**
     * 拆分逗号分隔的 uid 列表，去空白、去空项、去重。
     *
     * @param raw 原始字符串
     * @return uid 列表
     */
    private List<String> splitUids(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
