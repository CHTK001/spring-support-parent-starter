package com.chua.starter.mybatis.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.MybatisUtils;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.interfaces.MPJBaseJoin;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 在 MyBatis Plus 的 基础映射器 的基础上拓展，提供更多的能力
 *
 * 1. {@link BaseMapper} 为 MyBatis Plus 的基础接口，提供基础的 CRUD 能力
 * 2. {@link MPJBaseMapper} 为 MyBatis Plus 连接 的基础接口，提供连表 连接 能力
 * @author CH
 * @since 4.0.0
 */
public interface BaseMapperX<T> extends MPJBaseMapper<T> {

    /**
     * 选择Page
     *
     * @param pageParam page参数
     * @param @Param("ew" @参数("ew"
     * @return 选择page的结果
     * @param queryWrapper 查询Wrapper，不允许为 null
     */
    default PageResult<T> selectPage(Query<T> pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        // 特殊：不分页，直接查询全部
        if (Query.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            List<T> list = selectList(queryWrapper);
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> tempPage = MybatisUtils.buildPage(pageParam);
            tempPage.setRecords(list);
            tempPage.setTotal(list.size());
            return PageResult.copy(tempPage);
        }

 // MyBatis Plus 查询
        IPage<T> mpPage = MybatisUtils.buildPage(pageParam);
        /**
         * 选择page。
         * @param mpPage mppage
         * @param queryWrapper 查询包装器
         */
        selectPage(mpPage, queryWrapper);
        // 转换返回
        return PageResult.copy(mpPage);
    }

    /**
     * 选择合并Page
     *
     * @param pageParam page参数
     * @param resultTypeClass 结果类型类
     * @param joinQueryWrapper 连接查询包装器
     * @return 选择连接page的结果
     */
    default <DTO> PageResult<DTO> selectJoinPage(Query<T> pageParam, Class<DTO> resultTypeClass, MPJBaseJoin<T> joinQueryWrapper) {
        IPage<DTO> mpPage = MybatisUtils.buildPage(pageParam);
        /**
         * 选择连接page。
         * @param mpPage mppage
         * @param resultTypeClass 结果类型类
         * @param joinQueryWrapper 连接查询包装器
         */
        selectJoinPage(mpPage, resultTypeClass, joinQueryWrapper);
        // 转换返回
        return PageResult.copy(mpPage);
    }

    /**
     * 选择One
     *
     * @param field 字段
     * @param value 值
     * @return 选择one的结果
     */
    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 选择One
     *
     * @param field 字段
     * @param value 值
     * @return 选择one的结果
     */
    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 选择One
     *
     * @param field1 字段1
     * @param value1 值1
     * @param field2 字段2
     * @param value2 值2
     * @return 选择one的结果
     */
    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 选择One
     *
     * @param field1 字段1
     * @param value1 值1
     * @param field2 字段2
     * @param value2 值2
     * @return 选择one的结果
     */
    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 选择One
     * @param field1 字段1
     * @param value1 值1
     * @param field2 字段2
     * @param value2 值2
     * @param field3 字段3
     * @param value3 值3
     */
    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
                        SFunction<T, ?> field3, Object value3) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2)
                .eq(field3, value3));
    }

    /**
     * 选择计算数量
     *
     * @return 选择数量的结果
     */
    default Long selectCount() {
        return selectCount(new QueryWrapper<>());
    }

    /**
     * 选择计算数量
     *
     * @param field 字段
     * @param value 值
     * @return 选择数量的结果
     */
    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 选择计算数量
     *
     * @param field 字段
     * @param value 值
     * @return 选择数量的结果
     */
    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 选择列表
     *
     * @return 选择列表的结果
     */
    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    /**
     * 选择列表
     *
     * @param field 字段
     * @param value 值
     * @return 选择列表的结果
     */
    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 选择列表
     *
     * @param field 字段
     * @param value 值
     * @return 选择列表的结果
     */
    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 选择列表
     *
     * @param field 字段
     * @param values 值
     * @return 选择列表的结果
     */
    default List<T> selectList(String field, Collection<?> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    /**
     * 选择列表
     *
     * @param field 字段
     * @param values 值
     * @return 选择列表的结果
     */
    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    @Deprecated
    /**
     * 选择列表
     *
     * @param leField le字段
     * @param geField ge字段
     * @param value 值
     * @return 选择列表的结果
     */
    default List<T> selectList(SFunction<T, ?> leField, SFunction<T, ?> geField, Object value) {
        return selectList(new LambdaQueryWrapper<T>().le(leField, value).ge(geField, value));
    }

    /**
     * 选择列表
     *
     * @param field1 字段1
     * @param value1 值1
     * @param field2 字段2
     * @param value2 值2
     * @return 选择列表的结果
     */
    default List<T> selectList(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 批量插入，适合大量数据插入
     *
     * @param entities 实体们
     * @return 插入batch的结果
     */
    default Boolean insertBatch(Collection<T> entities) {
        return Db.saveBatch(entities);
    }

    /**
     * 批量插入，适合大量数据插入
     *
     * @param entities 实体们
     * @param size     插入数量 Db.保存批量 默认为 1000
     * @return 插入batch的结果
     */
    default Boolean insertBatch(Collection<T> entities, int size) {
        return Db.saveBatch(entities, size);
    }

    /**
     * 更新批量
     *
     * @param update 更新
     * @return 更新batch的结果
     */
    default int updateBatch(T update) {
        return update(update, new QueryWrapper<>());
    }

    /**
     * 更新批量
     *
     * @param entities 实体
     * @return 更新batch的结果
     */
    default Boolean updateBatch(Collection<T> entities) {
        return Db.updateBatchById(entities);
    }

    /**
     * 更新批量
     *
     * @param entities 实体
     * @param size 大小
     * @return 更新batch的结果
     */
    default Boolean updateBatch(Collection<T> entities, int size) {
        return Db.updateBatchById(entities, size);
    }

    /**
     * 插入或更新
     *
     * @param entity 实体
     * @return 插入or更新的结果
     */
    default boolean insertOrUpdate(T entity) {
        return  Db.saveOrUpdate(entity);
    }

    /**
     * 插入或更新批量
     *
     * @param collection 集合
     * @return 插入or更新batch的结果
     */
    default Boolean insertOrUpdateBatch(Collection<T> collection) {
        return Db.saveOrUpdateBatch(collection);
    }

    /**
     * 删除
     *
     * @param field 字段
     * @param value 值
     * @return 删除的结果
     */
    default int delete(String field, String value) {
        return delete(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 删除
     *
     * @param field 字段
     * @param value 值
     * @return 删除的结果
     */
    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }

}

