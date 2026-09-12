package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* AI app钥匙 映射器。
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface SysAiAppKeyMapper extends BaseMapper<SysAiAppKey> {

    /**
    * 按分组 标识 查询 app钥匙 列表。
    *
    * @param groupId 分组 标识
    * @return AppKey 列表
    * @param @Param("groupId" @参数("群体标识"
     */
    List<SysAiAppKey> selectByGroupId(@Param("groupId") Integer groupId);

    /**
    * 按 app钥匙 查询。
    *
    * @param appKey app钥匙
    * @return AppKey 实体，不存在时返回 空
    * @param @Param("appKey" @参数("app键"
     */
    SysAiAppKey selectByAppKey(@Param("appKey") String appKey);

    /**
    * 原子自增使用次数，避免并发下丢失更新。
    *
    * <p>对应 SQL：{@code SET sys_ai_app_key_use_count = COALESCE(sys_ai_app_key_use_count, 0) + 1}</p>
    *
    * @param appKey app钥匙 值
    * @param @Param("appKey" @参数("app键"
     */
    void updateUseCount(@Param("appKey") String appKey);

    /**
    * 原子累加 令牌 消耗量，避免并发下丢失更新。
    *
    * <p>对应 SQL：{@code SET sys_ai_app_key_total_tokens = COALESCE(sys_ai_app_key_total_tokens, 0) + #{tokens}}</p>
    *
    * @param appKey  app钥匙 值
    * @param tokens  本次消耗的 令牌 数
    * @param @Param("appKey" @参数("app键"
     */
    void addTokenQuota(@Param("appKey") String appKey, @Param("tokens") long tokens);
}
