package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiResult;
import com.chua.starter.ai.support.nomifun.store.ConversationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 伴侣（Companion）存根控制器。
 *
 * <p>提供伴侣系统的占位实现，包括伴侣 CRUD、记忆管理、技能管理、数据导入导出等。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/companion")
@RequiredArgsConstructor
public class ApiCompanionController {

    private final ConversationStore conversationStore;

    @GetMapping("/companions")
    public ApiResult<List<Map<String, Object>>> listCompanions() {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/companions")
    public ApiResult<Map<String, Object>> createCompanion(@RequestBody Map<String, Object> body) {
        body.put("companion_id", conversationStore.newId());
        body.putIfAbsent("created_at", System.currentTimeMillis());
        return ApiResult.ok(body);
    }

    @GetMapping("/companions/{companionId}")
    public ApiResult<Map<String, Object>> getCompanion(@PathVariable String companionId) {
        return ApiResult.ok(Map.of("companion_id", companionId, "name", "", "character", ""));
    }

    @PatchMapping("/companions/{companionId}")
    public ApiResult<Map<String, Object>> patchCompanion(@PathVariable String companionId,
                                                          @RequestBody Map<String, Object> body) {
        body.put("companion_id", companionId);
        return ApiResult.ok(body);
    }

    @DeleteMapping("/companions/{companionId}")
    public ApiResult<Void> deleteCompanion(@PathVariable String companionId) {
        return ApiResult.ok();
    }

    @PostMapping("/companions/{companionId}/apply-preset")
    public ApiResult<Void> applyPreset(@PathVariable String companionId,
                                        @RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/companions/{companionId}/status")
    public ApiResult<Map<String, Object>> companionStatus(@PathVariable String companionId) {
        return ApiResult.ok(Map.of("companion_id", companionId, "status", "idle"));
    }

    @PostMapping("/companions/{companionId}/figure")
    public ApiResult<Map<String, Object>> setFigure(@PathVariable String companionId,
                                                     @RequestBody Map<String, Object> body) {
        return ApiResult.ok(body);
    }

    @GetMapping("/companions/{companionId}/companion/active")
    public ApiResult<Map<String, Object>> activeCompanion(@PathVariable String companionId) {
        return ApiResult.ok(Map.of("companion_id", companionId));
    }

    @GetMapping("/companions/{companionId}/companion/threads")
    public ApiResult<List<Map<String, Object>>> companionThreads(@PathVariable String companionId) {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/companions/{companionId}/skills/from-session")
    public ApiResult<Map<String, Object>> skillsFromSession(@PathVariable String companionId,
                                                             @RequestBody Map<String, Object> body) {
        return ApiResult.ok(body);
    }

    @PostMapping("/companions/{companionId}/learn/run")
    public ApiResult<Map<String, Object>> learnRun(@PathVariable String companionId,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("status", "completed"));
    }

    @GetMapping("/companions/{companionId}/weekly-digest")
    public ApiResult<Map<String, Object>> weeklyDigest(@PathVariable String companionId,
                                                        @RequestParam(required = false) Integer days) {
        return ApiResult.ok(Map.of("companion_id", companionId, "digest", ""));
    }

    @GetMapping("/companions/{companionId}/digests")
    public ApiResult<List<Map<String, Object>>> listDigests(@PathVariable String companionId,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer page_size) {
        return ApiResult.ok(List.of());
    }

    @GetMapping("/companions/{companionId}/history/days")
    public ApiResult<List<Map<String, Object>>> historyDays(@PathVariable String companionId) {
        return ApiResult.ok(List.of());
    }

    @GetMapping("/companions/{companionId}/skills")
    public ApiResult<List<Map<String, Object>>> listSkills(@PathVariable String companionId) {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/companions/{companionId}/skills")
    public ApiResult<Map<String, Object>> createSkill(@PathVariable String companionId,
                                                       @RequestBody Map<String, Object> body) {
        body.put("companion_skill_id", conversationStore.newId());
        return ApiResult.ok(body);
    }

    @PutMapping("/companions/{companionId}/skills/{skillId}")
    public ApiResult<Map<String, Object>> updateSkill(@PathVariable String companionId,
                                                       @PathVariable String skillId,
                                                       @RequestBody Map<String, Object> body) {
        body.put("companion_skill_id", skillId);
        return ApiResult.ok(body);
    }

    @DeleteMapping("/companions/{companionId}/skills/{skillId}")
    public ApiResult<Void> deleteSkill(@PathVariable String companionId,
                                        @PathVariable String skillId) {
        return ApiResult.ok();
    }

    @PostMapping("/companions/{companionId}/skills/{skillId}/decide")
    public ApiResult<Map<String, Object>> decideSkill(@PathVariable String companionId,
                                                       @PathVariable String skillId,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("decision", "accept"));
    }

    @GetMapping("/memories")
    public ApiResult<Map<String, Object>> listMemories(@RequestParam(required = false) String companion_id,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer page_size) {
        return ApiResult.ok(Map.of("items", List.of(), "total", 0));
    }

    @PostMapping("/memories")
    public ApiResult<Map<String, Object>> createMemory(@RequestBody Map<String, Object> body) {
        body.put("memory_id", conversationStore.newId());
        body.putIfAbsent("created_at", System.currentTimeMillis());
        return ApiResult.ok(body);
    }

    @PutMapping("/memories/{memoryId}")
    public ApiResult<Map<String, Object>> updateMemory(@PathVariable String memoryId,
                                                        @RequestBody Map<String, Object> body) {
        body.put("memory_id", memoryId);
        return ApiResult.ok(body);
    }

    @DeleteMapping("/memories/{memoryId}")
    public ApiResult<Void> deleteMemory(@PathVariable String memoryId,
                                         @RequestParam(required = false) String companion_id) {
        return ApiResult.ok();
    }

    @PostMapping("/memories/batch")
    public ApiResult<List<Map<String, Object>>> batchMemories(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/memories/merge-suggestions")
    public ApiResult<List<Map<String, Object>>> mergeSuggestions(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/memories/merge")
    public ApiResult<Map<String, Object>> mergeMemories(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("status", "merged"));
    }

    @GetMapping("/figures")
    public ApiResult<List<Map<String, Object>>> listFigures() {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/figures")
    public ApiResult<Map<String, Object>> createFigure(@RequestBody Map<String, Object> body) {
        body.put("figure_id", conversationStore.newId());
        return ApiResult.ok(body);
    }

    @PutMapping("/figures/{figureId}")
    public ApiResult<Map<String, Object>> updateFigure(@PathVariable String figureId,
                                                        @RequestBody Map<String, Object> body) {
        body.put("figure_id", figureId);
        return ApiResult.ok(body);
    }

    @DeleteMapping("/figures/{figureId}")
    public ApiResult<Void> deleteFigure(@PathVariable String figureId) {
        return ApiResult.ok();
    }

    @GetMapping("/events/stats")
    public ApiResult<List<Map<String, Object>>> eventStats() {
        return ApiResult.ok(List.of());
    }

    @GetMapping("/events/storage")
    public ApiResult<Map<String, Object>> eventStorage() {
        return ApiResult.ok(Map.of("total", 0, "used", 0));
    }

    @PostMapping("/consent")
    public ApiResult<Map<String, Object>> applyConsent(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(body);
    }

    @PostMapping("/disable-all")
    public ApiResult<Map<String, Object>> disableAll(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(body);
    }

    @GetMapping("/config")
    public ApiResult<Map<String, Object>> getConfig() {
        return ApiResult.ok(Map.of());
    }

    @PatchMapping("/config")
    public ApiResult<Map<String, Object>> patchConfig(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(body);
    }

    @PostMapping("/export/memory")
    public ApiResult<Map<String, Object>> exportMemory(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("exported", 0));
    }

    @PostMapping("/export/companions/{companionId}")
    public ApiResult<Map<String, Object>> exportCompanion(@PathVariable String companionId,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("exported", 0));
    }

    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importBundle(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("imported", 0));
    }
}
