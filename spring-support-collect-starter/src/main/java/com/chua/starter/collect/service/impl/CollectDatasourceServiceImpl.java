package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectNodeDTO;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.entity.CollectDatasource;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.mapper.CollectDatasourceMapper;
import com.chua.starter.collect.mapper.CollectFlowMapper;
import com.chua.starter.collect.service.CollectDatasourceService;
import com.chua.starter.collect.support.CollectJdbcSupport;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectSecrets;
import com.chua.starter.collect.vo.CollectDatasourceVO;
import com.chua.starter.collect.vo.CollectOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 采集数据源服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectDatasourceServiceImpl extends ServiceImpl<CollectDatasourceMapper, CollectDatasource>
        implements CollectDatasourceService {

    /**
     * 启用。
     */
    private static final int ENABLED = 1;

    /**
     * 连接串最大长度，与建表列宽一致。
     */
    private static final int MAX_URL_LENGTH = 500;

    /**
     * 连接串前缀到驱动类，仅在驱动留空时按此补全。
     */
    private static final Map<String, String> URL_DRIVERS = Map.of(
            "jdbc:mysql", "com.mysql.cj.jdbc.Driver",
            "jdbc:mariadb", "org.mariadb.jdbc.Driver",
            "jdbc:postgresql", "org.postgresql.Driver",
            "jdbc:h2", "org.h2.Driver",
            "jdbc:oracle", "oracle.jdbc.OracleDriver",
            "jdbc:sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:dm", "dm.jdbc.driver.DmDriver",
            "jdbc:clickhouse", "com.clickhouse.jdbc.ClickHouseDriver");

    /**
     * 节点里引用数据源的参数名。
     */
    private static final String PARAM_DATASOURCE = "datasource";

    /**
     * 流程表访问，删除前检查是否被引用。
     */
    private final CollectFlowMapper flowMapper;

    /**
     * JDBC 通道，负责连接池缓存与连通性试探。
     */
    private final CollectJdbcSupport jdbcSupport;

    @Override
    public List<CollectDatasourceVO> listDatasources() {
        List<CollectDatasourceVO> list = new ArrayList<>();
        for (CollectDatasource datasource : list(Wrappers.<CollectDatasource>lambdaQuery()
                .orderByAsc(CollectDatasource::getCollectDatasourceName))) {
            list.add(toVo(datasource));
        }
        return list;
    }

    @Override
    public List<CollectOptionVO> options() {
        List<CollectOptionVO> options = new ArrayList<>();
        options.add(new CollectOptionVO(CollectJdbcSupport.APP_DATASOURCE, "应用主库（默认）"));
        for (CollectDatasource datasource : list()) {
            options.add(new CollectOptionVO(String.valueOf(datasource.getCollectDatasourceId()),
                    datasource.getCollectDatasourceName()));
        }
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectDatasourceVO addDatasource(CollectDatasource datasource) {
        applyName(datasource, null);
        applyUrl(datasource);
        datasource.setCollectDatasourceId(null);
        datasource.setCollectDatasourcePassword(
                CollectSecrets.encode(blankToEmpty(datasource.getCollectDatasourcePassword())));
        if (datasource.getCollectDatasourceStatus() == null) {
            datasource.setCollectDatasourceStatus(ENABLED);
        }
        save(datasource);
        return toVo(datasource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editDatasource(CollectDatasource datasource) {
        CollectDatasource exists = requireDatasource(datasource.getCollectDatasourceId());
        applyName(datasource, exists.getCollectDatasourceId());
        applyUrl(datasource);
        String password = datasource.getCollectDatasourcePassword();
        if (CollectSecrets.isMasked(password) || password == null || password.isBlank()) {
            datasource.setCollectDatasourcePassword(exists.getCollectDatasourcePassword());
        } else {
            datasource.setCollectDatasourcePassword(CollectSecrets.encode(password));
        }
        boolean changed = updateById(datasource);
        jdbcSupport.discard(String.valueOf(exists.getCollectDatasourceId()));
        jdbcSupport.discard(exists.getCollectDatasourceName());
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDatasource(Integer datasourceId) {
        CollectDatasource exists = requireDatasource(datasourceId);
        long used = countReferences(exists);
        if (used > 0) {
            throw new IllegalArgumentException("还有 " + used + " 个流程在引用该数据源，不能删除");
        }
        boolean changed = removeById(datasourceId);
        jdbcSupport.discard(String.valueOf(datasourceId));
        jdbcSupport.discard(exists.getCollectDatasourceName());
        return changed;
    }

    /**
     * 统计 DSL 里真正引用了该数据源的流程数。
     *
     * <p>数据源引用是数字 ID，LIKE <code>"\"1\""</code> 会把 DSL 里任意取值为 1 的参数
     * 都算成引用，所以只把它当粗筛，命中后逐个解析参数名再比对。</p>
     *
     * @param datasource 待删数据源
     * @return 引用该数据源的流程数
     */
    private long countReferences(CollectDatasource datasource) {
        String id = String.valueOf(datasource.getCollectDatasourceId());
        String name = datasource.getCollectDatasourceName();
        List<CollectFlow> candidates = flowMapper.selectList(Wrappers.<CollectFlow>lambdaQuery()
                .select(CollectFlow::getCollectFlowDsl)
                .eq(CollectFlow::getCollectFlowIsDeleted, 0)
                .and(query -> query
                        .like(CollectFlow::getCollectFlowDsl, "\"" + name + "\"")
                        .or()
                        .like(CollectFlow::getCollectFlowDsl, "\"" + id + "\"")));
        long used = 0;
        for (CollectFlow flow : candidates) {
            if (references(flow.getCollectFlowDsl(), name, id)) {
                used++;
            }
        }
        return used;
    }

    /**
     * DSL 中是否有节点的 datasource 参数取给定引用。
     *
     * @param dsl  流程 DSL
     * @param name 数据源名称引用
     * @param id   数据源 ID 引用
     * @return 引用返回 true
     */
    private boolean references(String dsl, String name, String id) {
        CollectDslDTO graph = CollectJson.parse(dsl, CollectDslDTO.class);
        if (graph == null || graph.nodes() == null) {
            return false;
        }
        for (CollectNodeDTO node : graph.nodes()) {
            if (node.params() == null) {
                continue;
            }
            for (CollectParamDTO param : node.params()) {
                String value = param.value() == null ? "" : param.value().trim();
                if (PARAM_DATASOURCE.equals(param.name()) && (name.equals(value) || id.equals(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String testConnection(CollectDatasource datasource) {
        CollectDatasource candidate = datasource == null ? new CollectDatasource() : datasource;
        if (candidate.getCollectDatasourceId() != null) {
            mergeStored(candidate);
        }
        applyUrl(candidate);
        try {
            return jdbcSupport.probe(candidate);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("连接失败: " + rootMessage(ex), ex);
        }
    }

    /**
     * 取最内层异常文案，HikariCP 外层只会说 Failed to initialize pool。
     *
     * @param error 原始异常
     * @return 可直接展示的原因
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String text = cause.getMessage();
        return isBlank(text) ? error.toString() : text.trim();
    }

    /**
     * 用已保存的配置补齐本次测试缺省的字段。
     *
     * @param candidate 待测配置
     */
    private void mergeStored(CollectDatasource candidate) {
        CollectDatasource exists = getById(candidate.getCollectDatasourceId());
        if (exists == null) {
            return;
        }
        if (isBlank(candidate.getCollectDatasourceJdbcUrl())) {
            candidate.setCollectDatasourceJdbcUrl(exists.getCollectDatasourceJdbcUrl());
        }
        if (isBlank(candidate.getCollectDatasourceUsername())) {
            candidate.setCollectDatasourceUsername(exists.getCollectDatasourceUsername());
        }
        if (isBlank(candidate.getCollectDatasourceDriverClassName())) {
            candidate.setCollectDatasourceDriverClassName(exists.getCollectDatasourceDriverClassName());
        }
        String password = candidate.getCollectDatasourcePassword();
        if (isBlank(password) || CollectSecrets.isMasked(password)) {
            candidate.setCollectDatasourcePassword(exists.getCollectDatasourcePassword());
        }
    }

    /**
     * 按主键取数据源。
     *
     * @param datasourceId 数据源ID
     * @return 数据源
     */
    private CollectDatasource requireDatasource(Integer datasourceId) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        CollectDatasource datasource = getById(datasourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }
        return datasource;
    }

    /**
     * 校验名称唯一。
     *
     * @param datasource 数据源
     * @param exceptId   排除的ID
     */
    private void applyName(CollectDatasource datasource, Integer exceptId) {
        String name = datasource.getCollectDatasourceName() == null
                ? "" : datasource.getCollectDatasourceName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (CollectJdbcSupport.APP_DATASOURCE.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("app 是应用主库的保留引用名");
        }
        long same = count(Wrappers.<CollectDatasource>lambdaQuery()
                .eq(CollectDatasource::getCollectDatasourceName, name)
                .ne(exceptId != null, CollectDatasource::getCollectDatasourceId, exceptId));
        if (same > 0) {
            throw new IllegalArgumentException("数据源名称已存在: " + name);
        }
        datasource.setCollectDatasourceName(name);
    }

    /**
     * 校验连接串。
     *
     * @param datasource 数据源
     */
    private void applyUrl(CollectDatasource datasource) {
        String url = datasource.getCollectDatasourceJdbcUrl() == null
                ? "" : datasource.getCollectDatasourceJdbcUrl().trim();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("JDBC 连接串不能为空");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("JDBC 连接串过长");
        }
        if (!url.startsWith("jdbc:")) {
            throw new IllegalArgumentException("JDBC 连接串必须以 jdbc: 开头");
        }
        datasource.setCollectDatasourceJdbcUrl(url);
        applyDriver(datasource, url);
    }

    /**
     * 驱动留空时按连接串补全驱动类与类型。
     *
     * @param datasource 数据源
     * @param url        已校验的连接串
     */
    private void applyDriver(CollectDatasource datasource, String url) {
        if (!isBlank(datasource.getCollectDatasourceDriverClassName())) {
            return;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : URL_DRIVERS.entrySet()) {
            if (!lower.startsWith(entry.getKey())) {
                continue;
            }
            datasource.setCollectDatasourceDriverClassName(entry.getValue());
            int end = url.indexOf(':', "jdbc:".length());
            if (end > 0 && end < url.length() - 1) {
                datasource.setCollectDatasourceType(url.substring("jdbc:".length(), end).toUpperCase(Locale.ROOT));
            }
            return;
        }
    }

    /**
     * 实体转出参。
     *
     * @param datasource 数据源
     * @return 出参
     */
    private CollectDatasourceVO toVo(CollectDatasource datasource) {
        return new CollectDatasourceVO(
                datasource.getCollectDatasourceId(),
                datasource.getCollectDatasourceName(),
                datasource.getCollectDatasourceType(),
                datasource.getCollectDatasourceDriverClassName(),
                datasource.getCollectDatasourceJdbcUrl(),
                datasource.getCollectDatasourceUsername(),
                !isBlank(datasource.getCollectDatasourcePassword()),
                datasource.getCollectDatasourceStatus(),
                datasource.getCollectDatasourceRemark(),
                datasource.getCreateTime(),
                datasource.getUpdateTime());
    }

    /**
     * 文本是否为空。
     *
     * @param text 文本
     * @return 空返回 true
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
