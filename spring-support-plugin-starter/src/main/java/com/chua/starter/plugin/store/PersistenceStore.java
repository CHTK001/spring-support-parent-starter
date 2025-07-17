package com.chua.starter.plugin.store;

import java.util.List;
import java.util.Optional;

/**
 * 持久化存储接口
 * 
 * @author CH
 * @since 2025/1/16
 */
public interface PersistenceStore<T, ID> extends Store {

    /**
     * 保存实体
     * 
     * @param entity 实体对象
     * @return 保存后的实体
     */
    T save(T entity);

    /**
     * 批量保存实体
     * 
     * @param entities 实体列表
     * @return 保存后的实体列表
     */
    List<T> saveAll(List<T> entities);

    /**
     * 根据ID查找实体
     * 
     * @param id 主键ID
     * @return 实体对象
     */
    Optional<T> findById(ID id);

    /**
     * 查找所有实体
     * 
     * @return 实体列表
     */
    List<T> findAll();

    /**
     * 根据条件查找实体
     * 
     * @param condition 查询条件
     * @return 实体列表
     */
    List<T> findByCondition(QueryCondition condition);

    /**
     * 根据ID删除实体
     * 
     * @param id 主键ID
     * @return 是否删除成功
     */
    boolean deleteById(ID id);

    /**
     * 删除实体
     * 
     * @param entity 实体对象
     * @return 是否删除成功
     */
    boolean delete(T entity);

    /**
     * 批量删除
     * 
     * @param ids ID列表
     * @return 删除的数量
     */
    int deleteByIds(List<ID> ids);

    /**
     * 根据条件删除
     * 
     * @param condition 删除条件
     * @return 删除的数量
     */
    int deleteByCondition(QueryCondition condition);

    /**
     * 检查实体是否存在
     * 
     * @param id 主键ID
     * @return 是否存在
     */
    boolean existsById(ID id);

    /**
     * 统计实体数量
     * 
     * @return 总数量
     */
    long count();

    /**
     * 根据条件统计数量
     * 
     * @param condition 查询条件
     * @return 数量
     */
    long countByCondition(QueryCondition condition);

    /**
     * 更新实体
     * 
     * @param entity 实体对象
     * @return 是否更新成功
     */
    boolean update(T entity);

    /**
     * 根据条件更新
     * 
     * @param updateData 更新数据
     * @param condition 更新条件
     * @return 更新的数量
     */
    int updateByCondition(UpdateData updateData, QueryCondition condition);

    /**
     * 分页查询
     * 
     * @param condition 查询条件
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<T> findPage(QueryCondition condition, int page, int size);

    /**
     * 初始化存储
     */
    void initialize();

    /**
     * 销毁存储
     */
    void destroy();

    /**
     * 获取存储类型
     * 
     * @return 存储类型
     */
    StoreType getStoreType();

    /**
     * 存储类型枚举
     */
    enum StoreType {
        MEMORY,
        SQLITE,
        DATASOURCE
    }
}
