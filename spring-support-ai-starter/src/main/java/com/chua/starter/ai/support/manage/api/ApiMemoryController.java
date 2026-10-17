package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversation;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.store.SysAiMemoryStore;
import com.chua.starter.ai.support.model.AiMemoryDTO;
import com.chua.starter.ai.support.model.AiMemoryMaintenanceDTO;
import com.chua.starter.ai.support.model.AiMemoryRecallDTO;
import com.chua.starter.ai.support.model.AiMemoryStatsDTO;
import com.chua.starter.ai.support.model.AiMemoryUpsertRequest;
import com.chua.starter.ai.support.schedule.MemoryMaintenanceTask;
import com.chua.starter.ai.support.service.SysAiMemoryService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 记忆体出口 —— 会话顶部「上下文管理」面板的记忆列表、删除与召回试算。
 *
 * <p>契约：{@code /api/memories} 系列以 {@code conversation_id} 为可选维度，一次列表同时返回该用户
 * 的全局记忆与指定会话的会话级记忆；{@code recall} 出口用于回显"本轮会注入哪些记忆"，
 * 默认只做试算不回写统计（{@code apply=false}）。</p>
 *
 * <p>隔离口径：全部出口按 {@code sys_ai_memory_user_id} 归属，本人可读写；管理员可读他人记忆、
 * 不可修改或删除；他人一律 403。归属用户一律由服务端按当前登录用户写入，请求体里的
 * {@code userId} 不接受。跨用户的全量维护出口仅管理员可用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
@Slf4j
public class ApiMemoryController {

    /**
     * 记忆体服务
     */
    private final SysAiMemoryService memoryService;

    /**
     * 记忆体存储（容量统计与按会话清理）
     */
    private final SysAiMemoryStore memoryStore;

    /**
     * 会话存储（校验会话归属）
     */
    private final SysAiConversationStore conversationStore;

    @GetMapping
    /**
     * 记忆列表。
     * @param scope 作用域筛选
     * @param conversationId 会话 标识筛选
     * @param keyword 关键词筛选
     * @param status 状态筛选
     * @param limit 条数上限
     * @return 记忆列表的结果
     */
    public ApiResult<List<AiMemoryDTO>> list(@RequestParam(required = false) String scope,
                                             @RequestParam(name = "conversation_id", required = false) String conversationId,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(name = "limit", required = false, defaultValue = "100") int limit) {
        if (conversationId != null && !conversationId.isBlank()) {
            conversationStore.assertReadableIfPresent(conversationId);
        }
        return ApiResult.ok(memoryService.list(scope, conversationId, keyword, status, limit));
    }

    @PostMapping
    /**
     * 新增记忆。
     * @param request 请求主体
     * @return 新增记忆的结果
     */
    public ApiResult<AiMemoryDTO> create(@RequestBody AiMemoryUpsertRequest request) {
        String conversationId = request == null ? null : request.getConversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            conversationStore.assertWritableIfPresent(conversationId);
        }
        return ApiResult.ok(memoryService.record(AiUserContext.requireUserId(), request, "manual"));
    }

    @GetMapping("/{memoryId}")
    /**
     * 读取记忆。
     * @param memoryId 记忆 标识
     * @return 读取记忆的结果
     */
    public ApiResult<AiMemoryDTO> get(@PathVariable String memoryId) {
        return ApiResult.ok(memoryService.get(memoryId));
    }

    @PutMapping("/{memoryId}")
    /**
     * 整体更新记忆。
     * @param memoryId 记忆 标识
     * @param request 请求主体
     * @return 更新记忆的结果
     */
    public ApiResult<AiMemoryDTO> update(@PathVariable String memoryId, @RequestBody AiMemoryUpsertRequest request) {
        return ApiResult.ok(memoryService.update(memoryId, request));
    }

    @PatchMapping("/{memoryId}")
    /**
     * 部分更新记忆。
     * @param memoryId 记忆 标识
     * @param request 请求主体
     * @return 更新记忆的结果
     */
    public ApiResult<AiMemoryDTO> patch(@PathVariable String memoryId, @RequestBody AiMemoryUpsertRequest request) {
        return ApiResult.ok(memoryService.update(memoryId, request));
    }

    @DeleteMapping("/{memoryId}")
    /**
     * 删除记忆。
     * @param memoryId 记忆 标识
     * @return 删除记忆的结果
     */
    public ApiResult<Void> delete(@PathVariable String memoryId) {
        memoryService.delete(memoryId);
        return ApiResult.ok();
    }

    @DeleteMapping("/conversation/{conversationId}")
    /**
     * 清空某会话的会话级记忆（全局记忆不动）。
     * @param conversationId 会话 标识
     * @return 清空操作的结果
     */
    public ApiResult<Integer> clearConversation(@PathVariable String conversationId) {
        conversationStore.assertWritableIfPresent(conversationId);
        return ApiResult.ok(memoryStore.deleteByConversation(conversationId));
    }

    @GetMapping("/recall")
    /**
     * 召回试算：本轮会注入哪些记忆。
     * @param conversationId 会话 标识
     * @param query 查询文本
     * @param apply 是否回写召回统计
     * @return 召回试算：本轮会注入哪些记忆的结果
     */
    public ApiResult<AiMemoryRecallDTO> recall(@RequestParam(name = "conversation_id", required = false) String conversationId,
                                               @RequestParam(required = false) String query,
                                               @RequestParam(required = false, defaultValue = "false") boolean apply) {
        String userId;
        if (conversationId != null && !conversationId.isBlank()) {
            conversationStore.assertReadableIfPresent(conversationId);
            SysAiConversation conversation = conversationStore.findIfPresent(conversationId);
            userId = conversation == null || conversation.getUserId() == null
                    ? AiUserContext.requireUserId() : conversation.getUserId();
        } else {
            userId = AiUserContext.requireUserId();
        }
        return ApiResult.ok(memoryService.recall(userId, conversationId, query, apply));
    }

    @PostMapping("/maintenance")
    /**
     * 维护当前用户的记忆库。
     * @return 维护当前用户的记忆库的结果
     */
    public ApiResult<AiMemoryMaintenanceDTO> maintenance() {
        return ApiResult.ok(memoryService.maintain(AiUserContext.requireUserId()));
    }

    @Permission("admin")
    @PostMapping("/maintenance/all")
    /**
     * 维护全部用户的记忆库。
     * @return 维护全部用户的记忆库的结果
     */
    public ApiResult<AiMemoryMaintenanceDTO> maintenanceAll() {
        return ApiResult.ok(memoryService.maintainAll());
    }

    @GetMapping("/maintenance/latest")
    /**
     * 最近一次后台维护报告（尚未执行过时 data 为空）。
     * @return 最近一次后台维护报告的结果
     */
    public ApiResult<AiMemoryMaintenanceDTO> maintenanceLatest() {
        return ApiResult.ok(MemoryMaintenanceTask.lastReport());
    }

    @GetMapping("/stats")
    /**
     * 记忆容量统计（当前用户）。
     * @return 记忆容量统计（当前用户）的结果
     */
    public ApiResult<AiMemoryStatsDTO> stats() {
        String userId = AiUserContext.requireUserId();
        return ApiResult.ok(new AiMemoryStatsDTO(
                userId,
                memoryStore.countActive(userId),
                memoryService.tuning().getMaxPerUser(),
                memoryStore.countRecallable(userId, null)));
    }
}
