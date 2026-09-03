package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 用量 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiUsageMapper extends BaseMapper<SysAiUsage> {

    /**
     * 数据库端聚合统计——单条 SQL 完成 SUM/COUNT，配合索引秒级返回，避免全量装载到 JVM。
     *
     * @param groupName 分组名称（可为空，为空时不按分组过滤）
     * @param provider  服务商（可为空）
     * @param model     模型（可为空）
     * @param startTime 开始时间（可为空，不传则不限起始时间）
     * @param endTime   结束时间（可为空，不传则不限截止时间）
     * @return 聚合结果 Map，键名：totalCalls / totalPromptTokens / totalCompletionTokens /
     *         totalTokens / totalCost / totalCachedTokens / standardCost / totalLatency
     */
    Map<String, Object> selectStatistics(
            @Param("groupName") String groupName,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
