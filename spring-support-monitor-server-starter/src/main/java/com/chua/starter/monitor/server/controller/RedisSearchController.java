package com.chua.starter.monitor.server.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.search.SearchQuery;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.starter.monitor.server.pojo.JvmSearchQuery;
import com.chua.starter.redis.support.service.RedisSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_SEARCH_MONITOR_REPORT_PREFIX;
import static com.chua.starter.redis.support.service.impl.RedisSearchServiceImpl.LANGUAGE;

/**
 * 终端
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/search")
@Tag(name = "检索信息")
@RequiredArgsConstructor
@Getter
public class RedisSearchController {

    private final RedisSearchService redisSearchService;

        // 检查ID是否为空
        if(StringUtils.isEmpty(jvmQuery.getId())) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        String key = REDIS_SEARCH_MONITOR_REPORT_PREFIX +
                jvmQuery.getAppName() + ":" +
                jvmQuery.getServerHost() + "_" +
                jvmQuery.getServerPort() + ":" +
                jvmQuery.getType();

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setIndex(key);
        searchQuery.setLanguage(LANGUAGE);
        searchQuery.setKeyword(jvmQuery.getKeyword());
        searchQuery.setSort("timestamp");
        return redisSearchService.queryAll(searchQuery, jvmQuery.getOffset(), jvmQuery.getCount());
    }
}
