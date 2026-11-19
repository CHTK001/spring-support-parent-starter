package com.chua.starter.ai.support.manage.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.mapper.SysAiGenerateTaskMapper;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.common.support.annotations.Permission;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 余额告警接收人候选：给「告警接收人」下拉提供真实用户候选。
 *
 * <p>用户目录（sys_user 表与用户列表 API）在外部认证服务里，本模块没有也不该有
 * （模块边界 R2：禁止穿透别人的持久层）。所以候选只从 <b>ai 自己的表</b>里取——
 * 用过 AI 的用户（会话表 / 生成任务表的 sys_user_id），按最近活跃时间倒序返回；
 * 按用户 ID 模糊过滤。前端远程搜索展示，取不到或输错时仍保留手输 ID 兜底。</p>
 *
 * <p>只读两件事：谁出现过（distinct sys_user_id）、最近什么时候活跃（max 更新时间）。
 * 不返回昵称 / 邮箱等目录信息——那些在认证服务里，这里拿不到也不该猜。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@RestController
@RequestMapping("/api/providers/credit")
@RequiredArgsConstructor
public class ProviderReceiverCandidateController {

    /**
     * AI 会话表（sys_ai_session）：聊过天的用户即候选
     */
    private final SysAiSessionMapper sysAiSessionMapper;

    /**
     * AI 生成任务表（sys_ai_generate_task）：生成过图片 / 视频的用户也是候选
     */
    private final SysAiGenerateTaskMapper sysAiGenerateTaskMapper;

    /**
     * 候选项：用户 ID + 最近活跃时间（epoch 毫秒；取不到为 null）。
     *
     * @param userId       用户标识（sys_user_id）
     * @param lastActiveAt 最近活跃时间（会话 / 生成任务里较新的一个更新时间）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ReceiverCandidateItem(@JsonProperty("user_id") String userId,
                                 @JsonProperty("last_active_at") Long lastActiveAt) {
    }

    /**
     * 接收人候选列表：会话表 ∪ 生成任务表，按最近活跃倒序，最多 50 条。
     *
     * @param keyword 用户 ID 模糊过滤（空 = 不过滤，返回最近活跃的前 50 个）
     * @return 候选列表（查不到为空列表，不报错）
     */
    @Permission("admin")
    @GetMapping("/receiver-candidates")
    public ApiResult<List<ReceiverCandidateItem>> receiverCandidates(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        Map<String, Long> merged = new LinkedHashMap<>();
        mergeCandidates(merged, sessionCandidates(kw));
        mergeCandidates(merged, taskCandidates(kw));
        List<Map.Entry<String, Long>> entries = new ArrayList<>(merged.entrySet());
        entries.sort((a, b) -> {
            Long ra = a.getValue();
            Long rb = b.getValue();
            if (ra == null && rb == null) {
                return 0;
            }
            if (ra == null) {
                return 1;
            }
            if (rb == null) {
                return -1;
            }
            return Long.compare(rb, ra);
        });
        List<ReceiverCandidateItem> items = new ArrayList<>(Math.min(50, entries.size()));
        for (Map.Entry<String, Long> entry : entries) {
            if (items.size() >= 50) {
                break;
            }
            items.add(new ReceiverCandidateItem(entry.getKey(), entry.getValue()));
        }
        return ApiResult.ok(items);
    }

    /**
     * 会话表候选：distinct sys_user_id + 最近一次会话更新时间。
     */
    private List<Map<String, Object>> sessionCandidates(String keyword) {
        QueryWrapper<SysAiSession> wrapper = new QueryWrapper<SysAiSession>()
                .select("sys_user_id", "max(update_time) as last_active_at")
                .isNotNull("sys_user_id")
                .ne("sys_user_id", "")
                .groupBy("sys_user_id");
        if (!keyword.isEmpty()) {
            wrapper.like("sys_user_id", keyword);
        }
        wrapper.orderByDesc("last_active_at").last("limit 50");
        return sysAiSessionMapper.selectMaps(wrapper);
    }

    /**
     * 生成任务表候选：distinct sys_user_id + 最近一次任务更新时间。
     */
    private List<Map<String, Object>> taskCandidates(String keyword) {
        QueryWrapper<SysAiGenerateTask> wrapper = new QueryWrapper<SysAiGenerateTask>()
                .select("sys_user_id", "max(sys_ai_generate_task_update_time) as last_active_at")
                .isNotNull("sys_user_id")
                .ne("sys_user_id", "")
                .groupBy("sys_user_id");
        if (!keyword.isEmpty()) {
            wrapper.like("sys_user_id", keyword);
        }
        wrapper.orderByDesc("last_active_at").last("limit 50");
        return sysAiGenerateTaskMapper.selectMaps(wrapper);
    }

    /**
     * 把一张表的候选并进总表：同一用户取较新的活跃时间。
     */
    private void mergeCandidates(Map<String, Long> merged, List<Map<String, Object>> rows) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Object rawId = row == null ? null : row.get("sys_user_id");
            if (rawId == null) {
                continue;
            }
            String userId = String.valueOf(rawId).trim();
            if (userId.isEmpty()) {
                continue;
            }
            Long activeAt = toEpochMilli(row.get("last_active_at"));
            Long old = merged.get(userId);
            if (old == null || (activeAt != null && activeAt > old)) {
                merged.put(userId, activeAt);
            }
        }
    }

    /**
     * 时间值统一转 epoch 毫秒（LocalDateTime / Timestamp / Date / 数字都接）。
     */
    private Long toEpochMilli(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (raw instanceof Timestamp ts) {
            return ts.getTime();
        }
        if (raw instanceof Date d) {
            return d.getTime();
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
