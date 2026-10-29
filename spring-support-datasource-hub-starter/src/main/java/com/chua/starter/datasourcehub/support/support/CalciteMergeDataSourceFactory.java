package com.chua.starter.datasourcehub.support.support;

import com.chua.calcite.support.datasource.CalciteDataSourceCreator;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Calcite 合并数据源装配器：把合并台账（{@code type=calcite}）的成员 id 列表
 * 装配成一个可联合查询的统一数据源。
 *
 * <p><b>为什么每次都要现装</b>：合并记录本身不落成员快照 —— 成员配置在各自的
 * {@code ms_datasource} 行上。打开控制台时按当前成员逐个建连、注册为 Calcite
 * schema，因此「改成员后重开」立即生效；连接池/口令/隧道的变化也随 {@code connection(memberId)}
 * 一并反映。这与「合并记录是一次性生成物」的定位一致。</p>
 *
 * <p>schema 名 = 成员数据源名去空白；重名自动加 {@code -2/-3} 后缀，命名冲突不互相覆盖。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalciteMergeDataSourceFactory {

    /**
     * 台账读取
     */
    private final MsDatasourceMapper datasourceMapper;

    /**
     * 成员建连（懒取）：{@link DatasourceServiceImpl} 需要本部件处理 calcite 分支的
     * {@code connection(id)}，这里再直接持有 service 的强引用会构成构造期循环依赖
     * （Boot 3+ 默认禁止）。用 {@link ObjectProvider} 延迟解析，运行期才建立依赖。
     */
    private final ObjectProvider<DatasourceService> datasourceServiceProvider;

    /**
     * 连接串/成员 JSON 解析
     */
    private final ObjectMapper objectMapper;

    /**
     * 读取合并记录的成员数据源 id 列表。
     *
     * <p>{@code datasource_database} 存 {@code {"members":[3,7,9]}}。</p>
     *
     * @param row 合并记录
     * @return 成员 id 列表（按声明顺序、去重）；无成员返回空列表
     */
    public List<Long> memberIds(MsDatasource row) {
        String payload = row == null ? null : row.getDatasourceDatabase();
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode members = root.path("members");
            Set<Long> ids = new LinkedHashSet<>();
            for (JsonNode node : members) {
                if (node != null && node.canConvertToLong()) {
                    ids.add(node.asLong());
                }
            }
            return List.copyOf(ids);
        } catch (Exception e) {
            log.warn("[calcite] 解析合并记录成员失败 id={} payload={} reason={}",
                    row.getId(), payload, e.getMessage());
            return List.of();
        }
    }

    /**
     * 校验合并记录是否可装配（成员存在且非合并自身、不循环引用）。
     *
     * @param row 合并记录
     * @return 错误信息；为空表示通过
     */
    public String validateMembers(MsDatasource row) {
        List<Long> ids = memberIds(row);
        if (ids.isEmpty()) {
            return "合并数据源没有任何成员";
        }
        for (Long id : ids) {
            if (row.getId() != null && row.getId().equals(id)) {
                return "合并数据源不能包含自身";
            }
        }
        for (Long id : ids) {
            MsDatasource member = datasourceMapper.selectById(id);
            if (member == null) {
                return "成员数据源不存在: " + id;
            }
            String type = member.getDatasourceType();
            if (DatasourceHubConstants.isCalciteType(type)) {
                return "暂不支持嵌套合并（成员 " + id + " 本身是合并数据源）";
            }
            if (!DatasourceHubConstants.isJdbcFamily(type)) {
                return "成员 " + id + " 不是 JDBC 类型，无法参与联合查询: " + type;
            }
        }
        return null;
    }

    /**
     * 按当前成员现装一个 Calcite 联邦数据源。
     *
     * @param row 合并记录
     * @return Calcite 统一数据源（每个成员注册为一个 schema）
     * @throws IllegalStateException 无成员、成员不存在或建连失败
     */
    public DataSource assemble(MsDatasource row) {
        String error = validateMembers(row);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        List<Long> ids = memberIds(row);
        CalciteDataSourceCreator creator = CalciteDataSourceCreator.newCreator();
        Set<String> used = new LinkedHashSet<>();
        for (Long id : ids) {
            String schemaName = schemaName(used, row, id);
            DataSource memberDs = toDataSource(id);
            if (memberDs == null) {
                throw new IllegalStateException("成员数据源连接不可用: " + id);
            }
            creator.addDataSource(schemaName, memberDs);
            used.add(schemaName);
            log.debug("[calcite] 合并 {} -> schema {}", id, schemaName);
        }
        return creator.create();
    }

    /**
     * 装配并直接取一条连接（供探测/控制台复用）。
     *
     * @param row 合并记录
     * @return 已打开的 Calcite 连接
     * @throws SQLException 建连失败
     */
    public java.sql.Connection openConnection(MsDatasource row) throws SQLException {
        return assemble(row).getConnection();
    }

    /**
     * 为成员 id 生成唯一的 schema 名：按成员名字去空白，冲突追加 {@code -2/-3}。
     */
    private String schemaName(Set<String> used, MsDatasource row, Long memberId) {
        MsDatasource member = datasourceMapper.selectById(memberId);
        String base = member == null ? "ds_" + memberId : normalize(member.getDatasourceName(), memberId);
        if (!used.contains(base)) {
            return base;
        }
        int suffix = 2;
        while (used.contains(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    /**
     * 成员名转 schema 名：去空白、去非法字符；空名回退 {@code ds_{id}}。
     */
    private static String normalize(String name, Long id) {
        if (name == null || name.isBlank()) {
            return "ds_" + id;
        }
        String cleaned = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_").trim();
        return (cleaned == null || cleaned.isEmpty()) ? "ds_" + id : cleaned;
    }

    /**
     * 经 {@link DatasourceService#connection(Long)} 建连并包装为 DataSource。
     */
    private DataSource toDataSource(Long id) {
        try {
            java.sql.Connection connection = datasourceServiceProvider.getObject().connection(id);
            return new SingleConnectionDataSource(connection);
        } catch (Exception e) {
            log.warn("[calcite] 成员建连失败 id={} reason={}", id, e.getMessage());
            return null;
        }
    }

    /**
     * 单连接 DataSource 适配：Calcite 需要 {@link DataSource#getConnection()}。
     *
     * <p>{@code DatasourceService.connection(id)} 返回的是「屏蔽 close 的空闲 TTL 缓存」连接，
     * 这里把它包成 DataSource 交给 Calcite —— 每次 {@code getConnection} 返回同一底层连接。
     * 调用方（控制台）用完需关闭外层 DataSource 包装，底层连接受 TTL 清扫保护。</p>
     */
    public record SingleConnectionDataSource(java.sql.Connection delegate) implements DataSource {

        @Override
        public java.sql.Connection getConnection() {
            return delegate;
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return delegate;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}