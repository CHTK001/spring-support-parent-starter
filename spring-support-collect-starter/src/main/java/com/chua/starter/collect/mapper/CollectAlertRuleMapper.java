package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectAlertRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集告警规则 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectAlertRuleMapper extends BaseMapper<CollectAlertRule> {
}
