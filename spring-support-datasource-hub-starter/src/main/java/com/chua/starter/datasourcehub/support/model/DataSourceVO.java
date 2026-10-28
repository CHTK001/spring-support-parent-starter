package com.chua.starter.datasourcehub.support.model;

/**
 * 数据源配置视图。
 *
 * @param id         数据源 ID
 * @param name       数据源名称
 * @param type       数据源类型（jdbc/redis/influxdb/neo4j/nacos/zookeeper/kafka/nginx/etcd/prometheus/mongodb）
 * @param host       主机地址
 * @param port       端口
 * @param database   库名
 * @param username   用户名
 * @param password   密码明文。<b>只在服务端内部使用</b>（中间件客户端要靠它建连），
 *                   接口出参一律用 {@link #masked()} 换成空串
 * @param remark     备注
 * @param status     状态，0 停用、1 启用
 * @param driverClassName JDBC 驱动类名
 * @param createTime 创建时间（ISO-8601 文本）
 * @param updateTime 更新时间（ISO-8601 文本）
 * @param connectLatencyMs 连接延迟（毫秒），从未测试过时为 null
 * @param sessionLatencyMs 会话延迟（毫秒），从未测试过时为 null
 * @param lastTestTime 最近一次测试时间（ISO-8601 文本）
 * @param lastTestOk 最近一次测试是否连通，null 表示从未测试
 * @param controlMode 进程控制方式：{@code systemd} / {@code local}；null 表示自动探测
 * @param controlTarget systemd 方式的托管单元名；null 或空表示自动探测
 * @param version 服务端大版本（当前 influxdb 使用：2/3）
 * @param passwordSet 是否已设置口令。页面靠它显示「已设置 / 未设置」，
 *                    也靠它决定编辑时要不要提示「留空表示不修改」
 * @param fastLoadEnabled 是否支持 CSV 快速导入（LOAD/COPY 直灌）。服务端按 JDBC
 *                        方言自动计算：MySQL / PostgreSQL 为 true，其余类型或无法判断为 null
 * @param topFlag 置顶标记：1 置顶、0 普通；列表按置顶优先排序
 * @author CH
 * @since 4.0.0.42
 */
public record DataSourceVO(
        Long id,
        String name,
        String type,
        String host,
        Integer port,
        String database,
        String username,
        String password,
        String remark,
        Integer status,
        String driverClassName,
        Long driverJarId,
        String createTime,
        String updateTime,
        Long connectLatencyMs,
        Long sessionLatencyMs,
        String lastTestTime,
        Integer lastTestOk,
        String controlMode,
        String controlTarget,
        String version,
        Boolean passwordSet,
        Boolean fastLoadEnabled,
        Integer topFlag
) {

    /**
     * 兼容旧签名的构造器（不带 {@code passwordSet}/{@code topFlag}）。
     *
     * <p>中间件客户端、控制台会用请求里的连接参数临时拼一个 ad-hoc 视图去建连，
     * 这些场景不关心「是否已设置口令」。保留这个重载让那些调用点一行都不用改。</p>
     *
     * @param id              数据源 ID
     * @param name            数据源名称
     * @param type            数据源类型
     * @param host            主机地址
     * @param port            端口
     * @param database        库名
     * @param username        用户名
     * @param password        密码明文
     * @param remark          备注
     * @param status          状态
     * @param driverClassName 驱动类名
     * @param driverJarId     驱动 jar ID
     * @param createTime      创建时间
     * @param updateTime      更新时间
     * @param connectLatencyMs 连接延迟
     * @param sessionLatencyMs 会话延迟
     * @param lastTestTime    最近测试时间
     * @param lastTestOk      最近测试结果
     * @param controlMode     控制方式
     * @param controlTarget   控制目标
     * @param version         服务端大版本
     */
    public DataSourceVO(Long id, String name, String type, String host, Integer port, String database,
                        String username, String password, String remark, Integer status,
                        String driverClassName, Long driverJarId, String createTime, String updateTime,
                        Long connectLatencyMs, Long sessionLatencyMs, String lastTestTime, Integer lastTestOk,
                        String controlMode, String controlTarget, String version) {
        this(id, name, type, host, port, database, username, password, remark, status,
                driverClassName, driverJarId, createTime, updateTime, connectLatencyMs, sessionLatencyMs,
                lastTestTime, lastTestOk, controlMode, controlTarget, version, null, null, null);
    }

    /**
     * 去掉口令明文的副本，供接口出参使用。
     *
     * <p>口令明文只该在服务端内部流转：中间件客户端要靠它建连，而浏览器不需要 ——
     * 把明文发给前端，等于让任何能打开这个页面的人（以及任何能读浏览器内存/日志的东西）
     * 拿到全平台数据库口令。出参统一走这里换成空串，另附 {@code passwordSet} 标记，
     * 页面据此显示「已设置」并提示编辑时留空即不修改。</p>
     *
     * @return 口令为空串的副本；本来就没口令时返回自身
     */
    public DataSourceVO masked() {
        if (password == null || password.isEmpty()) {
            return this;
        }
        return new DataSourceVO(id, name, type, host, port, database, username, "", remark, status,
                driverClassName, driverJarId, createTime, updateTime, connectLatencyMs, sessionLatencyMs,
                lastTestTime, lastTestOk, controlMode, controlTarget, version, passwordSet, fastLoadEnabled, topFlag);
    }
}
