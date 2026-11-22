// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.service.SysAiNewsService;
import com.chua.starter.ai.support.manage.store.SysAiNews;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AIHOT 精选资讯端点：查询最近资讯与手动触发同步。
 *
 * <p>模型管理页右侧资讯面板走 {@code GET /api/ai-news/latest}（发布时间倒序）；
 * 管理员可 {@code POST /api/ai-news/sync} 手动同步 AIHOT RSS（按 link 去重 upsert）。
 * 资讯表为空时，latest 会懒同步一次（限频），避免新库面板永远空白。</p>
 *
 * @since 4.0.0.70
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/ai-news")
@RequiredArgsConstructor
@Slf4j
public class ApiAiNewsController {

    /**
     * 兜底返回条数（limit 非法时使用）
     */
    private static final int DEFAULT_LIMIT = 20;

    /**
     * 资讯同步与查询服务
     */
    private final SysAiNewsService newsService;

    /**
     * 最近 AIHOT 资讯（发布时间倒序）。
     *
     * @param limit 条数（1~100，非法取 20）
     * @return 资讯列表（可能为空：来源不可达且库中无历史，此时前端面板整体不渲染）
     */
    @GetMapping("/latest")
    public ApiResult<List<SysAiNews>> latest(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        int safe = limit <= 0 ? DEFAULT_LIMIT : limit;
        return ApiResult.ok(newsService.latestWithBootstrap(safe));
    }

    /**
     * 手动触发 AIHOT 资讯同步（按 link 去重 upsert）。仅限管理员。
     *
     * @return {total, inserted, updated}
     */
    @Permission("admin")
    @PostMapping("/sync")
    public ApiResult<Map<String, Integer>> sync() {
        log.info("[AiNews] 手动触发 AIHOT 资讯同步");
        return ApiResult.ok(newsService.syncFromAiHot());
    }
}
