package com.chua.report.server.starter.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.TimeIndicator;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.redis.support.search.SearchQuery;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.report.server.starter.pojo.IndicatorQuery;
import com.chua.starter.redis.support.service.RedisSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * 全文检索信息
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/search")
@Tag(name = "全文检索信息")
@RequiredArgsConstructor
@Getter
public class RedisSearchController {

    private final RedisSearchService redisSearchService;
    /**
     * 查询基本信息。
     *
     * @param indicatorQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询指标信息")
    @GetMapping("series")
    public ReturnResult<SearchResultItem> list(IndicatorQuery indicatorQuery) {
        SearchQuery query = new SearchQuery();
        query.setIndex(RedisConstant.REDIS_SEARCH_PREFIX + indicatorQuery.getName());
        if(StringUtils.isNotBlank(indicatorQuery.getKeyword())) {
            query.setKeyword(indicatorQuery.getKeyword()+ StringUtils.format(" AND timestamp: [{} {}]", indicatorQuery.getFromTimestamp(), indicatorQuery.getToTimestamp()));
        } else {
            query.setKeyword(StringUtils.format("timestamp: [{} {}]", indicatorQuery.getFromTimestamp(), indicatorQuery.getToTimestamp()));
        }
        return redisSearchService.queryAll(query, indicatorQuery.getOffset(), indicatorQuery.getCount());
    }
}
