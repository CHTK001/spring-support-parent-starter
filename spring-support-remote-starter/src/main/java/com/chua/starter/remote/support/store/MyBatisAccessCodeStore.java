package com.chua.starter.remote.support.store;

import com.chua.remote.gateway.AccessCodeInfo;
import com.chua.remote.gateway.store.AccessCodeStore;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 接入码存储 SPI 的 MyBatis 实现（spring-xx-parent 提供——数据库持久化，重启不丢）。
 *
 * <p>网关为纯 JDK 进程（无 spring 容器）——本实现经 {@code ServiceLoader} 加载，
 * 内部自建 MyBatis {@link SqlSessionFactory}（不依赖 spring 装配）。</p>
 *
 * <p>配置（系统属性 / 环境变量）：</p>
 * <ul>
 *   <li>{@code remote.store.jdbc.url}：JDBC 地址（默认 H2 文件库 {@code ./.remote_data/remote-access}）</li>
 *   <li>{@code remote.store.jdbc.username}（默认 sa）</li>
 *   <li>{@code remote.store.jdbc.password}（默认空）</li>
 * </ul>
 * 切换 MySQL：{@code -Dremote.store.jdbc.url=jdbc:mysql://host:3306/remote}（需引入对应驱动）。
 *
 * @author AtomCode
 */
public class MyBatisAccessCodeStore implements AccessCodeStore {

    private final SqlSessionFactory sqlSessionFactory;

    public MyBatisAccessCodeStore() {
        String url = System.getProperty("remote.store.jdbc.url",
                "jdbc:h2:file:" + defaultDataDir() + ";AUTO_SERVER=TRUE");
        String user = System.getProperty("remote.store.jdbc.username", "sa");
        String pass = System.getProperty("remote.store.jdbc.password", "");
        Environment env = new Environment("remote-store", new JdbcTransactionFactory(),
                new PooledDataSource(detectDriver(url), url, user, pass));
        Configuration cfg = new Configuration(env);
        cfg.addMapper(AccessCodeMapper.class);
        // 显式加载 XML mapper（SQL 定义在 resources/mapper/AccessCodeMapper.xml）
        try (var in = Resources.getResourceAsStream("mapper/AccessCodeMapper.xml")) {
            var xmlMapper = new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, cfg, "mapper/AccessCodeMapper.xml", cfg.getSqlFragments());
            xmlMapper.parse();
        } catch (Exception e) {
            throw new IllegalStateException("加载 AccessCodeMapper.xml 失败: " + e.getMessage(), e);
        }
        this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(cfg);
        initSchema();
    }

    private static String defaultDataDir() {
        String dir = System.getProperty("user.dir", ".");
        return dir.replace('\\', '/') + "/.remote_data/remote-access";
    }

    private static String detectDriver(String url) {
        if (url.startsWith("jdbc:mysql")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (url.startsWith("jdbc:postgresql")) {
            return "org.postgresql.Driver";
        }
        return "org.h2.Driver";
    }

    /** 启动建表（幂等） */
    private void initSchema() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            mapper.createCodeTable();
            mapper.createAgentTable();
            try {
                mapper.addIpWhitelistColumn();
            } catch (Exception ignored) {
                // 列已存在或数据库不支持 ADD COLUMN IF NOT EXISTS——忽略
            }
        }
    }

    @Override
    public List<AccessCodeInfo> findAll() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            List<AccessCodeInfo> result = new ArrayList<>();
            for (Map<String, Object> row : mapper.findAllRows()) {
                result.add(toInfo(row, mapper));
            }
            return result;
        }
    }

    @Override
    public AccessCodeInfo findByCode(String code) {
        if (code == null) {
            return null;
        }
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            Map<String, Object> row = mapper.findRow(code);
            return row == null ? null : toInfo(row, mapper);
        }
    }

    @Override
    public void save(AccessCodeInfo info) {
        if (info == null || info.getCode() == null) {
            return;
        }
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            mapper.deleteAgents(info.getCode());
            mapper.deleteInfo(info.getCode());
            mapper.insertInfo(info);
            for (AccessCodeInfo.AgentAccessStat stat : info.getAgents()) {
                mapper.insertAgent(info.getCode(), stat);
            }
            session.commit();
        }
    }

    @Override
    public void delete(String code) {
        if (code == null) {
            return;
        }
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            mapper.deleteAgents(code);
            mapper.deleteInfo(code);
        }
    }

    @Override
    public void recordAgent(String code, AccessCodeInfo.AgentAccessStat stat) {
        if (code == null || stat == null) {
            return;
        }
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            mapper.removeAgent(code, stat.getAgentId());
            mapper.insertAgent(code, stat);
            mapper.updateUsedAgents(code, mapper.countAgents(code));
        }
    }

    @Override
    public void removeAgent(String code, String agentId) {
        if (code == null || agentId == null) {
            return;
        }
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AccessCodeMapper mapper = session.getMapper(AccessCodeMapper.class);
            mapper.removeAgent(code, agentId);
            mapper.updateUsedAgents(code, mapper.countAgents(code));
        }
    }

    /** 行 → 模型（含接入明细） */
    private AccessCodeInfo toInfo(Map<String, Object> row, AccessCodeMapper mapper) {
        AccessCodeInfo info = new AccessCodeInfo();
        info.setCode(str(val(row, "code")));
        info.setType(str(val(row, "type")));
        info.setStatus(str(val(row, "status")));
        info.setCreatedAt(str(val(row, "created_at")));
        info.setExpiresAt(str(val(row, "expires_at")));
        info.setIpWhitelist(str(val(row, "ip_whitelist")));
        info.setMaxAgents(num(val(row, "max_agents")));
        info.setUsedAgents(num(val(row, "used_agents")));
        for (Map<String, Object> agent : mapper.findAgents(info.getCode())) {
            info.getAgents().add(new AccessCodeInfo.AgentAccessStat(
                    str(val(agent, "agent_id")), str(val(agent, "ip")), str(val(agent, "time"))));
        }
        return info;
    }

    /**
     * 从结果行取值（兼容列名大小写：H2 默认大写 CODE/TYPE，MySQL 默认小写 code/type）。
     *
     * @param row 结果行
     * @param key 期望列名（小写）
     * @return 值（未找到返回 null）
     */
    private static Object val(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int num(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    /**
     * MyBatis Mapper 接口（SQL 定义见 resources/mapper/AccessCodeMapper.xml）。
     *
     * <p>本接口仅作方法声明，所有 SQL 统一在 XML mapper 中定义，
     * 禁止使用 {@code @Select}/{@code @Insert}/{@code @Update}/{@code @Delete} 注解编写 SQL（P3C 规约四）。</p>
     */
    public interface AccessCodeMapper {

        void createCodeTable();

        void addIpWhitelistColumn();

        void createAgentTable();

        List<Map<String, Object>> findAllRows();

        Map<String, Object> findRow(@Param("code") String code);

        List<Map<String, Object>> findAgents(@Param("code") String code);

        int countAgents(@Param("code") String code);

        void insertInfo(@Param("info") AccessCodeInfo info);

        void deleteInfo(@Param("code") String code);

        void deleteAgents(@Param("code") String code);

        void insertAgent(@Param("code") String code, @Param("stat") AccessCodeInfo.AgentAccessStat stat);

        void removeAgent(@Param("code") String code, @Param("agentId") String agentId);

        void updateUsedAgents(@Param("code") String code, @Param("count") int count);
    }
}
