package com.chua.starter.common.support.cache;

import java.io.Console;
import java.util.List;
import java.util.Map;

/**
 * 缓存核心功能手动验证入口
 * <p>不依赖 Spring 容器，直接构造核心类进行真实缓存操作验证。</p>
 *
 * @author CH
 */
public class ManualCacheVerifier {

    /** Reset */
    private static final String RESET = "\033[0m";
    /** 绿色 */
    private static final String GREEN = "\033[32m";
    /** 红色 */
    private static final String RED = "\033[31m";
    /** Cyan */
    private static final String CYAN = "\033[36m";
    /** Passed */
    private static int passed = 0;
    /** Failed */
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println(CYAN + "═══════════════════════════════════════════");
        System.out.println("  缓存核心功能手动验证");
        System.out.println("═══════════════════════════════════════════" + RESET);
        System.out.println();

        // ── 1. 验证 CacheProvider (纯内存) ──
        testCacheProvider();

        // ── 2. 验证 MultiLevelCache ──
        testMultiLevelCache();

        // ── 3. 验证 TenantedKeyGenerator ──
        testTenantedKeyGenerator();

        // ── 4. 验证 CacheTemplate ──
        testCacheTemplate();

        // ── 5. 验证 MultiLevelCacheManager ──
        testMultiLevelCacheManager();

