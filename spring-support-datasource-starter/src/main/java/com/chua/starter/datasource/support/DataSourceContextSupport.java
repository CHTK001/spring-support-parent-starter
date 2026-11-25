package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.chua.starter.datasource.support.DynamicDataSource.TARGET_DATA_SOURCES;

/**
 * 数据源管理器
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class DataSourceContextSupport {

    /**
     * 数据源上下文支持。
     */
    private DataSourceContextSupport() {
    }

    public static final Map<Object, Object> DATA_SOURCE_MAP = new ConcurrentHashMap<>(); // 数据源映射

    /**
     * 数据源注册顺序（按首次注册先后记录）
     * <p>用于默认数据源判定：未注册名为 {@code master} 的数据源时，取第一个注册的数据源
     * 充当默认源（master 角色）。同名重复注册不改变次序；条目失效（从
     * {@link #DATA_SOURCE_MAP} 移除）后由读取方跳过。</p>
     */
    public static final List<String> DATA_SOURCE_ORDER = new CopyOnWriteArrayList<>();

    /**
     * 运行期聚合源（分片 {@code shardingDataSource} / Calcite 联邦 {@code calciteDataSource}）
     * 的默认源候选，按注册先后记录。
     * <p>当业务数据源恰好只有一个时，在册的聚合源临时充当默认源（master 角色），
     * 业务免 {@code @DS} 直走分片/联邦；聚合源摘除后由
     * {@link #resolveCompositeDefaultSource(Map)} 返回 null，默认源自动还原为原业务源。
     * 多数据源场景不接管，默认源仍为 {@code master}/第一个注册源。</p>
     */
    public static final List<String> COMPOSITE_DEFAULT_CANDIDATES = new CopyOnWriteArrayList<>();

    /**
     * 静态注册的分片数据源 Bean 名（列表化 {@code plugin.datasource.sharding[*].name}，
     * 含缺省 {@code shardingDataSource}）。
     * <p>用于分片成员收集过滤：分片数据源之间不互为成员。运行期动态建源
     * （ShardingRuleManager）仍走 {@link #registerCompositeDefaultSource(String)} /
     * {@link #unregisterCompositeDefaultSource(String)}。</p>
     */
    public static final Set<String> SHARDING_BEAN_NAMES = ConcurrentHashMap.newKeySet();

    /**
     * 默认数据源
     */
    public static final String DEFAULT_DATASOURCE = "master";
    /**
     * 本地安全处理数据源
     */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 数据源上下文栈（用于 @dstransactional 嵌套切换）
    */
    private static final ThreadLocal<Deque<String>> DS_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 设置数据源名
     *
     * @param dbType 数据类型
     */
    public static void setDbType(String dbType) {
        CONTEXT_HOLDER.set(dbType);
 // 当 @dstransactional 激活时，自动追踪已使用的数据源
        MultiDSTransactionContext ctx = MultiDSTransactionContext.getCurrent();
        if (ctx != null) {
            ctx.pushDataSource(dbType);
        }
    }

    /**
     * 推入数据源上下文栈（用于嵌套切换时的上下文恢复）
     *
     * @param dsName 数据源名称
     */
    public static void pushDbType(String dsName) {
        DS_STACK.get().push(dsName);
    }

    /**
     * 弹出数据源上下文栈，恢复上一级上下文
     *
     * @return 上一级数据源名称（栈空时返回 空）
     */
    public static String popDbType() {
        Deque<String> stack = DS_STACK.get();
        stack.poll(); // 移除当前
        String previous = stack.peek(); // 获取上一级
        if (previous == null) {
            DS_STACK.remove();
        }
        return previous;
    }

    /**
     * 添加数据源
     * <p>若 SQL 监听已启用，注册前会统一包装数据源（幂等），实现纯 JDBC 层 SQL 监控。</p>
     * <p>首次注册会按先后顺序记录到 {@link #DATA_SOURCE_ORDER}，作为「未命名 master 时取第一个」的依据。</p>
     *
     * @param name       名称
     * @param dataSource 添加数据源
     */
    public static void addDatasource(String name, DataSource dataSource) {
        DataSource target = com.chua.starter.datasource.monitor.DataSourceSqlMonitor.wrapIfEnabled(name, dataSource);
        Object previous = TARGET_DATA_SOURCES.put(name, target);
        if (null == previous && !DATA_SOURCE_ORDER.contains(name)) {
            // 首次注册才记录顺序；同名重复注册（含移除后重注册）不改变、不重复记录次序
            DATA_SOURCE_ORDER.add(name);
        }
        // 自动检测并注册数据源分组（如 slave_1 → slave 组）
        DataSourceGroupRegistry.register(name, target);
    }

    /**
     * 登记运行期聚合源为默认源候选（分片/联邦刷新建源后调用）
     *
     * @param name 聚合源名称（如 shardingDataSource / calciteDataSource）
     */
    public static void registerCompositeDefaultSource(String name) {
        if (name != null && !COMPOSITE_DEFAULT_CANDIDATES.contains(name)) {
            COMPOSITE_DEFAULT_CANDIDATES.add(name);
        }
    }

    /**
     * 登记静态分片数据源 Bean 名（列表化配置注册 Bean 时调用）
     *
     * @param name 分片数据源 Bean 名
     */
    public static void registerShardingBeanName(String name) {
        if (name != null && !name.isBlank()) {
            SHARDING_BEAN_NAMES.add(name);
        }
    }

    /**
     * 注销运行期聚合源的默认源候选（分片/联邦摘除后调用），默认源随之自动还原
     *
     * @param name 聚合源名称
     */
    public static void unregisterCompositeDefaultSource(String name) {
        if (name != null) {
            COMPOSITE_DEFAULT_CANDIDATES.remove(name);
        }
    }

    /**
     * 解析「聚合源接管默认源」：仅当业务数据源恰好一个且存在在册聚合源时生效
     * <p>聚合源候选必须仍存在于当前注册表，防止默认源悬空指向已摘除的聚合源。</p>
     *
     * @param sources 当前数据源注册表
     * @return 应充当默认源的聚合源；条件不满足返回 null（调用方回退常规规则）
     */
    public static Object resolveCompositeDefaultSource(Map<Object, Object> sources) {
        if (COMPOSITE_DEFAULT_CANDIDATES.isEmpty() || sources.isEmpty()) {
            return null;
        }
        long businessCount = sources.keySet().stream()
                .filter(key -> !COMPOSITE_DEFAULT_CANDIDATES.contains(String.valueOf(key)))
                .count();
        if (businessCount != 1) {
            return null;
        }
        for (String name : COMPOSITE_DEFAULT_CANDIDATES) {
            Object dataSource = sources.get(name);
            if (dataSource != null) {
                return dataSource;
            }
        }
        return null;
    }

    /**
     * 是否存在数据源
     *
     * @param dbType 数据源名称
     * @return 是否存在数据源
     */
    public static boolean hasDbType(String dbType) {
        return TARGET_DATA_SOURCES.containsKey(dbType);
    }

    /**
     * 获取数据源
     *
     * @param name 名称
     * @return dataSource 数据源
     */
    public static DataSource getDatasource(String name) {
        return (DataSource) TARGET_DATA_SOURCES.get(name);
    }

    /**
     * 获取所有数据源
     *
     * @return 所有数据源Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, DataSource> getAllDataSources() {
        Map<String, DataSource> result = new ConcurrentHashMap<>();
        for (Map.Entry<Object, Object> entry : TARGET_DATA_SOURCES.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof DataSource) {
                result.put((String) entry.getKey(), (DataSource) entry.getValue());
            }
        }
        return result;
    }

    /**
     * 获取数据源名
     *
     * @return 获取数据源名
     */
    public static String getDbType() {
        return (CONTEXT_HOLDER.get());
    }

    /**
     * 清除数据源名
     */
    public static void clearDbType() {
        CONTEXT_HOLDER.remove();
    }
}
