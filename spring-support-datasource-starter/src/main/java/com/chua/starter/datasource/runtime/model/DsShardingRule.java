package com.chua.starter.datasource.runtime.model;

import lombok.Data;

import java.util.List;

/**
 * 分库分表规则实体（表 ds_sharding_rule）。
 *
 * <p>一条记录 = 一个逻辑表的分片规则；分库成员连接参数由前端从数据源台账
 * 展开后存入 databases JSON（也支持引用已注册数据源名），运行期由本模块
 * 构建/替换 ShardingSphere 数据源，不重启即生效。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class DsShardingRule {

    /**
     * 分片算法：MOD
     */
    public static final String ALGO_MOD = "MOD";

    /**
     * 分片算法：INTERVAL（按时间）
     */
    public static final String ALGO_INTERVAL = "INTERVAL";

    /**
     * 主键
     */
    private Long id;

    /**
     * 逻辑表名（同时作为物理表名前缀）
     */
    private String logicTable;

    /**
     * 分片列
     */
    private String shardingColumn;

    /**
     * 算法：MOD / INTERVAL
     */
    private String algorithm = ALGO_MOD;

    /**
     * 分片数（MOD 生效）
     */
    private Integer shardCount = 16;

    /**
     * 时间粒度：YEAR/QUARTER/MONTH/WEEK/DAY/HOUR（INTERVAL 生效）
     */
    private String timeUnit;

    /**
     * 时间下界（INTERVAL 生效，yyyy-MM-dd）
     */
    private String timeLower = "2000-01-01";

    /**
     * 时间上界（INTERVAL 生效，yyyy-MM-dd）
     */
    private String timeUpper = "2099-12-31";

    /**
     * 分库成员 JSON：[{name,url,username,password,driverClassName}] 或 ["已注册数据源名"]。
     * 注意：列名为 database_members（DATABASES 是 MySQL 保留字，不能作列名）。
     */
    private String databases;

    /**
     * 是否启用（0 否 1 是）
     */
    private Integer enabled = 1;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间（epoch millis）
     */
    private Long createdAt;

    /**
     * 更新时间（epoch millis）
     */
    private Long updatedAt;

    /**
     * 乐观锁版本号（更新成功时自增；保存须携带读取时的旧值，不匹配则拒绝写入）
     */
    private Integer version;

    /**
     * 反序列化后的分库成员（不落库，便于服务层/页面透传）
     */
    private List<ShardingDbMember> dbMemberList;
}
