package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
* 预设标签控制器。
*
* <p>提供预设标签（PresetTag）的 CRUD，支持 audience 和 scenario 两个维度。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/preset-tags")
@RequiredArgsConstructor
public class ApiPresetTagController {

    private final SysAiConversationStore conversationStore; // conversation存储

    /**
    * 内存标签存储：preset_标签_标识 -> 标签数据
    */
    private final Map<String, Map<String, Object>> tags = new ConcurrentHashMap<>();

    @GetMapping
    /**
    * 列表。
    * @return 列表的结果
    */
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(tags.values()));
    }

    @PostMapping
    /**
    * 创建。
    * @param body 主体
    * @return 创建的结果
    */
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String tagId = String.valueOf(body.getOrDefault("preset_tag_id", conversationStore.newId()));
        body.put("preset_tag_id", tagId);
        body.putIfAbsent("key", "tag-" + tagId);
        body.putIfAbsent("dimension", "audience");
        body.putIfAbsent("created_at", System.currentTimeMillis());
        tags.put(tagId, body);
        return ApiResult.ok(body);
    }

    @PutMapping("/{tagId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String tagId,
                                                  @RequestBody Map<String, Object> body) {
        Map<String, Object> tag = tags.get(tagId);
        if (tag == null) {
            /**
            * api异常。
            * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
            * @param tagId 标签标识
            * @return api异常的结果
            */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设标签不存在: " + tagId);
        }
        body.forEach((k, v) -> {
            if (!"preset_tag_id".equals(k)) {
                tag.put(k, v);
            }
        });
        return ApiResult.ok(tag);
    }

    @DeleteMapping("/{tagId}")
    /**
    * 删除。
    * @param tagId 标签标识
    * @return 删除的结果
    */
    public ApiResult<Void> delete(@PathVariable String tagId) {
        Map<String, Object> removed = tags.remove(tagId);
        if (removed == null) {
            /**
            * api异常。
            * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
            * @param tagId 标签标识
            * @return api异常的结果
            */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设标签不存在: " + tagId);
        }
        return ApiResult.ok();
    }
}
