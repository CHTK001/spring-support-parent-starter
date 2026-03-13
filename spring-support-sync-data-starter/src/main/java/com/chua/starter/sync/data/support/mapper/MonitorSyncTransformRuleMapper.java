package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncTransformRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据转换规则 Mapper 接口
 *
 * @author System
 * @since 2026/03/09
 */
@Mapper
public interface MonitorSyncTransformRuleMapper extends BaseMapper<MonitorSyncTransformRule> {

    /**
     * 根据规则类型查询转换规则
     *
     * @param ruleType 规则类型
     * @return 转换规则列表
     */
    @Select("SELECT * FROM monitor_sync_transform_rule WHERE rule_type = #{ruleType} ORDER BY create_time DESC")
    List<MonitorSyncTransformRule> selectByRuleType(@Param("ruleType") String ruleType);
}
