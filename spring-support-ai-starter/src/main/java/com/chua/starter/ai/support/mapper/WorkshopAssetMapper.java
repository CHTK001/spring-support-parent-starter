package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.WorkshopAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* 创意工坊资产 映射器。
*
* @author CH
* @since 4.0.0
 */
@Mapper
public interface WorkshopAssetMapper extends BaseMapper<WorkshopAsset> {

    /**
    * 按集合名称查询资产 标识，用于集合重命名时批量更新。
    *
    * @param collection 原集合名称
    * @return 资产 标识 列表
    * @param @Param("collection" @参数("集合"
     */
    List<String> selectIdsByCollection(@Param("collection") String collection);

    /**
    * 按标签模糊查询资产 标识，底层使用 MySQL JSON_搜索。
    *
    * @param query 关键字
    * @return 资产 标识 列表
    * @param @Param("query" @参数("查询"
     */
    List<String> selectIdsByTags(@Param("query") String query);

    /**
    * 按过滤条件分页查询资产列表（SQL 在 workshopasset映射器.xml）。
    *
    * @param kind       资产类型，空/空 = 全部
    * @param collection 集合名称，空/空 = 全部
    * @param ungrouped  仅查未分组（1）或全部（0）
    * @param query      标题/标签模糊搜索
    * @param tag        精确标签
    * @param inLibrary  是否仅在库（1/0），空 = 全部
    * @param sort       排序（created_desc 等）
    * @param offset     偏移量
    * @param pageSize   每页数量
    * @return 资产列表
     */
    List<WorkshopAsset> selectPageByFilter(@Param("kind") String kind,
                                           @Param("collection") String collection,
                                           @Param("ungrouped") boolean ungrouped,
                                           @Param("query") String query,
                                           @Param("tag") String tag,
                                           @Param("inLibrary") Boolean inLibrary,
                                           @Param("sort") String sort,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    /**
    * 按过滤条件统计资产总数（SQL 在 workshopasset映射器.xml）。
    *
    * @param kind       资产类型，空/空 = 全部
    * @param collection 集合名称，空/空 = 全部
    * @param ungrouped  仅查未分组（1）或全部（0）
    * @param query      标题/标签模糊搜索
    * @param tag        精确标签
    * @param inLibrary  是否仅在库（1/0），空 = 全部
    * @return 资产总数
    * @param @Param("kind" @参数("种类"
     */
    long countByFilter(@Param("kind") String kind,
                       @Param("collection") String collection,
                       @Param("ungrouped") boolean ungrouped,
                       @Param("query") String query,
                       @Param("tag") String tag,
                       @Param("inLibrary") Boolean inLibrary);

    /**
    * 查询所有非空集合名称（去重、非空）。
    *
    * @return 集合名称列表
     */
    List<String> selectDistinctCollections();

    /**
    * 按主键更新资产元数据（标题/集合/标签/入_图书馆/更新时间）。
    *
    * @param assetId    资产 标识
    * @param title      新标题，空 不更新
    * @param collection 新集合，空 不更新，空字符串置 空（移出分组）
    * @param tagsJson   新标签 JSON 数组字符串，空 不更新
    * @param inLibrary  是否在库，空 不更新
    * @param updatedAt  更新时间戳（轮次 ms）
    * @return 更新行数
    * @param @Param("assetId" @参数("assetid"
     */
    int updateMeta(@Param("assetId") String assetId,
                   @Param("title") String title,
                   @Param("collection") String collection,
                   @Param("tagsJson") String tagsJson,
                   @Param("inLibrary") Boolean inLibrary,
                   @Param("updatedAt") long updatedAt);

    /**
    * 批量重命名集合：把所有使用 从 的资产集合改为 转为（转为 为 空/空 表示移出分组）。
    *
    * @param from 原集合名
    * @param to   新集合名（空/空 = 移出分组）
    * @return 更新行数
    * @param @Param("from" @参数("从"
     */
    int renameCollection(@Param("from") String from, @Param("to") String to);
}
