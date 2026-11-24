// [P3C A20 豁免] A20/A30/A31: SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasource.runtime.store;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.datasource.runtime.model.DsCalciteFederation;
import com.chua.starter.datasource.runtime.model.DsShardingRule;
import com.chua.starter.datasource.runtime.model.FederationMember;
import com.chua.starter.datasource.runtime.model.ShardingDbMember;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据源运行时配置存储（JdbcTemplate 直读直写，MySQL/H2 通用写法）。
 *
 * <p>负责 ds_calcite_federation 与 ds_sharding_rule 两张配置表的持久化；
 * 成员 JSON 的解析/序列化也在此完成，服务层只面向模型对象。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Repository
public class DsRuntimeConfigStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public DsRuntimeConfigStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== Calcite 联邦 ====================

    /**
     * 查询全部联邦配置（含停用）。
     */
    public List<DsCalciteFederation> findAllFederations() {
        return jdbcTemplate.query(
                "SELECT id, schema_name, default_schema, enabled, members, remark, version, created_at, updated_at "
                        + "FROM ds_calcite_federation ORDER BY id",
                (rs, i) -> mapFederation(rs));
    }

    /**
     * 查询启用的联邦配置。
     */
    public List<DsCalciteFederation> findEnabledFederations() {
        return jdbcTemplate.query(
                "SELECT id, schema_name, default_schema, enabled, members, remark, version, created_at, updated_at "
                        + "FROM ds_calcite_federation WHERE enabled = 1 ORDER BY id",
                (rs, i) -> mapFederation(rs));
    }

    /**
     * 按主键查询联邦配置。
     */
    public DsCalciteFederation findFederationById(Long id) {
        List<DsCalciteFederation> list = jdbcTemplate.query(
                "SELECT id, schema_name, default_schema, enabled, members, remark, version, created_at, updated_at "
                        + "FROM ds_calcite_federation WHERE id = ?",
                (rs, i) -> mapFederation(rs), id);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 按 schema 名查询联邦配置（判重用）。
     */
    public DsCalciteFederation findFederationBySchemaName(String schemaName) {
        List<DsCalciteFederation> list = jdbcTemplate.query(
                "SELECT id, schema_name, default_schema, enabled, members, remark, version, created_at, updated_at "
                        + "FROM ds_calcite_federation WHERE schema_name = ?",
                (rs, i) -> mapFederation(rs), schemaName);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 保存联邦配置（有 id 先更新，未命中再插入；无 id 直接插入）。
     */
    public long saveFederation(DsCalciteFederation f) {
        if (f.getId() != null) {
            int updated = jdbcTemplate.update(
                    "UPDATE ds_calcite_federation SET schema_name = ?, default_schema = ?, enabled = ?, "
                            + "members = ?, remark = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP "
                            + "WHERE id = ? AND version = ?",
                    ps -> {
                        ps.setString(1, f.getSchemaName());
                        ps.setString(2, f.getDefaultSchema());
                        ps.setInt(3, f.getEnabled() == null ? 1 : f.getEnabled());
                        ps.setString(4, f.getMembers());
                        ps.setString(5, f.getRemark());
                        ps.setLong(6, f.getId());
                        ps.setInt(7, f.getVersion() == null ? 0 : f.getVersion());
                    });
            if (updated > 0) {
                return f.getId();
            }
            // 未命中：行已被删除则继续走插入；行仍在说明携带的版本已过期（并发修改冲突）
            if (federationExists(f.getId())) {
                throw new IllegalStateException("联邦配置已被他人修改，请刷新后重试");
            }
        }
        jdbcTemplate.update(
                "INSERT INTO ds_calcite_federation (schema_name, default_schema, enabled, members, remark, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ps -> {
                    ps.setString(1, f.getSchemaName());
                    ps.setString(2, f.getDefaultSchema());
                    ps.setInt(3, f.getEnabled() == null ? 1 : f.getEnabled());
                    ps.setString(4, f.getMembers());
                    ps.setString(5, f.getRemark());
                });
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM ds_calcite_federation", Long.class);
    }

    /**
     * 删除联邦配置。
     */
    public int deleteFederation(Long id) {
        return jdbcTemplate.update("DELETE FROM ds_calcite_federation WHERE id = ?", id);
    }

    private DsCalciteFederation mapFederation(ResultSet rs) throws SQLException {
        DsCalciteFederation f = new DsCalciteFederation();
        f.setId(rs.getLong("id"));
        f.setSchemaName(rs.getString("schema_name"));
        f.setDefaultSchema(rs.getString("default_schema"));
        f.setEnabled(rs.getInt("enabled"));
        f.setMembers(rs.getString("members"));
        f.setRemark(rs.getString("remark"));
        f.setVersion(rs.getInt("version"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        f.setCreatedAt(created == null ? null : created.getTime());
        f.setUpdatedAt(updated == null ? null : updated.getTime());
        f.setMemberList(parseFederationMembers(f.getMembers()));
        return f;
    }

    /**
     * 解析成员 JSON；解析失败返回空列表（不阻断查询）。
     */
    public List<FederationMember> parseFederationMembers(String json) {
        List<FederationMember> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructParametricType(List.class, Map.class));
            for (Map<String, Object> m : raw) {
                FederationMember member = new FederationMember();
                member.setSchemaName((String) m.get("schemaName"));
                member.setUrl((String) m.get("url"));
                member.setUsername((String) m.get("username"));
                member.setPassword((String) m.get("password"));
                member.setDriverClassName((String) m.get("driverClassName"));
                result.add(member);
            }
        } catch (Exception e) {
            log.warn("[ds-runtime] 联邦成员 JSON 解析失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 序列化成员列表为 JSON。
     */
    public String writeFederationMembers(List<FederationMember> members) {
        if (members == null) {
            return null;
        }
        return Json.toJson(members);
    }

    // ==================== 分库分表规则 ====================

    /**
     * 查询全部分片规则（含停用）。
     */
    public List<DsShardingRule> findAllShardingRules() {
        return jdbcTemplate.query(
                "SELECT id, logic_table, sharding_column, algorithm, shard_count, time_unit, time_lower, time_upper, "
                        + "database_members, enabled, remark, version, created_at, updated_at FROM ds_sharding_rule ORDER BY id",
                (rs, i) -> mapShardingRule(rs));
    }

    /**
     * 查询启用的分片规则。
     */
    public List<DsShardingRule> findEnabledShardingRules() {
        return jdbcTemplate.query(
                "SELECT id, logic_table, sharding_column, algorithm, shard_count, time_unit, time_lower, time_upper, "
                        + "database_members, enabled, remark, version, created_at, updated_at FROM ds_sharding_rule WHERE enabled = 1 ORDER BY id",
                (rs, i) -> mapShardingRule(rs));
    }

    /**
     * 按主键查询分片规则。
     */
    public DsShardingRule findShardingRuleById(Long id) {
        List<DsShardingRule> list = jdbcTemplate.query(
                "SELECT id, logic_table, sharding_column, algorithm, shard_count, time_unit, time_lower, time_upper, "
                        + "database_members, enabled, remark, version, created_at, updated_at FROM ds_sharding_rule WHERE id = ?",
                (rs, i) -> mapShardingRule(rs), id);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 按逻辑表名查询分片规则（判重用）。
     */
    public DsShardingRule findShardingRuleByLogicTable(String logicTable) {
        List<DsShardingRule> list = jdbcTemplate.query(
                "SELECT id, logic_table, sharding_column, algorithm, shard_count, time_unit, time_lower, time_upper, "
                        + "database_members, enabled, remark, version, created_at, updated_at FROM ds_sharding_rule WHERE logic_table = ?",
                (rs, i) -> mapShardingRule(rs), logicTable);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 保存分片规则（有 id 先更新，未命中再插入；无 id 直接插入）。
     */
    public long saveShardingRule(DsShardingRule r) {
        if (r.getId() != null) {
            int updated = jdbcTemplate.update(
                    "UPDATE ds_sharding_rule SET logic_table = ?, sharding_column = ?, algorithm = ?, shard_count = ?, "
                            + "time_unit = ?, time_lower = ?, time_upper = ?, database_members = ?, enabled = ?, remark = ?, "
                            + "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND version = ?",
                    ps -> {
                        ps.setString(1, r.getLogicTable());
                        ps.setString(2, r.getShardingColumn());
                        ps.setString(3, r.getAlgorithm());
                        ps.setInt(4, r.getShardCount() == null ? 16 : r.getShardCount());
                        ps.setString(5, r.getTimeUnit());
                        ps.setString(6, r.getTimeLower());
                        ps.setString(7, r.getTimeUpper());
                        ps.setString(8, r.getDatabases());
                        ps.setInt(9, r.getEnabled() == null ? 1 : r.getEnabled());
                        ps.setString(10, r.getRemark());
                        ps.setLong(11, r.getId());
                        ps.setInt(12, r.getVersion() == null ? 0 : r.getVersion());
                    });
            if (updated > 0) {
                return r.getId();
            }
            // 未命中：行已被删除则继续走插入；行仍在说明携带的版本已过期（并发修改冲突）
            if (shardingRuleExists(r.getId())) {
                throw new IllegalStateException("分片规则已被他人修改，请刷新后重试");
            }
        }
        jdbcTemplate.update(
                "INSERT INTO ds_sharding_rule (logic_table, sharding_column, algorithm, shard_count, time_unit, "
                        + "time_lower, time_upper, database_members, enabled, remark, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ps -> {
                    ps.setString(1, r.getLogicTable());
                    ps.setString(2, r.getShardingColumn());
                    ps.setString(3, r.getAlgorithm());
                    ps.setInt(4, r.getShardCount() == null ? 16 : r.getShardCount());
                    ps.setString(5, r.getTimeUnit());
                    ps.setString(6, r.getTimeLower());
                    ps.setString(7, r.getTimeUpper());
                    ps.setString(8, r.getDatabases());
                    ps.setInt(9, r.getEnabled() == null ? 1 : r.getEnabled());
                    ps.setString(10, r.getRemark());
                });
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM ds_sharding_rule", Long.class);
    }

    /**
     * 删除分片规则。
     */
    public int deleteShardingRule(Long id) {
        return jdbcTemplate.update("DELETE FROM ds_sharding_rule WHERE id = ?", id);
    }

    private boolean federationExists(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ds_calcite_federation WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private boolean shardingRuleExists(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ds_sharding_rule WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private DsShardingRule mapShardingRule(ResultSet rs) throws SQLException {
        DsShardingRule r = new DsShardingRule();
        r.setId(rs.getLong("id"));
        r.setLogicTable(rs.getString("logic_table"));
        r.setShardingColumn(rs.getString("sharding_column"));
        r.setAlgorithm(rs.getString("algorithm"));
        r.setShardCount(rs.getInt("shard_count"));
        r.setTimeUnit(rs.getString("time_unit"));
        r.setTimeLower(rs.getString("time_lower"));
        r.setTimeUpper(rs.getString("time_upper"));
        r.setDatabases(rs.getString("database_members"));
        r.setEnabled(rs.getInt("enabled"));
        r.setRemark(rs.getString("remark"));
        r.setVersion(rs.getInt("version"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        r.setCreatedAt(created == null ? null : created.getTime());
        r.setUpdatedAt(updated == null ? null : updated.getTime());
        r.setDbMemberList(parseShardingMembers(r.getDatabases()));
        return r;
    }

    /**
     * 解析分库成员 JSON；元素可为对象（连接参数）或字符串（已注册数据源名）。
     */
    public List<ShardingDbMember> parseShardingMembers(String json) {
        List<ShardingDbMember> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            List<?> raw = MAPPER.readValue(json, List.class);
            for (Object o : raw) {
                ShardingDbMember member = new ShardingDbMember();
                if (o instanceof Map<?, ?> m) {
                    member.setName((String) m.get("name"));
                    member.setUrl((String) m.get("url"));
                    member.setUsername((String) m.get("username"));
                    member.setPassword((String) m.get("password"));
                    member.setDriverClassName((String) m.get("driverClassName"));
                } else {
                    // 字符串形式：引用已注册数据源名，运行期从上下文取
                    member.setName(String.valueOf(o));
                }
                result.add(member);
            }
        } catch (Exception e) {
            log.warn("[ds-runtime] 分库成员 JSON 解析失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 序列化分库成员列表为 JSON。
     */
    public String writeShardingMembers(List<ShardingDbMember> members) {
        if (members == null) {
            return null;
        }
        return Json.toJson(members);
    }
}
