// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectDatasource;
import com.chua.starter.collect.mapper.CollectDatasourceMapper;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.zaxxer.hikari.HikariConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 采集侧数据源解析器：把「数据源引用」解析成可建池的连接参数。
 *
 * <p>台账统一读数据源中心 {@code ms_datasource}（{@link DatasourceService}）。
 * 迁移期仍允许回落到老的 {@code collect_datasource}：存量数据源没搬完时流程不至于直接报错，
 * 搬完并核对后把回落分支去掉即可。</p>
 *
 * <p>密码两种存法都吃：数据源中心存明文，老采集台账存 {@code b64:} 前缀，
 * {@link CollectSecrets#decode} 对无前缀值原样返回，所以两边可以共用同一条解码路径。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectDatasourceRegistry {

    /**
     * 数据源中心服务。
     */
    private final ObjectProvider<DatasourceService> hub;

    /**
     * 老采集台账访问，仅迁移期回落用。
     */
    private final ObjectProvider<CollectDatasourceMapper> legacyMapper;

    /**
     * 解析结果。
     *
     * @param jdbcUrl      JDBC 地址
     * @param username     账号
     * @param password     明文口令
     * @param driverClass  驱动类名，可空
     * @param displayName  展示名，用于报错
     * @param legacy       是否来自老采集台账（迁移期回落）
     */
    public record Target(String jdbcUrl, String username, String password, String driverClass,
                         String displayName, boolean legacy) {
    }

    /**
     * 按 ID 或名称解析数据源。
     *
     * @param key 数据源引用，全数字按 ID，其余按名称
     * @return 连接参数
     * @throws IllegalStateException 数据源不存在或已停用
     */
    public Target resolve(String key) {
        Target fromHub = fromHub(key);
        if (fromHub != null) {
            return fromHub;
        }
        Target legacy = fromLegacy(key);
        if (legacy != null) {
            log.info("[collect] 数据源 {} 尚未迁移到数据源中心，按老台账回落", key);
            return legacy;
        }
        throw new IllegalStateException("找不到数据源: " + key);
    }

    /**
     * 在数据源中心里按 ID 或名称找，供中间件类节点（InfluxDB / Neo4j 等）复用。
     *
     * <p>与 {@link #resolve} 分开是刻意的：SQL / 入库节点要的是「能建 JDBC 池的连接参数」，
     * 中间件节点要的是「数据源行 + 类型」，两者校验口径不同，不该互相迁就。</p>
     *
     * @param key 数据源引用，全数字按 ID，其余按名称
     * @return 数据源行
     * @throws IllegalStateException 数据源中心未启用、数据源不存在或已停用
     */
    public DataSourceVO findRow(String key) {
        DatasourceService service = hub.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("数据源中心未启用，无法解析数据源: " + key);
        }
        DataSourceVO row = find(service, key);
        if (row == null) {
            throw new IllegalStateException("找不到数据源: " + key);
        }
        if (row.status() != null && row.status() == 0) {
            throw new IllegalStateException("数据源已停用: " + row.name());
        }
        return row;
    }

    /**
     * 从数据源中心解析。
     *
     * <p>数据源中心的 JDBC 卡常规存法是 host/port/库名（新增弹窗的「数据库/库名」
     * 提示填库名），完整 {@code jdbc:} URL 只是可选写法——与 hub 侧
     * {@code resolveJdbcUrl} 同口径：URL 形态直接用，否则按 host+port+库名拼
     * {@code jdbc:mysql} URL。早先只认 URL 前缀，UI 建的卡全会被误判成
     * 「不是 JDBC 类型」。</p>
     *
     * @param key 数据源引用
     * @return 连接参数，未找到返回 null
     * @throws IllegalStateException 数据源已停用、类型不是 jdbc 或缺少主机/库名
     */
    private Target fromHub(String key) {
        DatasourceService service = hub.getIfAvailable();
        if (service == null) {
            return null;
        }
        DataSourceVO row = find(service, key);
        if (row == null) {
            return null;
        }
        if (row.status() != null && row.status() == 0) {
            throw new IllegalStateException("数据源已停用: " + row.name());
        }
        if (!"jdbc".equalsIgnoreCase(trim(row.type()))) {
            throw new IllegalStateException("数据源 " + row.name() + " 不是 JDBC 类型，无法被 SQL/入库节点使用");
        }
        String url = trim(row.database());
        if (!url.regionMatches(true, 0, DatasourceHubConstants.JDBC_URL_PREFIX, 0,
                DatasourceHubConstants.JDBC_URL_PREFIX.length())) {
            String host = trim(row.host());
            if (host.isEmpty() || url.isEmpty()) {
                throw new IllegalStateException("JDBC 数据源缺少主机或库名: " + row.name());
            }
            int port = row.port() != null ? row.port()
                    : DatasourceHubConstants.DS_DEFAULT_PORTS.getOrDefault("jdbc", 3306);
            url = String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                    host, port, url);
        }
        return new Target(url, trim(row.username()),
                CollectSecrets.decode(row.password()), trim(row.driverClassName()),
                row.name(), false);
    }

    /**
     * 在数据源中心里按 ID 或名称找。
     *
     * @param service 数据源中心服务
     * @param key     数据源引用
     * @return 数据源行，未找到返回 null
     */
    private DataSourceVO find(DatasourceService service, String key) {
        if (key.chars().allMatch(Character::isDigit)) {
            ReturnResult<DataSourceVO> got = service.get(Long.valueOf(key));
            return got == null || got.getData() == null ? null : got.getData();
        }
        ReturnResult<List<DataSourceVO>> all = service.list();
        if (all == null || all.getData() == null) {
            return null;
        }
        return all.getData().stream()
                .filter(row -> key.equalsIgnoreCase(trim(row.name())))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从老采集台账回落解析，供迁移期未搬完的存量数据源继续可用。
     *
     * @param key 数据源引用
     * @return 连接参数，未找到返回 null
     */
    private Target fromLegacy(String key) {
        CollectDatasource entity = findLegacy(key);
        if (entity == null) {
            return null;
        }
        if (entity.getCollectDatasourceStatus() != null && entity.getCollectDatasourceStatus() == 0) {
            throw new IllegalStateException("数据源已停用: " + entity.getCollectDatasourceName());
        }
        return new Target(trim(entity.getCollectDatasourceJdbcUrl()), trim(entity.getCollectDatasourceUsername()),
                CollectSecrets.decode(entity.getCollectDatasourcePassword()),
                trim(entity.getCollectDatasourceDriverClassName()),
                entity.getCollectDatasourceName(), true);
    }

    /**
     * 按ID或名称查老采集台账。
     *
     * @param key 数据源引用
     * @return 老台账行，不存在返回 null
     */
    private CollectDatasource findLegacy(String key) {
        if (key.chars().allMatch(Character::isDigit)) {
            return legacyMapper.getIfAvailable() == null
                    ? null
                    : legacyMapper.getObject().selectById(Integer.valueOf(key));
        }
        return legacyMapper.getIfAvailable() == null
                ? null
                : legacyMapper.getObject().selectOne(
                        Wrappers.<CollectDatasource>lambdaQuery()
                                .eq(CollectDatasource::getCollectDatasourceName, key)
                                .last("LIMIT 1"));
    }

    /**
     * 组装连接池配置。
     *
     * @param target           连接参数
     * @param poolName         连接池名
     * @param connectTimeoutMs 取连接超时毫秒
     * @return 连接池配置
     */
    public HikariConfig config(Target target, String poolName, long connectTimeoutMs) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(target.jdbcUrl());
        config.setUsername(target.username());
        config.setPassword(target.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(connectTimeoutMs);
        config.setIdleTimeout(600000L);
        config.setMaxLifetime(1800000L);
        config.setPoolName("collect-ds-" + poolName);
        if (target.driverClass() != null && !target.driverClass().isBlank()) {
            config.setDriverClassName(target.driverClass());
        }
        return config;
    }

    /**
     * 截断空白。
     *
     * @param value 原值
     * @return 去空白后的值，null 转空串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}