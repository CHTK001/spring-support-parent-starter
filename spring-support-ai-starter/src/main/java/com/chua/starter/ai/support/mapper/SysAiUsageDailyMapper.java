package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * AI 用量日汇总 映射器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiUsageDailyMapper extends BaseMapper<SysAiUsageDaily> {

    /**
     * 汇总表已经聚合到哪一天（表为空时返回 {@code null}）。
     *
     * <p>用量统计优先读这张几百行的日汇总表；它只覆盖「最近一次聚合任务跑完的那天」，
     * 之后的数据还在明细表里。调用方按这个日期把区间切成「汇总段 + 增量段」。</p>
     *
     * @return 最大统计日期
     */
    @Select("SELECT MAX(sys_ai_usage_daily_stat_date) FROM sys_ai_usage_daily")
    LocalDate selectMaxStatDate();
}
