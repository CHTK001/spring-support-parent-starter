package com.chua.starter.plugin.repository;

import com.chua.starter.plugin.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 限流配置Repository
 * 
 * @author CH
 * @since 2025/1/16
 */
@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    /**
     * 根据限流类型和限流键查找配置
     * 
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 限流配置
     */
    Optional<RateLimitConfig> findByLimitTypeAndLimitKey(
        RateLimitConfig.LimitType limitType, 
        String limitKey
    );

    /**
     * 查找所有启用的配置
     * 
     * @return 启用的配置列表
     */
    List<RateLimitConfig> findByEnabledTrue();

    /**
     * 根据限流类型查找启用的配置
     * 
     * @param limitType 限流类型
     * @return 配置列表
     */
    List<RateLimitConfig> findByLimitTypeAndEnabledTrue(RateLimitConfig.LimitType limitType);

    /**
     * 查找所有IP限流配置
     * 
     * @return IP限流配置列表
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.limitType = 'IP' ORDER BY c.updatedTime DESC")
    List<RateLimitConfig> findAllIpConfigs();

    /**
     * 查找所有API限流配置
     * 
     * @return API限流配置列表
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.limitType = 'API' ORDER BY c.updatedTime DESC")
    List<RateLimitConfig> findAllApiConfigs();

    /**
     * 根据QPS范围查找配置
     * 
     * @param minQps 最小QPS
     * @param maxQps 最大QPS
     * @return 配置列表
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.qps BETWEEN :minQps AND :maxQps ORDER BY c.qps")
    List<RateLimitConfig> findByQpsRange(@Param("minQps") Integer minQps, @Param("maxQps") Integer maxQps);

    /**
     * 根据算法类型查找配置
     * 
     * @param algorithmType 算法类型
     * @return 配置列表
     */
    List<RateLimitConfig> findByAlgorithmType(RateLimitConfig.AlgorithmType algorithmType);

    /**
     * 模糊查找限流键
     * 
     * @param keyword 关键词
     * @return 配置列表
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.limitKey LIKE %:keyword% ORDER BY c.updatedTime DESC")
    List<RateLimitConfig> findByLimitKeyContaining(@Param("keyword") String keyword);

    /**
     * 查找最近更新的配置
     * 
     * @param since 时间点
     * @return 配置列表
     */
    @Query("SELECT c FROM RateLimitConfig c WHERE c.updatedTime >= :since ORDER BY c.updatedTime DESC")
    List<RateLimitConfig> findRecentlyUpdated(@Param("since") LocalDateTime since);

    /**
     * 统计各种类型的配置数量
     * 
     * @param limitType 限流类型
     * @return 数量
     */
    long countByLimitType(RateLimitConfig.LimitType limitType);

    /**
     * 统计启用的配置数量
     * 
     * @return 数量
     */
    long countByEnabledTrue();

    /**
     * 批量启用配置
     * 
     * @param ids ID列表
     * @param updatedBy 更新者
     * @return 更新数量
     */
    @Modifying
    @Transactional
    @Query("UPDATE RateLimitConfig c SET c.enabled = true, c.updatedTime = :updateTime, c.updatedBy = :updatedBy WHERE c.id IN :ids")
    int batchEnable(@Param("ids") List<Long> ids, 
                   @Param("updateTime") LocalDateTime updateTime,
                   @Param("updatedBy") String updatedBy);

    /**
     * 批量禁用配置
     * 
     * @param ids ID列表
     * @param updatedBy 更新者
     * @return 更新数量
     */
    @Modifying
    @Transactional
    @Query("UPDATE RateLimitConfig c SET c.enabled = false, c.updatedTime = :updateTime, c.updatedBy = :updatedBy WHERE c.id IN :ids")
    int batchDisable(@Param("ids") List<Long> ids, 
                    @Param("updateTime") LocalDateTime updateTime,
                    @Param("updatedBy") String updatedBy);

    /**
     * 批量更新QPS
     * 
     * @param limitType 限流类型
     * @param qps 新的QPS值
     * @param updatedBy 更新者
     * @return 更新数量
     */
    @Modifying
    @Transactional
    @Query("UPDATE RateLimitConfig c SET c.qps = :qps, c.updatedTime = :updateTime, c.updatedBy = :updatedBy WHERE c.limitType = :limitType")
    int batchUpdateQpsByType(@Param("limitType") RateLimitConfig.LimitType limitType,
                            @Param("qps") Integer qps,
                            @Param("updateTime") LocalDateTime updateTime,
                            @Param("updatedBy") String updatedBy);

    /**
     * 删除指定类型的所有配置
     * 
     * @param limitType 限流类型
     * @return 删除数量
     */
    @Modifying
    @Transactional
    int deleteByLimitType(RateLimitConfig.LimitType limitType);

    /**
     * 删除禁用的配置
     * 
     * @return 删除数量
     */
    @Modifying
    @Transactional
    int deleteByEnabledFalse();

    /**
     * 检查配置是否存在
     * 
     * @param limitType 限流类型
     * @param limitKey 限流键
     * @return 是否存在
     */
    boolean existsByLimitTypeAndLimitKey(RateLimitConfig.LimitType limitType, String limitKey);

    /**
     * 获取所有唯一的限流键
     * 
     * @param limitType 限流类型
     * @return 限流键列表
     */
    @Query("SELECT DISTINCT c.limitKey FROM RateLimitConfig c WHERE c.limitType = :limitType AND c.enabled = true")
    List<String> findDistinctLimitKeysByType(@Param("limitType") RateLimitConfig.LimitType limitType);

    /**
     * 获取平均QPS
     * 
     * @param limitType 限流类型
     * @return 平均QPS
     */
    @Query("SELECT AVG(c.qps) FROM RateLimitConfig c WHERE c.limitType = :limitType AND c.enabled = true")
    Double getAverageQpsByType(@Param("limitType") RateLimitConfig.LimitType limitType);

    /**
     * 获取最大QPS
     * 
     * @param limitType 限流类型
     * @return 最大QPS
     */
    @Query("SELECT MAX(c.qps) FROM RateLimitConfig c WHERE c.limitType = :limitType AND c.enabled = true")
    Integer getMaxQpsByType(@Param("limitType") RateLimitConfig.LimitType limitType);

    /**
     * 获取最小QPS
     * 
     * @param limitType 限流类型
     * @return 最小QPS
     */
    @Query("SELECT MIN(c.qps) FROM RateLimitConfig c WHERE c.limitType = :limitType AND c.enabled = true")
    Integer getMinQpsByType(@Param("limitType") RateLimitConfig.LimitType limitType);
}
