package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * sys_作业_cluster 映射器。
 *
 * <p>选主的三条写语句含「条件更新（CAS）」语义，落在对应 XML 里：
 * 用 SQL 表达比在 Java 里拼 Wrapper 更可读，也符合「SQL 只写 Mapper.xml」的规约。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface ClusterMapper extends BaseMapper<ClusterEntity> {

    /**
     * 抢占租约（CAS）：无人持有 / 持有者是自己 / 租约已过期 三种情况可抢。
     *
     * @param group        命名空间
     * @param nodeId       节点标识
     * @param leaseSeconds 租约时长（秒）
     * @return 影响行数，1 表示抢到
     */
    int tryAcquire(@Param("group") String group, @Param("nodeId") String nodeId,
                   @Param("leaseSeconds") int leaseSeconds);

    /**
     * 续约：仅当自己仍是持有者时推进租约。
     *
     * @param group        命名空间
     * @param nodeId       节点标识
     * @param leaseSeconds 租约时长（秒）
     * @return 影响行数，1 表示续约成功
     */
    int renew(@Param("group") String group, @Param("nodeId") String nodeId,
              @Param("leaseSeconds") int leaseSeconds);

    /**
     * 释放：仅当自己仍是持有者时清空。
     *
     * @param group  命名空间
     * @param nodeId 节点标识
     * @return 影响行数
     */
    int release(@Param("group") String group, @Param("nodeId") String nodeId);
}