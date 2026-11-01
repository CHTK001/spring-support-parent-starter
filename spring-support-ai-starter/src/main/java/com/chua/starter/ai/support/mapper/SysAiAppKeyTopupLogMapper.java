package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiAppKeyTopupLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI appKey 充值/消费流水映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiAppKeyTopupLogMapper extends BaseMapper<SysAiAppKeyTopupLog> {

    /**
     * 查询 appKey 的充值/消费流水（按流水时间倒序）。
     *
     * @param appKey appKey
     * @return 流水列表
     */
    List<SysAiAppKeyTopupLog> selectByAppKey(@Param("appKey") String appKey);
}
