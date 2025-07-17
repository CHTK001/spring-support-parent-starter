package com.chua.starter.plugin.repository;

import com.chua.starter.plugin.entity.RateLimitRecord;
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
 * 限流记录Repository
 * 
 * @author CH
 * @since 2025/1/16
 */
@Repository
public interface RateLimitRecordRepository extends JpaRepository<RateLimitRecord, Long> {

    /**
     * 根据限流键和时间窗口查找记录
     * 
     * @param limitKey 限流键
     * @param windowStart 窗口开始时间
     * @param windowEnd 窗口结束时间
     * @return 限流记录
     */
    @Query("SELECT r FROM RateLimitRecord r WHERE r.limitKey = :limitKey " +
           "AND r.windowStart <= :windowEnd AND r.windowEnd >= :windowStart")
    Optional<RateLimitRecord> findByLimitKeyAndWindow(
        @Param("limitKey") String limitKey,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    /**
     * 根据IP地址查找最近的限流记录
     * 
     * @param ipAddress IP地址
     * @param limit 限制数量
     * @return 限流记录列表
     */
    @Query("SELECT r FROM RateLimitRecord r WHERE r.ipAddress = :ipAddress " +
           "ORDER BY r.createdTime DESC")
    List<RateLimitRecord> findRecentByIpAddress(@Param("ipAddress") String ipAddress, 
                                               @Param("limit") int limit);

    /**
     * 根据API路径查找最近的限流记录
     * 
     * @param apiPath API路径
     * @param limit 限制数量
     * @return 限流记录列表
     */
    @Query("SELECT r FROM RateLimitRecord r WHERE r.apiPath = :apiPath " +
           "ORDER BY r.createdTime DESC")
    List<RateLimitRecord> findRecentByApiPath(@Param("apiPath") String apiPath, 
                                             @Param("limit") int limit);

    /**
     * 查找指定时间范围内的限流记录
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 限流记录列表
     */
    @Query("SELECT r FROM RateLimitRecord r WHERE r.createdTime BETWEEN :startTime AND :endTime " +
           "ORDER BY r.createdTime DESC")
    List<RateLimitRecord> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 查找被限流的记录
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 被限流的记录列表
     */
    @Query("SELECT r FROM RateLimitRecord r WHERE r.isLimited = true " +
           "AND r.createdTime BETWEEN :startTime AND :endTime " +
           "ORDER BY r.createdTime DESC")
    List<RateLimitRecord> findLimitedRecords(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内的请求总数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 请求总数
     */
    @Query("SELECT COALESCE(SUM(r.requestCount), 0) FROM RateLimitRecord r " +
           "WHERE r.createdTime BETWEEN :startTime AND :endTime")
    Long countRequestsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内被限流的请求数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 被限流的请求数
     */
    @Query("SELECT COUNT(r) FROM RateLimitRecord r WHERE r.isLimited = true " +
           "AND r.createdTime BETWEEN :startTime AND :endTime")
    Long countLimitedRequestsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 根据IP地址统计请求数
     * 
     * @param ipAddress IP地址
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 请求数
     */
    @Query("SELECT COALESCE(SUM(r.requestCount), 0) FROM RateLimitRecord r " +
           "WHERE r.ipAddress = :ipAddress AND r.createdTime BETWEEN :startTime AND :endTime")
    Long countRequestsByIpAndTimeRange(@Param("ipAddress") String ipAddress,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 根据API路径统计请求数
     * 
     * @param apiPath API路径
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 请求数
     */
    @Query("SELECT COALESCE(SUM(r.requestCount), 0) FROM RateLimitRecord r " +
           "WHERE r.apiPath = :apiPath AND r.createdTime BETWEEN :startTime AND :endTime")
    Long countRequestsByApiAndTimeRange(@Param("apiPath") String apiPath,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 删除过期的记录
     * 
     * @param expireTime 过期时间
     * @return 删除的记录数
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RateLimitRecord r WHERE r.expireTime IS NOT NULL AND r.expireTime < :expireTime")
    int deleteExpiredRecords(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 删除指定时间之前的记录
     * 
     * @param beforeTime 时间点
     * @return 删除的记录数
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RateLimitRecord r WHERE r.createdTime < :beforeTime")
    int deleteRecordsBefore(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 清理指定天数之前的记录
     * 
     * @param days 天数
     * @return 删除的记录数
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RateLimitRecord r WHERE r.createdTime < :cutoffTime")
    int cleanupOldRecords(@Param("cutoffTime") LocalDateTime cutoffTime);
}
