package com.chua.starter.plugin.store.impl;

import com.chua.starter.plugin.store.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SQLite持久化存储实现
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
public class SqlitePersistenceStore<T, ID> implements PersistenceStore<T, ID> {

    private final String databasePath;
    private final Class<T> entityClass;
    private final String tableName;
    private final String storeName;
    private volatile boolean initialized = false;

    public SqlitePersistenceStore(String databasePath, Class<T> entityClass, String tableName) {
        this.databasePath = databasePath;
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.storeName = "SQLite-" + entityClass.getSimpleName();
    }

    @Override
    public void initialize() {
        if (!initialized) {
            try {
                // 加载SQLite驱动
                Class.forName("org.sqlite.JDBC");

                // 创建表
                createTableIfNotExists();

                initialized = true;
                log.info("Initialized SQLite store for {} at {}", entityClass.getSimpleName(), databasePath);
            } catch (Exception e) {
                log.error("Failed to initialize SQLite store", e);
                throw new RuntimeException("Failed to initialize SQLite store", e);
            }
        }
    }

    @Override
    public void destroy() {
        if (initialized) {
            initialized = false;
            log.info("Destroyed SQLite store for {}", entityClass.getSimpleName());
        }
    }

    @Override
    public boolean isAvailable() {
        return initialized;
    }

    @Override
    public String getName() {
        return storeName;
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }

