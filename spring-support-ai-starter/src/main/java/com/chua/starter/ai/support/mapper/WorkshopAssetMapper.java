package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.WorkshopAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 创意工坊资产 Mapper。
 *
 * @author CH
 */
@Mapper
public interface WorkshopAssetMapper extends BaseMapper<WorkshopAsset> {

    /**
     * 按集合名称查询所有资产 ID，用于集合重命名时批量更新。
     *
     * @param collection 原集合名称
     * @return 资产 ID 列表
     */
    List<String> selectIdsByCollection(@Param("collection") String collection);

    /**
     * 按标签模糊搜索资产 ID（底层使用 MySQL JSON_SEARCH）。
     *
     * @param query 关键词
     * @return 资产 ID 列表
     */
    List<String> selectIdsByTags(@Param("query") String query);
}
