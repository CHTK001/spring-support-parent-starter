package com.chua.starter.mybatis.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.mybatis.utils.MybatisUtils;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.interfaces.MPJBaseJoin;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 在 MyBatis Plus 的 BaseMapper 的基础上拓展，提供更多的能力
 *
 * 1. {@link BaseMapper} 为 MyBatis Plus 的基础接口，提供基础的 CRUD 能力
 * 2. {@link MPJBaseMapper} 为 MyBatis Plus Join 的基础接口，提供连表 Join 能力
 */
public interface BaseMapperX<T> extends MPJBaseMapper<T> {

    default PageResult<T> selectPage(PageRequest<T> pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        // 特殊：不分页，直接查询全部
        if (PageRequest.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            List<T> list = selectList(queryWrapper);
            return PageResult.<T>builder()
                    .records(list)
                    .pageSize(pageParam.getPageSize())
                    .pageNo(pageParam.getPage())
                    .total(list.size())
                    .build();
        }

        // MyBatis Plus 查询
        IPage<T> mpPage = MybatisUtils.buildPage(pageParam);
        selectPage(mpPage, queryWrapper);
        // 转换返回
        return PageResult.<T>builder()
                .records(mpPage.getRecords())
                .pageSize(pageParam.getPageSize())
                .pageNo(pageParam.getPage())
                .total(mpPage.getTotal())
                .build();
    }

    default <DTO> PageResult<DTO> selectJoinPage(PageRequest<T> pageParam, Class<DTO> resultTypeClass, MPJBaseJoin<T> joinQueryWrapper) {
        IPage<DTO> mpPage = MybatisUtils.buildPage(pageParam);
        selectJoinPage(mpPage, resultTypeClass, joinQueryWrapper);
        // 转换返回
        return PageResult.<DTO>builder()
                .records(mpPage.getRecords())
                .pageSize(pageParam.getPageSize())
                .pageNo(pageParam.getPage())
                .total(mpPage.getTotal())
                .build();
    }

    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
                        SFunction<T, ?> field3, Object value3) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2)
                .eq(field3, value3));
    }

    default Long selectCount() {
        return selectCount(new QueryWrapper<>());
    }

    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(String field, Collection<?> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    @Deprecated
    default List<T> selectList(SFunction<T, ?> leField, SFunction<T, ?> geField, Object value) {
        return selectList(new LambdaQueryWrapper<T>().le(leField, value).ge(geField, value));
    }

    default List<T> selectList(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 批量插入，适合大量数据插入
     *
     * @param entities 实体们
     */
    default Boolean insertBatch(Collection<T> entities) {
        return Db.saveBatch(entities);
    }

    /**
     * 批量插入，适合大量数据插入
     *
     * @param entities 实体们
     * @param size     插入数量 Db.saveBatch 默认为 1000
     */
    default Boolean insertBatch(Collection<T> entities, int size) {
        return Db.saveBatch(entities, size);
    }

    default int updateBatch(T update) {
        return update(update, new QueryWrapper<>());
    }

    default Boolean updateBatch(Collection<T> entities) {
        return Db.updateBatchById(entities);
    }

    default Boolean updateBatch(Collection<T> entities, int size) {
        return Db.updateBatchById(entities, size);
    }

    default Boolean insertOrUpdate(T entity) {
        return  Db.saveOrUpdate(entity);
    }

    default Boolean insertOrUpdateBatch(Collection<T> collection) {
        return Db.saveOrUpdateBatch(collection);
    }

    default int delete(String field, String value) {
        return delete(new QueryWrapper<T>().eq(field, value));
    }

    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }

}
