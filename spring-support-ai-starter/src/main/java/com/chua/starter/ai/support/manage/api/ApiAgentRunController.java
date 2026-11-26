// [P3C A20 豁免] A20/A30/A31: 含错误消息字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.ai.agent.run.AgentRunQuery;
import com.chua.common.support.ai.agent.run.AgentRunRecord;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.AgentRunService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行记录查询 / 治理 API。
 *
 * <p>运行列表与详情读开放（普通用户仅见本人运行，管理员可见全部）；
 * 取消操作限管理员或归属人。步骤时间线随详情返回，前端据此渲染瀑布图。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/agents/runs")
@RequiredArgsConstructor
public class ApiAgentRunController {

    /**
     * 分页大小上限
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 运行记录服务
     */
    private final AgentRunService agentRunService;

    /**
     * 运行列表（过滤：sessionId / agentId / status；分页）。
     *
     * @param sessionId 会话标识
     * @param agentId   Agent 标识
     * @param status    状态名（RUNNING/COMPLETED/FAILED/CANCELED/WAITING_APPROVAL）
     * @param page      页码（1 起）
     * @param pageSize  页大小（1~200，缺省 20）
     * @return { total, page, pageSize, items }
     */
    @GetMapping
    public ApiResult<Map<String, Object>> listRuns(
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "agentId", required = false) String agentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int size = pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        long offset = (long) (safePage - 1) * size;
        if (offset > Integer.MAX_VALUE) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "页码超出支持范围");
        }
        // 普通用户强制只看本人；管理员可看全部
        String userId = AiUserContext.canAccessOthersData() ? null : AiUserContext.requireUserId();
        AgentRunQuery query = new AgentRunQuery(sessionId, agentId, status, userId, size, (int) offset);
        long total = agentRunService.countRuns(query);
        List<AgentRunRecord> items = agentRunService.listRuns(query);
        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", size);
        result.put("items", items);
        return ApiResult.ok(result);
    }

    /**
     * 运行详情 + 步骤时间线。
     *
     * @param runId 运行标识
     * @return { run, steps }
     */
    @GetMapping("/{runId}")
    public ApiResult<Map<String, Object>> runDetail(@PathVariable("runId") String runId) {
        AgentRunRecord run = agentRunService.getRun(runId);
        if (!AiUserContext.canAccessOthersData()
                && (run.userId() == null || !run.userId().equals(AiUserContext.currentUserId()))) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权查看该运行");
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("run", run);
        result.put("steps", agentRunService.listSteps(runId));
        return ApiResult.ok(result);
    }

    /**
     * 取消运行（管理员或归属人；已终态幂等返回）。
     *
     * @param runId 运行标识
     * @return 取消后的运行记录
     */
    @PostMapping("/{runId}/cancel")
    public ApiResult<AgentRunRecord> cancelRun(@PathVariable("runId") String runId) {
        return ApiResult.ok(agentRunService.cancelRun(runId, AiUserContext.requireUserId()));
    }
}
