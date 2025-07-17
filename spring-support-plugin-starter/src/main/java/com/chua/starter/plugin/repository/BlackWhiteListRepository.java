package com.chua.starter.plugin.repository;

import com.chua.starter.plugin.entity.BlackWhiteList;
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
 * 黑白名单Repository
 * 
 * @author CH
 * @since 2025/1/16
 */
@Repository
public interface BlackWhiteListRepository extends JpaRepository<BlackWhiteList, Long> {

    /**
     * 根据类型和值查找
     * 
     * @param listType 名单类型
     * @param listValue 名单值
     * @return 黑白名单条目
     */
    Optional<BlackWhiteList> findByListTypeAndListValue(
        BlackWhiteList.ListType listType, 
        String listValue
    );

    /**
     * 根据类型查找所有启用的条目
     * 
     * @param listType 名单类型
     * @return 条目列表
     */
    List<BlackWhiteList> findByListTypeAndEnabledTrueOrderByPriorityAsc(
        BlackWhiteList.ListType listType
    );

    /**
     * 查找所有启用的黑名单
     * 
     * @return 黑名单列表
     */
    @Query("SELECT b FROM BlackWhiteList b WHERE b.listType = 'BLACKLIST' AND b.enabled = true ORDER BY b.priority ASC")
    List<BlackWhiteList> findEnabledBlacklist();

    /**
     * 查找所有启用的白名单
     * 
     * @return 白名单列表
     */
    @Query("SELECT b FROM BlackWhiteList b WHERE b.listType = 'WHITELIST' AND b.enabled = true ORDER BY b.priority ASC")
    List<BlackWhiteList> findEnabledWhitelist();

    /**
     * 根据匹配类型查找
     * 
     * @param listType 名单类型
     * @param matchType 匹配类型
     * @return 条目列表
     */
    List<BlackWhiteList> findByListTypeAndMatchTypeAndEnabledTrue(
        BlackWhiteList.ListType listType,
        BlackWhiteList.MatchType matchType
    );

    /**
     * 检查是否存在
     * 
     * @param listType 名单类型
     * @param listValue 名单值
     * @return 是否存在
     */
    boolean existsByListTypeAndListValue(
        BlackWhiteList.ListType listType, 
        String listValue
    );

    /**
     * 批量启用
     * 
     * @param ids ID列表
     * @param updateTime 更新时间
     * @return 更新数量
     */
    @Modifying
    @Transactional
    @Query("UPDATE BlackWhiteList b SET b.enabled = true, b.updatedTime = :updateTime WHERE b.id IN :ids")
    int batchEnable(@Param("ids") List<Long> ids, @Param("updateTime") LocalDateTime updateTime);

    /**
     * 批量禁用
     * 
     * @param ids ID列表
     * @param updateTime 更新时间
     * @return 更新数量
     */
    @Modifying
    @Transactional
    @Query("UPDATE BlackWhiteList b SET b.enabled = false, b.updatedTime = :updateTime WHERE b.id IN :ids")
    int batchDisable(@Param("ids") List<Long> ids, @Param("updateTime") LocalDateTime updateTime);

    /**
     * 删除过期条目
     * 
     * @param now 当前时间
     * @return 删除数量
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM BlackWhiteList b WHERE b.expireTime IS NOT NULL AND b.expireTime < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * 根据类型删除所有条目
     * 
     * @param listType 名单类型
     * @return 删除数量
     */
    @Modifying
    @Transactional
    int deleteByListType(BlackWhiteList.ListType listType);

    /**
     * 统计各类型数量
     * 
     * @param listType 名单类型
     * @return 数量
     */
    long countByListType(BlackWhiteList.ListType listType);

    /**
     * 统计启用的条目数量
     * 
     * @param listType 名单类型
     * @return 数量
     */
    long countByListTypeAndEnabledTrue(BlackWhiteList.ListType listType);

    /**
     * 查找即将过期的条目
     * 
     * @param beforeTime 时间点
     * @return 条目列表
     */
    @Query("SELECT b FROM BlackWhiteList b WHERE b.expireTime IS NOT NULL AND b.expireTime BETWEEN :now AND :beforeTime ORDER BY b.expireTime ASC")
    List<BlackWhiteList> findExpiringBefore(@Param("now") LocalDateTime now, @Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 模糊查找
     * 
     * @param keyword 关键词
     * @return 条目列表
     */
    @Query("SELECT b FROM BlackWhiteList b WHERE b.listValue LIKE %:keyword% OR b.description LIKE %:keyword% ORDER BY b.updatedTime DESC")
    List<BlackWhiteList> findByKeyword(@Param("keyword") String keyword);
}