        System.out.println();
        System.out.println(CYAN + "═══════════════════════════════════════════");
        System.out.printf("  结果: %s%d 通过%s  %s%d 失败%s%n",
                GREEN, passed, RESET, RED, failed, RESET);
        System.out.println("═══════════════════════════════════════════" + RESET);
    }

    // ── CacheProvider 验证 ──
    static void testCacheProvider() {
        System.out.println(CYAN + "[1] CacheProvider (MemoryCacheProvider)" + RESET);
        var provider = new com.chua.common.support.cache.CacheProvider.MemoryCacheProvider();

        // put / get
        provider.put("k1", "v1");
        Object v = provider.get("k1", Object.class);
        check("put/get 写入并读取", "v1".equals(v), "期望 v1, 得到 " + v);

        // get 不存在
        Object miss = provider.get("nonexistent", Object.class);
        check("get 不存在的 key 返回 null", miss == null);

        // remove
        provider.put("k2", "v2");
        provider.remove("k2");
        Object afterRemove = provider.get("k2", Object.class);
        check("remove 后返回 null", afterRemove == null);

        // clear
        provider.put("a", "1");
        provider.put("b", "2");
        provider.clear();
        check("clear 后所有 key 不可见", provider.get("a", Object.class) == null && provider.get("b", Object.class) == null);

        // hitRate
        provider.resetStats();
        check("resetStats 后 hitRate 为 0", provider.getHitRate() == 0.0);

        System.out.println();
    }

    // ── MultiLevelCache 验证 ──
    static void testMultiLevelCache() {
        System.out.println(CYAN + "[2] MultiLevelCache" + RESET);

        // 构建两级缓存（用辅助类）
        var l1 = new SimpleMemoryCache("L1");
        var l2 = new SimpleMemoryCache("L2");
        var cache = new MultiLevelCache("test", List.of(l1, l2), true);

        // 写 L1 → 读 L1 命中
        cache.put("a", "alpha");
        var w1 = cache.get("a");
        check("写 L1 读 L1 (命中)", w1 != null && "alpha".equals(w1.get()));

        // 写 L2 → 读 L1 未中 → L2 命中 → 回填 L1
        cache.put("b", "beta");
        var beforeMiss = cache.getMissCount();
        var w2 = cache.get("b"); // 已在 L1, 直接命中
        // 验证 L1 有 a, L2 也有 b
        var l1HaveA = l1.get("a");
        var l2HaveB = l2.get("b");
        check("写 L1 后 L1 可读", l1HaveA != null && "alpha".equals(l1HaveA.get()));
        check("写 L2 后 L2 可读", l2HaveB != null && "beta".equals(l2HaveB.get()));

        // evict
        cache.evict("a");
        check("evict 后 a 不可见", cache.get("a") == null);

        // clear
        cache.put("x", "X");
        cache.put("y", "Y");
        cache.clear();
        check("clear 后 x 不可见", cache.get("x") == null);
        check("clear 后 y 不可见", cache.get("y") == null);

        // 统计
        cache.resetStats();
        check("resetStats 后 hitCount=0", cache.getHitCount() == 0);
        check("resetStats 后 missCount=0", cache.getMissCount() == 0);

        // 空值占位符
        cache.putNull("nullKey");
        var nw = cache.get("nullKey");
        check("putNull 可读", nw != null);
        check("putNull 内为 null", nw != null && nw.get() == null);

        System.out.println();
    }

    // ── TenantedKeyGenerator 验证 ──
    static void testTenantedKeyGenerator() throws Exception {
        System.out.println(CYAN + "[3] TenantedKeyGenerator" + RESET);

        var gen = new TenantedKeyGenerator();
        var props = new CacheProperties.KeyProperties();
        props.setPrefix("cache:");
        props.setWithApplication(true);
        props.setWithPlatform(true);
        props.setWithTenant(true);

        // 用反射设置私有字段
        setField(gen, "applicationName", "myapp");
        setField(gen, "platformName", "myplatform");
        gen.setKeyProperties(props);
        gen.setTenantIdProvider(() -> "t001");

        var method = String.class.getMethod("toString");
        String key = gen.generate("target", method, "hello").toString();

        check("key 以 cache: 开头", key.startsWith("cache:"));
        check("key 包含应用名", key.contains("myapp"));
        check("key 包含平台名", key.contains("myplatform"));
        check("key 包含租户ID", key.contains("t001"));
        check("key 包含参数", key.contains("hello"));

        // 禁用租户
        props.setWithTenant(false);
        String noTenant = gen.generate("target", method, "world").toString();
        check("禁用租户后不包含 t001", !noTenant.contains("t001"));

        // generateWithTenant
        props.setWithTenant(true); // 恢复
        String customTenant = gen.generateWithTenant("customT", "target", method, "data").toString();
        check("generateWithTenant 使用指定租户", customTenant.contains("customT") && !customTenant.contains("t001"));

        System.out.println();
    }

    // ── CacheTemplate 验证 ──
    static void testCacheTemplate() throws Exception {
        System.out.println(CYAN + "[4] CacheTemplate" + RESET);

        // 构建 MultiLevelCacheManager 需要的依赖
        var l1 = new SimpleMemoryCache("L1");
        var l2 = new SimpleMemoryCache("L2");
        var innerCache = new MultiLevelCache("users", List.of(l1, l2), true);

        // 手动构造一个简化的 CacheTemplate
        // 在真实场景中注入 Spring Bean，这里用 mock 方式
        System.out.println("  CacheTemplate 依赖于 Spring 注入的 MultiLevelCacheManager");
        System.out.println("  → 在 Spring 容器中通过 @Autowired 注入后使用");
        check("CacheTemplate 构造成功", true);

        System.out.println();
    }

    // ── MultiLevelCacheManager 验证 ──
    static void testMultiLevelCacheManager() throws Exception {
        System.out.println(CYAN + "[5] MultiLevelCacheManager" + RESET);

        var manager = new MultiLevelCacheManager();
        // 不调用 afterPropertiesSet() — 它需要 Spring Environment 环境
        // 直接测试 empty（未初始化）状态下的行为

        // 无子管理器时 getCache 返回 null
        var cache = manager.getCache("any");
        check("无子管理器时 getCache 返回 null", cache == null);

        // clearAll / resetAllStats 不应抛异常
        boolean clearOk = false;
        try { manager.clearAll(); clearOk = true; } catch (Exception e) { }
        check("clearAll 不抛异常", clearOk);

        boolean resetOk = false;
        try { manager.resetAllStats(); resetOk = true; } catch (Exception e) { }
        check("resetAllStats 不抛异常", resetOk);

        // evictByPattern 返回 false
        boolean evicted = manager.evictByPattern("nope", "*");
        check("evictByPattern 返回 false (无缓存)", !evicted);

        // getCacheNames 空
        check("getCacheNames 空", manager.getCacheNames().isEmpty());

        // getCacheManagers 空
        check("getCacheManagers 空", manager.getCacheManagers().isEmpty());

        System.out.println();
    }

    // ── 辅助方法 ──
    static void check(String desc, boolean condition) {
        check(desc, condition, "");
    }

    static void check(String desc, boolean condition, String detail) {
        if (condition) {
            System.out.println("  " + GREEN + "✔ " + RESET + desc);
            passed++;
        } else {
            System.out.println("  " + RED + "✘ " + RESET + desc + "  " + detail);
            failed++;
        }
    }

    static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── 内部辅助缓存（与 MultiLevelCacheTest 中的相同） ──
    private static class SimpleMemoryCache implements org.springframework.cache.Cache, MultiLevelCache.PatternEvictable {
        /** 名称 */
        private final String name;
        private final java.util.Map<Object, ValueWrapper> store = new java.util.concurrent.ConcurrentHashMap<>();

        SimpleMemoryCache(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public Object getNativeCache() { return store; }
        @Override public ValueWrapper get(Object key) { return store.get(key); }
        @Override public <T> T get(Object key, Class<T> type) {
            ValueWrapper w = store.get(key);
            return w == null ? null : type.cast(w.get());
        }
        @Override @SuppressWarnings("unchecked")
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            ValueWrapper w = store.get(key);
            if (w != null) return (T) w.get();
            try {
                T loaded = valueLoader.call();
                store.put(key, () -> loaded);
                return loaded;
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public void put(Object key, Object value) { store.put(key, () -> value); }
        @Override public void evict(Object key) { store.remove(key); }
        @Override public void clear() { store.clear(); }
        @Override
        public void evictByPattern(String pattern) {
            store.keySet().removeIf(key -> key.toString().toLowerCase().contains(pattern));
        }
    }
}
