package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversation;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* 定时任务（Cron）控制器，替代 nomicore 的 {@code /api/cron/jobs} 契约。
*
* <p>维护定时任务（{@code cron_job_id} 主键）、立即执行、运行记录与技能脚本。
* 关键变更推送 {@code cron.job-created / cron.job-updated / cron.job-removed / cron.job-executed}
* 事件。当前为进程内内存实现。</p>
*
* @since 4.0.0.42
* @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/cron/jobs")
@RequiredArgsConstructor
@Slf4j
public class ApiCronController {

    /**
    * 主键生成与基础存储工具
    */
    private final SysAiConversationStore conversationStore;

    /**
    * WS 事件推送服务
    */
    private final ApiWebSocketPushService pushService;

    /**
    * 定时任务：cron_作业_标识 → 任务
    */
    private final Map<String, Map<String, Object>> jobs = new ConcurrentHashMap<>();

    @GetMapping
    /**
    * 列表。
    * @param false false
    * @return 列表的结果
    */
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(required = false) String conversation_id) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> job : jobs.values()) {
            if (conversation_id == null
                    || conversation_id.equals(jobPath(job, "conversation_id"))) {
                result.add(job);
            }
        }
        return ApiResult.ok(result);
    }

    @PostMapping
    /**
    * 添加。
    * @param body 主体
    * @return 添加的结果
    */
    public ApiResult<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        String jobId = conversationStore.newId();
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("cron_job_id", jobId);
        job.put("name", body.getOrDefault("name", "未命名任务"));
        job.put("description", body.get("description"));
        job.put("enabled", body.getOrDefault("enabled", true));
        job.put("schedule", body.getOrDefault("schedule", Map.of("kind", "every", "every_ms", 3600000, "description", "每小时")));
        job.put("message", body.getOrDefault("message", ""));
        job.put("execution_mode", body.getOrDefault("execution_mode", "new_conversation"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("agent_type", body.getOrDefault("agent_type", "nomi"));
        metadata.put("created_by", body.getOrDefault("created_by", "user"));
        long now = System.currentTimeMillis();
        metadata.put("created_at", now);
        metadata.put("updated_at", now);
        if (body.get("conversation_id") != null) {
            metadata.put("conversation_id", body.get("conversation_id"));
        }
        if (body.get("conversation_title") != null) {
            metadata.put("conversation_title", body.get("conversation_title"));
        }
        if (body.get("agent_config") != null) {
            metadata.put("agent_config", body.get("agent_config"));
        }
        job.put("metadata", metadata);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("next_run_at_ms", null);
        state.put("last_run_at_ms", null);
        state.put("last_status", null);
        state.put("last_error", null);
        state.put("run_count", 0);
        state.put("retry_count", 0);
        state.put("max_retries", body.getOrDefault("max_retries", 0));
        job.put("state", state);
        jobs.put(jobId, job);
        pushService.push("cron.job-created", job);
        return ApiResult.ok(job);
    }

    @GetMapping("/{cronJobId}")
    /**
    * 获取。
    * @param cronJobId cron作业标识
    * @return 获取的结果
    */
    public ApiResult<Map<String, Object>> get(@PathVariable String cronJobId) {
        return ApiResult.ok(requireJob(cronJobId));
    }

    @PutMapping("/{cronJobId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String cronJobId,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> job = requireJob(cronJobId);
        if (body.containsKey("name")) {
            job.put("name", body.get("name"));
        }
        if (body.containsKey("description")) {
            job.put("description", body.get("description"));
        }
        if (body.containsKey("enabled")) {
            job.put("enabled", body.get("enabled"));
        }
        if (body.containsKey("schedule")) {
            job.put("schedule", body.get("schedule"));
        }
        if (body.containsKey("message")) {
            job.put("message", body.get("message"));
        }
        if (body.containsKey("agent_config")) {
            ((Map<String, Object>) job.get("metadata")).put("agent_config", body.get("agent_config"));
        }
        if (body.containsKey("conversation_title")) {
            ((Map<String, Object>) job.get("metadata")).put("conversation_title", body.get("conversation_title"));
        }
        if (body.containsKey("max_retries")) {
            ((Map<String, Object>) job.get("state")).put("max_retries", body.get("max_retries"));
        }
        ((Map<String, Object>) job.get("metadata")).put("updated_at", System.currentTimeMillis());
        pushService.push("cron.job-updated", job);
        return ApiResult.ok(job);
    }

    @DeleteMapping("/{cronJobId}")
    /**
    * 移除。
    * @param cronJobId cron作业标识
    * @return 移除的结果
    */
    public ApiResult<Void> remove(@PathVariable String cronJobId) {
        /**
        * require作业。
        * @param cronJobId cron作业标识
        */
        requireJob(cronJobId);
        jobs.remove(cronJobId);
        pushService.push("cron.job-removed", Map.of("cron_job_id", cronJobId));
        return ApiResult.ok();
    }

    @PostMapping("/{cronJobId}/run")
    public ApiResult<Map<String, Object>> run(@PathVariable String cronJobId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> job = requireJob(cronJobId);
        Map<String, Object> state = (Map<String, Object>) job.get("state");
        state.put("last_run_at_ms", System.currentTimeMillis());
        state.put("last_status", "ok");
        state.put("run_count", ((Number) state.get("run_count")).intValue() + 1);
        Map<String, Object> executed = new LinkedHashMap<>();
        executed.put("cron_job_id", cronJobId);
        executed.put("status", "ok");
        pushService.push("cron.job-executed", executed);
        SysAiConversation c = new SysAiConversation();
        String conversationId = conversationStore.newId();
        c.setConversationId(conversationId);
        c.setType((String) job.getOrDefault("agent_type", "nomi"));
        Map<String, Object> metadata = (Map<String, Object>) job.get("metadata");
        String name = job.getOrDefault("name", "定时任务").toString();
        c.setName(name);
        Object agentConfig = metadata != null ? metadata.get("agent_config") : null;
        if (agentConfig instanceof Map) {
            Map<String, Object> raw = uncheckedCast(agentConfig);
            Map<String, Object> model = new LinkedHashMap<>();
            if (raw.get("provider_id") != null) {
                model.put("provider_id", raw.get("provider_id"));
            }
            if (raw.get("model") != null) {
                model.put("model", raw.get("model"));
            }
            c.setModel(model);
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("cron_job_id", cronJobId);
        extra.put("source", "cron");
        c.setExtra(extra);
        long now = System.currentTimeMillis();
        c.setCreatedAt(now);
        c.setModifiedAt(now);
        conversationStore.create(c);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "created"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversation_id", conversationId);
        return ApiResult.ok(result);
    }

    @GetMapping("/{cronJobId}/runs")
    /**
    * 运行。
    * @param cronJobId cron作业标识
    * @return 运行的结果
    */
    public ApiResult<List<Map<String, Object>>> runs(@PathVariable String cronJobId) {
        /**
        * require作业。
        * @param cronJobId cron作业标识
        */
        requireJob(cronJobId);
        return ApiResult.ok(new ArrayList<>());
    }

    @PostMapping("/{cronJobId}/skill")
    public ApiResult<Void> saveSkill(@PathVariable String cronJobId,
                                     @RequestBody Map<String, Object> body) {
        /**
        * require作业。
        * @param cronJobId cron作业标识
        */
        requireJob(cronJobId);
        return ApiResult.ok();
    }

    @GetMapping("/{cronJobId}/skill")
    /**
    * 是否包含skill。
    * @param cronJobId cron作业标识
    * @return 是否包含skill的结果
    */
    public ApiResult<Map<String, Object>> hasSkill(@PathVariable String cronJobId) {
        /**
        * require作业。
        * @param cronJobId cron作业标识
        */
        requireJob(cronJobId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("has_skill", false);
        return ApiResult.ok(result);
    }

    @DeleteMapping("/{cronJobId}/skill")
    /**
    * 删除skill。
    * @param cronJobId cron作业标识
    * @return 删除skill的结果
    */
    public ApiResult<Void> deleteSkill(@PathVariable String cronJobId) {
        /**
        * require作业。
        * @param cronJobId cron作业标识
        */
        requireJob(cronJobId);
        return ApiResult.ok();
    }

    /**
    * 作业路径。
    * @param job 作业
    * @param key 钥匙
    * @return 作业路径的结果
    */
    private String jobPath(Map<String, Object> job, String key) {
        Object metadata = job.get("metadata");
        if (metadata instanceof Map) {
            Object value = ((Map<?, ?>) metadata).get(key);
            return value == null ? null : value.toString();
        }
        return null;
    }

    /**
    * require作业。
    * @param cronJobId cron作业标识
    * @return require作业的结果
    */
    private Map<String, Object> requireJob(String cronJobId) {
        Map<String, Object> job = jobs.get(cronJobId);
        if (job == null) {
            /**
            * api异常。
            * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
            * @param cronJobId cron作业标识
            * @return api异常的结果
            */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "定时任务不存在: " + cronJobId);
        }
        return job;
    }

    /**
    * uncheckedcast。
    * @param obj obj
    * @return uncheckedCast的结果
    */
    private static Map<String, Object> uncheckedCast(Object obj) {
        return (Map<String, Object>) obj;
    }
}
