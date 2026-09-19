package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * sys_作业_cluster 集群选主表实体。
 *
 * <p>字段以表名 {@code sys_job_cluster} 作为前缀;
 * 审计字段来自 {@link SysBase}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_cluster")
public class ClusterEntity extends SysBase implements Serializable {

    /**
     * 序列化版本
     */
    @Serial
    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 集群命名空间 (主键, 字符串)
     */
    @TableId(value = "sys_job_cluster_id", type = IdType.INPUT)
    /**
     * SYS作业clusterid
    */
    private String sysJobClusterId;

    /**
     * 当前 铅 节点 标识
     */
    @TableField("sys_job_cluster_leader_node")
    /**
     * SYS作业clusterleader节点
    */
    private String sysJobClusterLeaderNode;

    /**
     * 租约到期时间戳 (毫秒)
     */
    @TableField("sys_job_cluster_lease_until_ms")
    /**
     * SYS作业clusterleaseuntilms
    */
    private Long sysJobClusterLeaseUntilMs;

    /**
     * 乐观锁版本 (续约 +1)
     */
    @TableField("sys_job_cluster_version")
    /**
     * SYS作业cluster版本
    */
    private Long sysJobClusterVersion;
}