        try (Connection conn = getConnection()) {
            ID id = getId(entity);
            if (id == null) {
                return insert(conn, entity);
            } else if (existsById(id)) {
                return update(conn, entity) ? entity : null;
            } else {
                return insert(conn, entity);
            }
        } catch (Exception e) {
            log.error("Failed to save entity", e);
            return null;
        }
    }

    @Override
    public List<T> saveAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> savedEntities = new ArrayList<>();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            for (T entity : entities) {
                T saved = saveWithConnection(conn, entity);
                if (saved != null) {
                    savedEntities.add(saved);
                }
            }

            conn.commit();
        } catch (Exception e) {
            log.error("Failed to save entities", e);
        }

        return savedEntities;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find entity by id: {}", id, e);
        }

        return Optional.empty();
    }

    @Override
    public List<T> findAll() {
        return findByCondition(QueryCondition.empty());
    }

    @Override
    public List<T> findByCondition(QueryCondition condition) {
        List<T> results = new ArrayList<>();

        try (Connection conn = getConnection()) {
            String sql = buildSelectSql(condition);
            PreparedStatement stmt = conn.prepareStatement(sql);

            // 设置参数
            setConditionParameters(stmt, condition);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(mapResultSetToEntity(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find entities by condition", e);
        }

        return results;
    }

    @Override
    public boolean deleteById(ID id) {
        if (id == null) {
            return false;
        }

        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            int affected = stmt.executeUpdate();

            log.debug("Deleted entity with id: {}, affected rows: {}", id, affected);
            return affected > 0;
        } catch (Exception e) {
            log.error("Failed to delete entity by id: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean delete(T entity) {
        if (entity == null) {
            return false;
        }

        try {
            ID id = getId(entity);
            return deleteById(id);
        } catch (Exception e) {
            log.error("Failed to delete entity", e);
            return false;
        }
    }

    @Override
    public int deleteByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM " + tableName + " WHERE id IN (" + placeholders + ")";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < ids.size(); i++) {
                stmt.setObject(i + 1, ids.get(i));
            }

            int affected = stmt.executeUpdate();
            log.debug("Deleted {} entities", affected);
            return affected;
        } catch (Exception e) {
            log.error("Failed to delete entities by ids", e);
            return 0;
        }
    }

    @Override
    public int deleteByCondition(QueryCondition condition) {
        try (Connection conn = getConnection()) {
            String sql = buildDeleteSql(condition);
            PreparedStatement stmt = conn.prepareStatement(sql);

            setConditionParameters(stmt, condition);

            int affected = stmt.executeUpdate();
            log.debug("Deleted {} entities by condition", affected);
            return affected;
        } catch (Exception e) {
            log.error("Failed to delete entities by condition", e);
            return 0;
        }
    }

    @Override
    public boolean existsById(ID id) {
        if (id == null) {
            return false;
        }

        String sql = "SELECT 1 FROM " + tableName + " WHERE id = ? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (Exception e) {
            log.error("Failed to check existence by id: {}", id, e);
            return false;
        }
    }

    @Override
    public long count() {
        return countByCondition(QueryCondition.empty());
    }

    @Override
    public long countByCondition(QueryCondition condition) {
        try (Connection conn = getConnection()) {
            String sql = buildCountSql(condition);
            PreparedStatement stmt = conn.prepareStatement(sql);

            setConditionParameters(stmt, condition);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count entities by condition", e);
        }

        return 0;
    }

    @Override
    public boolean update(T entity) {
        if (entity == null) {
            return false;
        }

        try (Connection conn = getConnection()) {
            return update(conn, entity);
        } catch (Exception e) {
            log.error("Failed to update entity", e);
            return false;
        }
    }

    @Override
    public int updateByCondition(UpdateData updateData, QueryCondition condition) {
        try (Connection conn = getConnection()) {
            String sql = buildUpdateSql(updateData, condition);
            PreparedStatement stmt = conn.prepareStatement(sql);

            // 设置更新字段参数
            int paramIndex = 1;
            for (Object value : updateData.getFields().values()) {
                stmt.setObject(paramIndex++, value);
            }

            // 设置条件参数
            setConditionParameters(stmt, condition, paramIndex);

            int affected = stmt.executeUpdate();
            log.debug("Updated {} entities by condition", affected);
            return affected;
        } catch (Exception e) {
            log.error("Failed to update entities by condition", e);
            return 0;
        }
    }

    @Override
    public PageResult<T> findPage(QueryCondition condition, int page, int size) {
        long total = countByCondition(condition);

        // 添加分页条件
        QueryCondition pageCondition = new QueryCondition();
        pageCondition.getConditions().addAll(condition.getConditions());
        pageCondition.getOrderBys().addAll(condition.getOrderBys());
        pageCondition.getParameters().putAll(condition.getParameters());

        List<T> content = new ArrayList<>();

        try (Connection conn = getConnection()) {
            String sql = buildSelectSql(pageCondition) + " LIMIT ? OFFSET ?";
            PreparedStatement stmt = conn.prepareStatement(sql);

            int paramIndex = setConditionParameters(stmt, pageCondition);
            stmt.setInt(paramIndex++, size);
            stmt.setInt(paramIndex, (page - 1) * size);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                content.add(mapResultSetToEntity(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find page", e);
        }

        return PageResult.of(content, page, size, total);
    }

    @Override
    public StoreType getStoreType() {
        return StoreType.SQLITE;
    }

    // 私有辅助方法

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private void createTableIfNotExists() throws Exception {
        try (Connection conn = getConnection()) {
            String createTableSql = generateCreateTableSql();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSql);
                log.debug("Created table if not exists: {}", tableName);
            }
        }
    }

    private String generateCreateTableSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");

        Field[] fields = entityClass.getDeclaredFields();
        List<String> columnDefs = new ArrayList<>();

        for (Field field : fields) {
            String columnName = field.getName();
            String columnType = getSqliteType(field.getType());

            if ("id".equals(columnName)) {
                columnDefs.add(columnName + " " + columnType + " PRIMARY KEY AUTOINCREMENT");
            } else {
                columnDefs.add(columnName + " " + columnType);
            }
        }

        sql.append(String.join(", ", columnDefs));
        sql.append(")");

        return sql.toString();
    }

    private String getSqliteType(Class<?> javaType) {
        if (javaType == String.class) {
            return "TEXT";
        } else if (javaType == Integer.class || javaType == int.class || javaType == Long.class
                || javaType == long.class) {
            return "INTEGER";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return "INTEGER";
        } else if (javaType == LocalDateTime.class) {
            return "TEXT";
        } else if (javaType.isEnum()) {
            return "TEXT";
        } else {
            return "TEXT";
        }
    }

    @SuppressWarnings("unchecked")
    private ID getId(T entity) throws Exception {
        Field idField = findIdField(entity.getClass());
        if (idField != null) {
            idField.setAccessible(true);
            return (ID) idField.get(entity);
        }
        return null;
    }

    private void setId(T entity, ID id) throws Exception {
        Field idField = findIdField(entity.getClass());
        if (idField != null) {
            idField.setAccessible(true);
            idField.set(entity, id);
        }
    }

    private Field findIdField(Class<?> clazz) {
        try {
            return clazz.getDeclaredField("id");
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return findIdField(superClass);
            }
        }
        return null;
    }

    private T insert(Connection conn, T entity) throws Exception {
        callOnCreate(entity);

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            if (!"id".equals(field.getName())) {
                field.setAccessible(true);
                columns.add(field.getName());
                values.add(convertToSqlValue(field.get(entity)));
            }
        }

        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + tableName + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setObject(i + 1, values.get(i));
            }

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    @SuppressWarnings("unchecked")
                    ID generatedId = (ID) generatedKeys.getObject(1);
                    setId(entity, generatedId);
                }
                log.debug("Inserted entity with id: {}", getId(entity));
                return entity;
            }
        }

        return null;
    }

    private boolean update(Connection conn, T entity) throws Exception {
        callOnUpdate(entity);

        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            if (!"id".equals(field.getName())) {
                field.setAccessible(true);
                setClauses.add(field.getName() + " = ?");
                values.add(convertToSqlValue(field.get(entity)));
            }
        }

        ID id = getId(entity);
        values.add(id);

        String sql = "UPDATE " + tableName + " SET " + String.join(",", setClauses) + " WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setObject(i + 1, values.get(i));
            }

            int affected = stmt.executeUpdate();
            log.debug("Updated entity with id: {}, affected rows: {}", id, affected);
            return affected > 0;
        }
    }

    private T saveWithConnection(Connection conn, T entity) throws Exception {
        ID id = getId(entity);
        if (id == null) {
            return insert(conn, entity);
        } else if (existsByIdWithConnection(conn, id)) {
            return update(conn, entity) ? entity : null;
        } else {
            return insert(conn, entity);
        }
    }

    private boolean existsByIdWithConnection(Connection conn, ID id) throws Exception {
        String sql = "SELECT 1 FROM " + tableName + " WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private void callOnCreate(T entity) {
        try {
            entity.getClass().getMethod("onCreate").invoke(entity);
        } catch (Exception e) {
            // 忽略，实体可能没有onCreate方法
        }
    }

    private void callOnUpdate(T entity) {
        try {
            entity.getClass().getMethod("onUpdate").invoke(entity);
        } catch (Exception e) {
            // 忽略，实体可能没有onUpdate方法
        }
    }

    private Object convertToSqlValue(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof LocalDateTime) {
            return value.toString();
        } else if (value instanceof Enum) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        } else {
            return value;
        }
    }

    private T mapResultSetToEntity(ResultSet rs) throws Exception {
        T entity = entityClass.getDeclaredConstructor().newInstance();

        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = convertFromSqlValue(rs.getObject(field.getName()), field.getType());
            field.set(entity, value);
        }

        return entity;
    }

    @SuppressWarnings("unchecked")
    private Object convertFromSqlValue(Object sqlValue, Class<?> targetType) {
        if (sqlValue == null) {
            return null;
        }

        if (targetType == LocalDateTime.class && sqlValue instanceof String) {
            return LocalDateTime.parse((String) sqlValue);
        } else if (targetType.isEnum() && sqlValue instanceof String) {
            return Enum.valueOf((Class<Enum>) targetType, (String) sqlValue);
        } else if (targetType == Boolean.class && sqlValue instanceof Integer) {
            return ((Integer) sqlValue) == 1;
        } else if (targetType == Long.class && sqlValue instanceof Integer) {
            return ((Integer) sqlValue).longValue();
        } else {
            return sqlValue;
        }
    }

    private String buildSelectSql(QueryCondition condition) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);

        if (!condition.getConditions().isEmpty()) {
            sql.append(" WHERE ").append(buildWhereClause(condition));
        }

        if (!condition.getOrderBys().isEmpty()) {
            sql.append(" ORDER BY ").append(buildOrderByClause(condition));
        }

        return sql.toString();
    }

    private String buildDeleteSql(QueryCondition condition) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName);

        if (!condition.getConditions().isEmpty()) {
            sql.append(" WHERE ").append(buildWhereClause(condition));
        }

        return sql.toString();
    }

    private String buildCountSql(QueryCondition condition) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName);

        if (!condition.getConditions().isEmpty()) {
            sql.append(" WHERE ").append(buildWhereClause(condition));
        }

        return sql.toString();
    }

    private String buildUpdateSql(UpdateData updateData, QueryCondition condition) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");

        List<String> setClauses = new ArrayList<>();
        for (String field : updateData.getFields().keySet()) {
            setClauses.add(field + " = ?");
        }
        sql.append(String.join(", ", setClauses));

        if (!condition.getConditions().isEmpty()) {
            sql.append(" WHERE ").append(buildWhereClause(condition));
        }

        return sql.toString();
    }

    private String buildWhereClause(QueryCondition condition) {
        List<String> clauses = new ArrayList<>();

        for (QueryCondition.Condition cond : condition.getConditions()) {
            String clause = buildSingleCondition(cond);
            if (clause != null) {
                clauses.add(clause);
            }
        }

        return String.join(" AND ", clauses);
    }

    private String buildSingleCondition(QueryCondition.Condition condition) {
        String field = condition.getField();
        QueryCondition.Operator operator = condition.getOperator();

        switch (operator) {
        case EQ:
            return field + " = ?";
        case NE:
            return field + " != ?";
        case GT:
            return field + " > ?";
        case GE:
            return field + " >= ?";
        case LT:
            return field + " < ?";
        case LE:
            return field + " <= ?";
        case LIKE:
            return field + " LIKE ?";
        case IN:
            List<?> values = (List<?>) condition.getValue();
            String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
            return field + " IN (" + placeholders + ")";
        case IS_NULL:
            return field + " IS NULL";
        case IS_NOT_NULL:
            return field + " IS NOT NULL";
        default:
            return null;
        }
    }

    private String buildOrderByClause(QueryCondition condition) {
        List<String> clauses = new ArrayList<>();

        for (QueryCondition.OrderBy orderBy : condition.getOrderBys()) {
            String direction = orderBy.getDirection() == QueryCondition.OrderDirection.DESC ? "DESC" : "ASC";
            clauses.add(orderBy.getField() + " " + direction);
        }

        return String.join(", ", clauses);
    }

    private int setConditionParameters(PreparedStatement stmt, QueryCondition condition) throws SQLException {
        return setConditionParameters(stmt, condition, 1);
    }

    private int setConditionParameters(PreparedStatement stmt, QueryCondition condition, int startIndex)
            throws SQLException {
        int paramIndex = startIndex;

        for (QueryCondition.Condition cond : condition.getConditions()) {
            if (cond.getOperator() == QueryCondition.Operator.IS_NULL
                    || cond.getOperator() == QueryCondition.Operator.IS_NOT_NULL) {
                continue;
            }

            if (cond.getOperator() == QueryCondition.Operator.IN) {
                List<?> values = (List<?>) cond.getValue();
                for (Object value : values) {
                    stmt.setObject(paramIndex++, convertToSqlValue(value));
                }
            } else {
                stmt.setObject(paramIndex++, convertToSqlValue(cond.getValue()));
            }
        }

        return paramIndex;
    }
}
