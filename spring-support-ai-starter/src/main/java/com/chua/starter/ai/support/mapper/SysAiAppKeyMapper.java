package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI AppKey Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiAppKeyMapper extends BaseMapper<SysAiAppKey> {

    /**
     * 按分组 ID 查询 AppKey 列表。
     *
     * @param groupId 分组 ID
     * @return AppKey 列表
     */
    List<SysAiAppKey> selectByGroupId(@Param("groupId") Integer groupId);

    /**
     * 按 AppKey 查询。
     *
     * @param appKey AppKey
     * @return AppKey 实体，不存在时返回 null
     */
    SysAiAppKey selectByAppKey(@Param("appKey") String appKey);

    /**
     * 原子自增使用次数。
     *
     * @param appKey AppKey
     */
    void updateUseCount(@Param("appKey") String appKey);

    /**
     * 原子累加 Token 消耗量。
     *
     * @param appKey  AppKey
     * @param tokens  本次消耗 Token 数
     */
    void addTokenQuota(@Param("appKey") String appKey, @Param("tokens") long tokens);
}
