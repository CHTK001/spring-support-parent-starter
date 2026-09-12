package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import org.apache.ibatis.annotations.Mapper;

/**
* AI 用量日汇总 映射器
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface SysAiUsageDailyMapper extends BaseMapper<SysAiUsageDaily> {
}
