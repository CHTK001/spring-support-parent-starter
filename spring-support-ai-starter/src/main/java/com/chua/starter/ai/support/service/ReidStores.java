package com.chua.starter.ai.support.service;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.util.AiUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重识别向量库的**门面**：按当前登录账号把请求路由到该账号自己的重识别向量库。
 *
 * <p><b>为什么必须有这一层。</b>重识别底库（{@code sys_ai_reid_library}）此前
 * 只存标量元数据，<b>向量从来没有被写进去过</b> ——
 * {@link ReidLibraryLinkService#insert} 的向量参数一直传的是 {@code null, null}，
 * 于是 {@code if (storage == null || feature == null)} 直接跳过向量写入。
 * 后果是：重识别的「以图搜人」只能搜内存里的实时归并（{@code PersonTrack}），
 * <b>进程一重启，历史观测就再也搜不到了</b>。</p>
 *
 * <p>本类补上这一段：每次观测除了写 DB 元数据，还把特征写进<b>按账号隔离的持久向量库</b>，
 * 于是「上传一张照片 → 这个人过去出现在哪」在重启后依然成立。</p>
 *
 * <p><b>与 {@link FaceStores} 同一套口径：</b></p>
 * <ul>
 *   <li>按账号隔离：每个账号 {@code <根>/<账号>/reid/} 一个目录，互不可见；</li>
 *   <li>{@code reid-index.vectors}：jvector 索引（可重建，不是权威）；</li>
 *   <li>{@code index.json}：<b>只存 id + 向量</b>，启动据此重建索引 ——
 *       <b>档案的权威是 DB</b>（{@code sys_ai_reid_library}），
 *       快照只承担「向量重建的来源」这一件事，不重复落盘元数据。</li>
 * </ul>
 *
 * <p><b>与人脸管线的关系。</b>向量库本身的配置（向量存储、比较算法、特征维度）
 * 与检测/特征模型一样归属于 {@code face} 分组（管线配置分组），
 * 但向量的落盘路径仍走 {@code reid} 目录冻结，两者不冲突。</p>
 *
 * <p><b>模型不重复加载</b>：本类不碰 ONNX，只管向量；特征由
 * {@link com.chua.starter.ai.support.configuration.reid.ReidClientManager} 共享的
 * 特征提取器产出。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReidStores {

    /**
     * 索引文件名（jvector 会在其后追加 .vectors）
     */
    private static final String INDEX_NAME = "reid-index";

    /**
     * 快照文件名
     */
    private static final String SNAPSHOT_NAME = "index.json";

    /**
     * 账号目录下的子目录名
     */
    private static final String LEAF = "reid";

    /**
     * 底库根目录的系统属性名（缺省为进程工作目录）
     */
    private static final String BASE_DIR_PROPERTY = "reid.store.dir";

    /**
     * 配置分组名：向量库的配置（vector_storage / vector_algorithm / feature_dimension）
     * 与检测/特征模型同归属人脸组（管线共享同一组，底库目录仍走 reid）。
     */
    private static final String GROUP = "face";

    /**
     * 默认特征维度
     */
    private static final int DEFAULT_DIMENSION = 512;

    /**
     * 快照落盘的最小间隔（毫秒）。
     *
     * <p>观测是每帧新增的，逐条落盘会把整份 {@code index.json} 反复重写（O(n) 写放大）。
     * 用 5 秒的窗口把同一批观测攒起来一次落盘 —— 代价是硬崩时最多丢最近 5 秒的
     * <b>向量</b>（DB 里的观测行仍在，只是重启后搜不到），这个取舍对实时监控是可接受的。</p>
     */
    private static final long FLUSH_INTERVAL_MS = 5_000L;

    /**
     * 待落盘条目的强制落盘阈值。
     *
     * <p>刚启动时「距上次落盘」恒为很久以前，间隔条件会立刻满足；
     * 但若观测在 5 秒内涌入上千条，只靠间隔会让内存快照越积越多。
     * 超过这个数量就立即落盘一次，避免「攒太多、崩一次全丢」。</p>
     */
    private static final int FLUSH_PENDING_LIMIT = 200;

    /**
     * 账号 -> 该账号的重识别向量库
     */
    private final Map<String, AccountReid> accounts = new ConcurrentHashMap<>();

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动时只记录根目录：向量库是懒建的，某个账号从没用过就不该凭空多出目录。
     */
    @PostConstruct
    public void init() {
        log.info("[Reid] 重识别向量库按账号隔离已启用（根目录 {}）", baseDir().toAbsolutePath());
    }

    /**
     * 关闭全部账号的向量存储。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, AccountReid> e : accounts.entrySet()) {
            try {
                // 先补一次落盘再关：攒在内存里的待落盘观测不能随进程一起消失
                e.getValue().flush();
            } catch (Exception ex) {
                log.warn("[Reid] 关闭前落盘账号 '{}' 快照失败: {}", e.getKey(), ex.getMessage());
            }
            try {
                e.getValue().close();
            } catch (Exception ex) {
                log.debug("[Reid] 关闭账号 '{}' 重识别向量库失败: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    /**
     * 当前账号的向量存储。
     *
     * <p><b>为什么直接暴露存储而不是包一个 add 方法。</b>观测的向量必须与 DB 行
     * <b>原子地</b>写入 —— {@link ReidLibraryLinkService#insert} 已经实现了
     * 「向量失败则回滚 DB 行」。若这里再包一层 add，业务侧就得自己重做这套回滚，
     * 两处逻辑迟早分叉。因此写入走那一处，本类只负责<b>把写成功的向量记进快照</b>
     * （见 {@link #remember}）。</p>
     *
     * @param group 分组名（空值按默认组）
     * @return 向量存储；未就绪时返回 {@code null}
     */
    public VectorStorage currentVectorStorage(String group) {
        return st(group).storage();
    }

    /**
     * 以特征在**多个分组**里检索最相似的观测。
     *
     * <p><b>分组是隔离手段，但检索常要跨组</b>（「在员工库和访客库里一起找」）。
     * 空值只查默认组 —— <b>不传不等于搜全部</b>，否则调用方漏传参数就会
     * 把本该隔离的数据带出来。</p>
     *
     * @param groups  分组名列表（空/未指定 = 只查默认组）
     * @param feature 查询特征
     * @param topK    返回条数
     * @return 命中列表（按相似度降序，已跨组归并）；向量库未就绪时返回空列表
     */
    public List<Vector> search(List<String> groups, float[] feature, int topK) {
        if (feature == null || feature.length == 0 || topK <= 0) {
            return List.of();
        }
        List<Vector> merged = new ArrayList<>();
        for (String group : normalizeGroups(groups)) {
            VectorStorage storage = st(group).storage();
            if (storage == null) {
                continue;
            }
            try {
                // 每组都取 topK：某组内的强命中不该因为「全局只留 K 条」而被挤掉
                List<Vector> hits = storage.search(feature, topK);
                if (hits != null) {
                    merged.addAll(hits);
                }
            } catch (Exception ex) {
                log.warn("[Reid] 分组 '{}' 检索失败（跳过该组）: {}", group, ex.getMessage());
            }
        }
        merged.sort((a, b) -> Double.compare(scoreOf(b), scoreOf(a)));
        if (merged.size() > topK) {
            return new ArrayList<>(merged.subList(0, topK));
        }
        return merged;
    }

    /**
     * 取向量的相似度分数（向量库把它写在 metadata 的 score 键）。
     *
     * @param v 向量
     * @return 相似度；缺失返回 0
     */
    private static double scoreOf(Vector v) {
        if (v != null && v.metadata() != null && v.metadata().get("score") instanceof Number n) {
            return n.doubleValue();
        }
        return 0d;
    }

    /**
     * 删除一条向量（同时清掉快照里的条目）。
     *
     * @param group 分组名（空值按默认组）
     * @param id    向量标识
     * @return 是否成功
     */
    public boolean remove(String group, String id) {
        if (id == null) {
            return false;
        }
        AccountReid account = st(group);
        VectorStorage storage = account.storage();
        try {
            if (storage != null) {
                storage.remove(id);
            }
            if (account.snapshot.remove(id) != null) {
                account.persistNow();
            }
            return true;
        } catch (Exception ex) {
            log.warn("[Reid] 删除重识别向量失败: group={}, id={} -> {}", group, id, ex.getMessage());
            return false;
        }
    }

    /**
     * 清空指定分组的全部向量（与快照）。
     *
     * @param group 分组名（空值按默认组）
     */
    public void clear(String group) {
        AccountReid account = st(group);
        try {
            VectorStorage storage = account.storage();
            if (storage != null) {
                storage.clear();
            }
            account.snapshot.clear();
            account.persistNow();
        } catch (Exception ex) {
            log.warn("[Reid] 清空重识别向量库失败: group={} -> {}", group, ex.getMessage());
        }
    }

    /**
     * 指定分组已入库的向量数量。
     *
     * @param group 分组名（空值按默认组）
     * @return 数量
     */
    public int size(String group) {
        VectorStorage storage = st(group).storage();
        return storage == null ? 0 : storage.size();
    }

    /**
     * 记下一条**已经写入向量库**的观测，供重启后重建索引。
     *
     * <p><b>为什么与写入分开。</b>观测的向量是业务侧通过
     * {@link ReidLibraryLinkService#insert} 连同 DB 行一起写的（那里有
     * 「向量失败则回滚 DB 行」的原子性保证）。本方法只负责把这条向量<b>记进快照</b>，
     * 不重复写向量库 —— 于是既保留原有的回滚语义，又让快照与向量库保持一致。</p>
     *
     * <p><b>为什么不是每次都落盘。</b>人脸登记是用户手动触发的低频操作，
     * 每次都写 {@code index.json} 无所谓；而重识别是<b>每帧都可能新增观测</b>，
     * 每帧把整份快照重写一遍是 O(n) 的写放大，跑几分钟就会把磁盘拖垮。
     * 因此这里按「距上次落盘超过 {@link #FLUSH_INTERVAL_MS}」或
     * 「待落盘条目超过 {@link #FLUSH_PENDING_LIMIT}」两个条件择一触发，
     * 其余情况只更新内存快照；进程正常关闭时由 {@link #shutdown()} 兜底全量落盘。</p>
     *
     * @param group   分组名（空值按默认组）
     * @param id      向量标识
     * @param feature 特征向量
     */
    public void remember(String group, String id, float[] feature) {
        if (id == null || feature == null || feature.length == 0) {
            return;
        }
        st(group).remember(id, feature);
    }

    /**
     * 把当前账号**指定分组**的快照强制落盘。
     *
     * @param group 分组名（空值按默认组）
     */
    public void flush(String group) {
        st(group).flush();
    }

    // ==================== 内部：账号解析 ====================

    /**
     * 当前账号**默认分组**的重识别向量库（不存在则懒建）。
     *
     * @return 账号向量库
     */
    private AccountReid st() {
        return st(AiLibraryGroups.DEFAULT);
    }

    /**
     * 当前账号指定分组的重识别向量库（不存在则懒建）。
     *
     * @param group 分组名（空值按默认组）
     * @return 账号 + 分组的向量库
     */
    private AccountReid st(String group) {
        String account = AiUserContext.requireUserId();
        String normalized = AiLibraryGroups.normalize(group);
        return accounts.computeIfAbsent(key(account, normalized), k -> create(account, normalized));
    }

    /**
     * 账号 + 分组的缓存键。
     *
     * <p>用 {@code \u0000} 分隔而不是可打印字符：账号名与分组名都可能含
     * 用户自定义字符，用 {@code /} 之类会出现「(a, b/c) 与 (a/b, c) 撞键」
     * 这种把两套向量库混在一起的静默错误。</p>
     *
     * @param account 账号标识
     * @param group   已归一化的分组名
     * @return 缓存键
     */
    private static String key(String account, String group) {
        return account + '\u0000' + group;
    }

    /**
     * 创建一个账号 + 分组的重识别向量库并加载快照。
     *
     * @param account 账号标识
     * @param group   已归一化的分组名
     * @return 账号向量库
     */
    private AccountReid create(String account, String group) {
        AccountReid created = new AccountReid(account, group, accountDir(account, group));
        created.reload();
        return created;
    }

    /**
     * 底库根目录。
     *
     * @return 根目录
     */
    private Path baseDir() {
        return Paths.get(System.getProperty(BASE_DIR_PROPERTY, "."));
    }

    /**
     * 某账号某分组的向量库目录（{@code <根>/<账号>/<分组>/reid}）。
     *
     * <p>分组进目录而不是进元数据过滤：topK 语义才精确
     * （过滤方案下「取前 K 再筛掉别的组」会返回不足 K 条），
     * 且删掉一整组就是删目录这么简单。</p>
     *
     * @param account 账号标识
     * @param group   分组名
     * @return 目录
     */
    private Path accountDir(String account, String group) {
        return baseDir()
                .resolve(sanitize(account))
                .resolve(AiLibraryGroups.sanitize(group))
                .resolve(LEAF);
    }

    /**
     * 归一化分组列表：空值只查默认组（**不**等于查全部）。
     *
     * @param groups 原始分组列表
     * @return 归一化后的分组列表，至少一项
     */
    private static List<String> normalizeGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of(AiLibraryGroups.DEFAULT);
        }
        List<String> out = new ArrayList<>(groups.size());
        for (String g : groups) {
            String n = AiLibraryGroups.normalize(g);
            if (!out.contains(n)) {
                out.add(n);
            }
        }
        return out.isEmpty() ? List.of(AiLibraryGroups.DEFAULT) : out;
    }

    /**
     * 把账号标识收敛成安全的目录名，避免把写入引出根目录。
     *
     * @param account 原始账号标识
     * @return 可安全作为单级目录名的字符串
     */
    private static String sanitize(String account) {
        if (account == null || account.isBlank()) {
            return "_unknown";
        }
        String s = account.trim()
                .replace("\\", "_").replace("/", "_").replace(":", "_")
                .replace("*", "_").replace("?", "_").replace("\"", "_")
                .replace("<", "_").replace(">", "_").replace("|", "_");
        s = s.replace("..", "_");
        s = s.replaceAll("^[.\\s]+|[.\\s]+$", "");
        return s.isBlank() ? "_unknown" : s;
    }

    /**
     * 构建向量存储（按当前配置）。
     *
     * @param indexPath 索引路径（不含扩展名）
     * @return 向量存储；构建失败返回 {@code null}
     */
    private VectorStorage buildStorage(Path indexPath) {
        String name = settingReader.getStr(GROUP, "vector_storage");
        if (name == null || name.isBlank()) {
            name = "jvector";
        }
        VectorCompareAlgorithm algorithm = resolveAlgorithm(
                settingReader.getStr(GROUP, "vector_algorithm"));
        int dimension = settingReader.getInt(GROUP, "reid_feature_dimension", 0);
        if (dimension <= 0) {
            // 留空 = 与 ReID 特征共用人脸特征维度（两套特征走同一维度的常见情形）
            dimension = settingReader.getInt(GROUP, "feature_dimension", DEFAULT_DIMENSION);
        }
        Map<String, Object> config = new LinkedHashMap<>(2);
        // jvector **不会自动创建缺失的父目录**：父目录不存在时写入静默失败，
        // 表现为「入库成功但重启即丢」。所以必须先建目录。
        try {
            Files.createDirectories(indexPath.toAbsolutePath().getParent());
        } catch (Exception ex) {
            log.warn("[Reid] 重识别向量索引目录创建失败，索引可能无法落盘: {} -> {}",
                    indexPath.getParent(), ex.getMessage());
        }
        config.put("indexPath", indexPath.toAbsolutePath().toString());
        try {
            return VectorStorageProvider.createDynamic(name, dimension, algorithm, config);
        } catch (Exception ex) {
            log.warn("[Reid] 构建重识别向量库失败: name={} -> {}", name, ex.getMessage());
            return null;
        }
    }

    /**
     * 解析比较算法。
     *
     * @param name 算法名（EUCLIDEAN / COSINE / DOT）
     * @return 比较算法；无法识别时回落余弦
     */
    private static VectorCompareAlgorithm resolveAlgorithm(String name) {
        if (name == null || name.isBlank()) {
            return VectorCompareAlgorithm.cosine();
        }
        return switch (name.trim().toUpperCase()) {
            case "EUCLIDEAN" -> VectorCompareAlgorithm.euclidean();
            case "DOT" -> VectorCompareAlgorithm.dotProduct();
            default -> VectorCompareAlgorithm.cosine();
        };
    }

    /**
     * 单个账号的重识别向量库：向量存储 + 磁盘快照。
     */
    private final class AccountReid {

        /**
         * 账号标识
         */
        private final String account;

        /**
         * 分组名（已归一化）。
         *
         * <p>落库时随观测写进 {@code sys_ai_reid_library_group}，检索命中后按它反查轨迹 ——
         * 所以必须由这里持有，不能从目录名反推（目录名是 sanitize 过的，可能与真实分组名不同）。</p>
         */
        private final String group;

        /**
         * 本账号本分组目录
         */
        private final Path dir;

        /**
         * 快照：id -> 特征向量。向量重建的来源
         */
        private final Map<String, float[]> snapshot = new LinkedHashMap<>();

        /**
         * 待落盘的条目数（用于节流判断）
         */
        private int pending;

        /**
         * 上次落盘时刻（毫秒）
         */
        private long lastPersistAt;

        /**
         * 向量存储
         */
        private volatile VectorStorage storage;

        /**
         * Account Reid
         * @param account 字符串
         * @param dir 路径
         * @param group 字符串
         * @return 返回值
        */
        AccountReid(String account, String group, Path dir) {
            this.account = account;
            this.group = group;
            this.dir = dir;
        }

        VectorStorage storage() {
            return storage;
        }

        /**
         * 重建：读快照 → 建存储 → <b>先清空索引</b> → 按快照重新灌入向量。
         *
         * <p>「先清空」是必须的：jvector 有自己的侧录文件（{@code <indexPath>.vectors}），
         * 里面留着历史 id。若不清空，删除过的条目会以「幽灵向量」形式继续参与检索 ——
         * 快照里已经没有它，检索却还能命中。</p>
         */
        synchronized void reload() {
            try {
                Files.createDirectories(dir);
            } catch (Exception ex) {
                log.warn("[Reid] 账号 '{}' 目录创建失败: {} -> {}", account, dir, ex.getMessage());
            }
            loadSnapshot();
            storage = buildStorage(dir.resolve(INDEX_NAME));
            if (storage == null) {
                log.warn("[Reid] 账号 '{}' 重识别向量库构建失败，重识别检索不可用", account);
                return;
            }
            try {
                storage.clear();
            } catch (Exception ex) {
                log.debug("[Reid] 清空向量索引失败（继续按快照灌入）: {}", ex.getMessage());
            }
            int restored = 0;
            for (Map.Entry<String, float[]> e : snapshot.entrySet()) {
                try {
                    if (storage.add(e.getKey(), e.getValue())) {
                        restored++;
                    } else {
                        log.warn("[Reid] 账号 '{}' 快照条目灌入失败: id={}", account, e.getKey());
                    }
                } catch (Exception ex) {
                    log.warn("[Reid] 账号 '{}' 快照条目灌入异常: id={} -> {}",
                            account, e.getKey(), ex.getMessage());
                }
            }
            log.info("[Reid] 账号 '{}' 重识别向量库就绪: {}/{} 条（{}）",
                    account, restored, snapshot.size(), dir.resolve(SNAPSHOT_NAME));
        }

        /**
         * 记下一条已入库的观测，并按节流策略决定是否立即落盘。
         *
         * @param id      标识
         * @param feature 特征
         */
        synchronized void remember(String id, float[] feature) {
            snapshot.put(id, feature.clone());
            pending++;
            long now = System.currentTimeMillis();
            if (pending >= FLUSH_PENDING_LIMIT || now - lastPersistAt >= FLUSH_INTERVAL_MS) {
                flush();
            }
        }

        /**
         * 强制把快照落盘（重置节流窗口）。
         */
        synchronized void flush() {
            if (pending <= 0) {
                return;
            }
            persistNow();
        }

        /**
         * 立即落盘并重置节流状态。
         *
         * <p>与 {@link #persist()} 的区别：本方法同时把待落盘计数清零、
         * 刷新节流时间窗 —— 删除/清空这类必须立即可见的操作走这里。</p>
         */
        synchronized void persistNow() {
            persist();
            pending = 0;
            lastPersistAt = System.currentTimeMillis();
        }

        /**
         * 把快照写入磁盘。
         *
         * <p><b>失败必须抛出，不能只告警。</b>否则用户看到「入库成功」，
         * {@code index.json} 里却没有这条，重启后向量凭空消失 ——
         * 而 DB 里那条观测还在，于是「有记录、搜不到」。</p>
         *
         * <p>磁盘快照只写 {@code id + feature}：档案的权威在 DB
         * （{@code sys_ai_reid_library}），不重复落盘元数据。</p>
         *
         * @throws IllegalStateException 写盘失败
         */
        synchronized void persist() {
            Path file = dir.resolve(SNAPSHOT_NAME);
            try {
                Files.createDirectories(dir);
                List<Map<String, Object>> rows = new ArrayList<>(snapshot.size());
                for (Map.Entry<String, float[]> e : snapshot.entrySet()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", e.getKey());
                    row.put("feature", e.getValue());
                    rows.add(row);
                }
                byte[] raw = objectMapper.writeValueAsBytes(rows);
                Path tmp = file.resolveSibling(SNAPSHOT_NAME + ".tmp");
                Files.write(tmp, raw);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                log.error("[Reid] 账号 '{}' 重识别快照写入失败: {} -> {}", account, file, ex.getMessage(), ex);
                throw new IllegalStateException("重识别快照写入失败: " + file, ex);
            }
        }

        /**
         * 从磁盘读快照。
         */
        private void loadSnapshot() {
            snapshot.clear();
            Path file = dir.resolve(SNAPSHOT_NAME);
            if (!Files.isRegularFile(file)) {
                return;
            }
            try {
                List<Map<String, Object>> rows = objectMapper.readValue(
                        Files.readAllBytes(file), new TypeReference<List<Map<String, Object>>>() {
                        });
                for (Map<String, Object> row : rows) {
                    String id = str(row.get("id"));
                    float[] feature = floats(row.get("feature"));
                    if (id == null || feature == null || feature.length == 0) {
                        continue;
                    }
                    snapshot.put(id, feature);
                }
            } catch (Exception ex) {
                log.warn("[Reid] 账号 '{}' 重识别快照读取失败（按空库继续）: {} -> {}",
                        account, file, ex.getMessage());
            }
        }

        /**
         * 关闭向量存储。
         */
        void close() {
            VectorStorage current = storage;
            if (current != null) {
                try {
                    current.close();
                } catch (Exception ex) {
                    log.debug("[Reid] 关闭账号 '{}' 向量库失败: {}", account, ex.getMessage());
                }
            }
            storage = null;
        }
    }

    /**
     * 取字符串。
     *
     * @param value 值
     * @return 字符串；为空返回 {@code null}
     */
    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }

    /**
     * 取浮点数组。
     *
     * @param value 值
     * @return 数组；无法解析返回 {@code null}
     */
    private static float[] floats(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof float[] arr) {
            return arr.clone();
        }
        if (value instanceof List<?> list) {
            float[] out = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number n) {
                    out[i] = n.floatValue();
                } else {
                    return null;
                }
            }
            return out;
        }
        return null;
    }
}
