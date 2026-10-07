package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.model.FieldMappingVO;
import com.chua.starter.datasourcehub.support.model.SchemaColumnVO;
import com.chua.starter.datasourcehub.support.model.SchemaTableVO;
import com.chua.starter.datasourcehub.support.model.TableDdlVO;
import com.chua.starter.datasourcehub.support.model.dto.DdlColumn;
import com.chua.starter.datasourcehub.support.model.dto.FieldMappingDTO;
import com.chua.starter.datasourcehub.support.model.dto.SourceField;
import com.chua.starter.datasourcehub.support.model.dto.TableDdlDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceSchemaService;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.support.ColumnTypeSupport;
import com.chua.starter.datasourcehub.support.support.JdbcDialectSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 数据源结构服务实现。
 *
 * <p>表/列元数据走 JDBC {@link DatabaseMetaData}，不拼 {@code SHOW TABLES} 之类方言语句；
 * DDL 由 {@link JdbcDialectSupport} 按方言拼装并执行。表名/列名一律先过标识符白名单，
 * 值不参与拼接（DDL 里只有类型与转义后的注释/默认值）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceSchemaServiceImpl implements DatasourceSchemaService {

    /**
     * 应用主库引用名。
     */
    public static final String APP_DATASOURCE = "app";

    /**
     * 自动主键列名。
     */
    private static final String ID_COLUMN = "id";

    /**
     * 表已存在的默认策略：补缺失列。
     */
    private static final String STRATEGY_APPEND = "APPEND";

    /**
     * 数据源台账服务：取连接。
     */
    private final DatasourceService datasourceService;

    /**
     * 数据源台账：按名称解析引用。
     */
    private final MsDatasourceMapper datasourceMapper;

    @Override
    public List<String> listTables(String datasource) {
        List<SchemaTableVO> infos = listTableInfos(datasource);
        List<String> names = new ArrayList<>(infos.size());
        for (SchemaTableVO info : infos) {
            names.add(info.name());
        }
        return names;
    }

    @Override
    public List<SchemaTableVO> listTableInfos(String datasource) {
        try (Connection connection = connection(datasource)) {
            Map<String, SchemaTableVO> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
                    new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    // 不按 sys 前缀过滤：本仓库业务表就是 sys_xxx，过滤掉等于把可选业务表全藏了
                    if (name != null && !name.isBlank()) {
                        tables.putIfAbsent(name, new SchemaTableVO(name, rs.getString("REMARKS"),
                                rs.getString("TABLE_TYPE")));
                    }
                }
            }
            return new ArrayList<>(tables.values());
        } catch (Exception ex) {
            throw new IllegalStateException("读取表清单失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<SchemaColumnVO> listColumns(String datasource, String table) {
        String name = identifier(table);
        try (Connection connection = connection(datasource)) {
            return readColumns(connection, name);
        } catch (Exception ex) {
            throw new IllegalStateException("读取列清单失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean tableExists(String datasource, String table) {
        try (Connection connection = connection(datasource)) {
            return JdbcDialectSupport.tableExists(connection, identifier(table));
        } catch (Exception ex) {
            throw new IllegalStateException("判断表是否存在失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public TableDdlVO previewDdl(TableDdlDTO request) {
        return build(request, false);
    }

    @Override
    public TableDdlVO executeDdl(TableDdlDTO request) {
        return build(request, true);
    }

    @Override
    public List<FieldMappingVO> resolveMapping(FieldMappingDTO request) {
        String table = identifier(request.table());
        try (Connection connection = connection(request.datasource())) {
            List<SourceField> fields = request.fields() == null ? List.of() : request.fields();
            if (!JdbcDialectSupport.tableExists(connection, table)) {
                List<FieldMappingVO> mappings = new ArrayList<>(fields.size());
                for (SourceField field : fields) {
                    mappings.add(new FieldMappingVO(field.name(), normalizeType(field.type()),
                            identifier(field.name()), ColumnTypeSupport.infer(connection, field.type()),
                            false, "NEW_TABLE", "目标表还不存在，按来源类型建列"));
                }
                return mappings;
            }
            Map<String, SchemaColumnVO> existing = new HashMap<>();
            for (SchemaColumnVO column : readColumns(connection, table)) {
                existing.put(column.name().toLowerCase(Locale.ROOT), column);
            }
            List<FieldMappingVO> mappings = new ArrayList<>(fields.size());
            for (SourceField field : fields) {
                String column = identifier(field.name());
                SchemaColumnVO target = existing.get(column.toLowerCase(Locale.ROOT));
                if (target == null) {
                    mappings.add(new FieldMappingVO(field.name(), normalizeType(field.type()), column,
                            ColumnTypeSupport.infer(connection, field.type()), false, "ADD",
                            "目标表没有同名列，按策略补列"));
                    continue;
                }
                boolean cast = ColumnTypeSupport.needCast(normalizeType(field.type()), target.typeName());
                mappings.add(new FieldMappingVO(field.name(), normalizeType(field.type()), target.name(),
                        target.typeName(), true, "USE", cast ? "类型不同，写入时按目标列类型转换" : null));
            }
            return mappings;
        } catch (Exception ex) {
            throw new IllegalStateException("解析字段映射失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 建表 / 补列的统一实现：预览只生成 SQL，执行才落库。
     *
     * @param request 建表请求
     * @param execute true=执行，false=只预览
     * @return 结果
     */
    private TableDdlVO build(TableDdlDTO request, boolean execute) {
        String table = identifier(request.table());
        List<DdlColumn> requested = request.columns() == null ? List.of() : request.columns();
        List<String> warnings = new ArrayList<>();
        try (Connection connection = connection(request.datasource())) {
            boolean exists = JdbcDialectSupport.tableExists(connection, table);
            List<JdbcDialectSupport.ColumnDef> definitions = new ArrayList<>();
            List<SchemaColumnVO> resolved = new ArrayList<>();
            for (DdlColumn column : requested) {
                String name = identifier(column.name());
                String type = StringUtils.hasText(column.type())
                        ? column.type().trim() : ColumnTypeSupport.infer(connection, column.sourceType());
                if (!StringUtils.hasText(column.type())) {
                    warnings.add("列 " + name + " 未指定类型，按来源类型推断为 " + type);
                }
                boolean nullable = column.nullable() == null || column.nullable();
                definitions.add(new JdbcDialectSupport.ColumnDef(name, type, nullable, column.comment(),
                        column.defaultValue()));
                resolved.add(new SchemaColumnVO(name, column.comment(), null, type, null, nullable, false));
            }
            List<String> primaryKey = new ArrayList<>();
            if (request.primaryKey() != null) {
                for (String key : request.primaryKey()) {
                    if (StringUtils.hasText(key)) {
                        primaryKey.add(identifier(key));
                    }
                }
            }
            if (Boolean.TRUE.equals(request.autoIdPrimaryKey()) && primaryKey.isEmpty()) {
                definitions.add(0, idColumn(connection));
                primaryKey.add(ID_COLUMN);
                warnings.add("已自动加自增主键列 " + ID_COLUMN);
            }
            if (definitions.isEmpty()) {
                throw new IllegalArgumentException("没有可建的列，请先配置字段映射");
            }
            if (!exists) {
                String ddl = JdbcDialectSupport.createTableSql(connection, table, definitions, primaryKey,
                        request.comment());
                // 非 MySQL 内核的表注释是独立的 COMMENT ON TABLE 语句，预览也要列出来：
                // 只给 CREATE TABLE 会让用户以为注释没生效（实际执行时它会补上）。
                List<String> statements = new ArrayList<>();
                statements.add(ddl);
                String tableComment = JdbcDialectSupport.tableCommentSql(connection, table, request.comment());
                if (tableComment != null) {
                    statements.add(tableComment);
                }
                if (execute) {
                    JdbcDialectSupport.createTable(connection, table, definitions, primaryKey, request.comment());
                }
                return new TableDdlVO(table, false, execute, List.of(),
                        execute ? readColumns(connection, table) : resolved, statements, warnings);
            }
            String strategy = StringUtils.hasText(request.ifExists())
                    ? request.ifExists().trim().toUpperCase(Locale.ROOT) : STRATEGY_APPEND;
            if ("ERROR".equals(strategy)) {
                throw new IllegalStateException("目标表已存在: " + table);
            }
            if ("SKIP".equals(strategy)) {
                warnings.add("目标表已存在，按策略跳过");
                return new TableDdlVO(table, true, false, List.of(), readColumns(connection, table),
                        List.of(), warnings);
            }
            Set<String> existingColumns = JdbcDialectSupport.columnNames(connection, table);
            List<String> appended = new ArrayList<>();
            List<String> statements = new ArrayList<>();
            for (JdbcDialectSupport.ColumnDef definition : definitions) {
                if (existingColumns.contains(definition.name().toLowerCase(Locale.ROOT))) {
                    warnings.add("列 " + definition.name() + " 已存在，跳过");
                    continue;
                }
                statements.add(JdbcDialectSupport.addColumnSql(connection, table, definition));
                if (execute) {
                    JdbcDialectSupport.addColumn(connection, table, definition);
                }
                appended.add(definition.name());
            }
            return new TableDdlVO(table, true, false, appended, readColumns(connection, table), statements, warnings);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("建表失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 读列元数据。
     *
     * <p>列名大小写在不同库里表现不一（MySQL 保留创建时大小写，H2 默认转大写），
     * 因此按原样与全大写各查一次，取到的那份为准；主键列额外标记。</p>
     *
     * @param connection 数据库连接
     * @param table      表名
     * @return 列元数据
     * @throws SQLException 读取失败
     */
    private List<SchemaColumnVO> readColumns(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        Map<Integer, SchemaColumnVO> columns = new TreeMap<>();
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, candidate, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (columnName == null || columnName.isBlank()) {
                        continue;
                    }
                    int position = rs.getInt("ORDINAL_POSITION");
                    // JDBC 约定：0=不允许空 1=可空 2=未知；未知按可空处理，误判成 NOT NULL 会挡住合法写入
                    boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                    columns.putIfAbsent(position, new SchemaColumnVO(columnName, rs.getString("REMARKS"),
                            position, rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"), nullable, false));
                }
            }
            if (!columns.isEmpty()) {
                break;
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("表不存在或没有可见列: " + table);
        }
        Set<String> keys = new HashSet<>();
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getPrimaryKeys(connection.getCatalog(), null, candidate)) {
                while (rs.next()) {
                    keys.add(rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                }
            }
        }
        List<SchemaColumnVO> result = new ArrayList<>(columns.size());
        for (SchemaColumnVO column : columns.values()) {
            boolean primary = keys.contains(column.name().toUpperCase(Locale.ROOT));
            result.add(new SchemaColumnVO(column.name(), column.label(), column.position(), column.typeName(),
                    column.size(), column.nullable(), primary));
        }
        return result;
    }

    /**
     * 自增主键列定义（跨方言）。
     *
     * @param connection 数据库连接
     * @return 列定义
     */
    private JdbcDialectSupport.ColumnDef idColumn(Connection connection) {
        if (JdbcDialectSupport.isMysqlFamily(connection)) {
            return new JdbcDialectSupport.ColumnDef(ID_COLUMN, "BIGINT AUTO_INCREMENT", false, "自增主键", null);
        }
        // 标准写法自带非空语义，不能再拼 NOT NULL，否则 H2/PG 语法不合法
        return new JdbcDialectSupport.ColumnDef(ID_COLUMN, "BIGINT GENERATED BY DEFAULT AS IDENTITY", true,
                "自增主键", null);
    }

    /**
     * 取数据源连接。
     *
     * @param datasource 数据源引用：留空或 app 表示应用主库，否则为台账 ID 或名称
     * @return 连接（关闭是空操作，由数据源中心统一管理）
     * @throws Exception 数据源不存在或连接失败
     */
    private Connection connection(String datasource) throws Exception {
        return datasourceService.connection(resolveId(datasource));
    }

    /**
     * 数据源引用解析：空/app 为应用主库，纯数字为台账 ID，其余按名称查台账。
     *
     * @param datasource 数据源引用
     * @return 台账 ID，null 表示应用主库
     */
    private Long resolveId(String datasource) {
        if (!StringUtils.hasText(datasource) || APP_DATASOURCE.equalsIgnoreCase(datasource.trim())) {
            return null;
        }
        String reference = datasource.trim();
        if (reference.chars().allMatch(Character::isDigit)) {
            return Long.valueOf(reference);
        }
        MsDatasource row = datasourceMapper.selectOne(Wrappers.<MsDatasource>lambdaQuery()
                .eq(MsDatasource::getDatasourceName, reference).last("limit 1"));
        if (row == null || row.getId() == null) {
            throw new IllegalArgumentException("数据源不存在: " + reference);
        }
        return row.getId();
    }

    /**
     * 校验并规范标识符。
     *
     * @param name 表名或列名
     * @return 合法名字
     */
    private String identifier(String name) {
        if (!JdbcDialectSupport.isSafeIdentifier(name == null ? null : name.trim())) {
            throw new IllegalArgumentException("非法表名或列名: " + name);
        }
        return name.trim();
    }

    /**
     * 来源类型归一化，空值按字符串处理。
     *
     * @param type 来源类型
     * @return 归一化后的类型
     */
    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "STRING";
    }
}
