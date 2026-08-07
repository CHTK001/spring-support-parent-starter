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
 * sys_job_cluster 集群选主表实体。
 *
 * <p>字段以表名 {@code sys_job_cluster} 作为前缀;
 * 审计字段来自 {@link SysBase}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_cluster")
public class ClusterEntity extends SysBase implements Serializable {

    /**
     * 序列化版本
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 集群命名空间 (主键, 字符串)
     */
    @TableId(value = "sys_job_cluster_id", type = IdType.INPUT)
    private String sysJobClusterId;

    /**
     * 当前 Leader 节点 ID
     */
    @TableField("sys_job_cluster_leader_node")
    private String sysJobClusterLeaderNode;

    /**
     * 租约到期时间戳 (毫秒)
     */
    @TableField("sys_job_cluster_lease_until_ms")
    private Long sysJobClusterLeaseUntilMs;

    /**
     * 乐观锁版本 (续约 +1)
     */
    @TableField("sys_job_cluster_version")
    private Long sysJobClusterVersion;
}