package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.util.AiUserContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 以图搜图底库的**门面**：按当前登录账号把请求路由到该账号自己的
 * {@link ImageSearchStore}，异常时按分组路由到所请求的那一组。
 *
 * <p><b>为什么要有这一层。</b>底库原本是全局一份：所有账号共用同一个目录、
 * 同一份快照、同一套向量索引，A 账号的图 B 账号能检索到，删除也会互相
 * 影响。改成按账号 + 分组隔离后，「谁是当前账号」「属于哪个分组」这两件事必须在
 * 每个入口解析一次，而解析口径必须唯一（都在 {@link AiUserContext} 与
 * {@link com.chua.starter.ai.support.util.AiLibraryGroups}），否则迟早分叉。</p>
 *
 * <p><b>目录规则</b>：{@code <base>/<账号>/<分组>/imagesearch}（分组默认取
 * {@code default}），其中 base 取系统属性 {@code image.search.store.dir}，未设置时
 * 为进程工作目录。远端服务的工作目录是 {@code /opt/ai-starter/runtime}，因此静态
 * 账号 admin 的默认组底库落在 {@code /opt/ai-starter/runtime/admin/default/imagesearch/}。</p>
 *
 * <p><b>账号标识</b>：静态账号（本地开发/无外部认证中心时使用）的 userId 就是
 * 用户名，见 {@code StaticTempLogin#issue}，跨登录稳定；因此目录名可读且可区分。</p>
 *
 * <p><b>加载时机</b>：启动时扫描 base 下所有已存在的账号目录并加载（这样重启后
 * 各账号底库即时可用，统计卡也不会在首次访问前显示为空）；运行期遇到磁盘上还没有
 * 目录的账号则懒建。两个路径共用同一处创建逻辑，避免「启动加载」与「首次访问」
 * 对同一份数据给出不同结果。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageSearchStores {

    /** 账号底库子目录名（与旧版 {@code image-search-store} 区分开，便于迁移识别） */
    private static final String STORE_LEAF = "imagesearch";

    /** 旧版单目录布局的目录名（全局一份，按账号隔离前的形态） */
    private static final String LEGACY_DIR = "image-search-store";

    /**
     * 旧布局数据的归属账号。
     *
     * <p>写死 {@code admin} 的依据：改造前底库接口是 {@code @Permission("admin")}，
     * 只有管理员能上传，因此那个全局目录里的数据只可能是 admin 的。若将来放开给普通
     * 账号使用，这一段必须去掉——那时旧目录里可能混着多个账号的数据，无法自动归属。</p>
     */
    private static final String LEGACY_OWNER = "admin";

    /** 旧布局数据的归属分组。底库改造前是全局一份，归入 admin 的默认组 */
    private static final String LEGACY_GROUP = "default";

    /** 设置分组名（用于过滤设置变更事件，与 {@link ImageSearchStore#GROUP} 同源） */
    private static final String GROUP = "image_search";

    /** 底库根目录的系统属性名（沿用旧名，语义由「底库目录」变为「底库根目录」） */
    private static final String BASE_DIR_PROPERTY = "image.search.store.dir";

    /** 账号 -> 该账号的底库实例（按（账号, 分组）拆分：key = {@code account + "\0" + group}） */
    private final Map<String, ImageSearchStore> accounts = new ConcurrentHashMap<>();

    private final GlobalSettingReader settingReader;

    /**
     * 底库元数据与向量的联动层，传给各账号的 {@link ImageSearchStore}。
     */
    private final ImageLibraryLinkService library;

    /**
     * 图片关键词提取器（OCR / 多模态模型，按 image_search 配置互斥选择）。
     */
    private final ImageKeywordExtractor keywordExtractor;

    /**
     * 启动时加载磁盘上已有的全部账号底库。
     *
     * <p>只读目录、不建目录：某个账号从没上传过图就不该因为一次启动而凭空多出
     * 一个空目录。真正需要写入时由 {@link #storeOf(String, String)} 建。</p>
     */
    @PostConstruct
    public void load() {
        Path base = baseDir();
        if (!Files.isDirectory(base)) {
            log.info("[ImageSearch] 底库根目录不存在，从空库开始: {}", base.toAbsolutePath());
            return;
        }
        adoptLegacyLayout(base);
        int loaded = 0;
        try (Stream<Path> children = Files.list(base)) {
            List<Path> dirs = children
                    .filter(Files::isDirectory)
                    .filter(p -> Files.isDirectory(p.resolve(STORE_LEAF)))
                    .sorted()
                    .toList();
            for (Path accountDir : dirs) {
                String account = accountDir.getFileName().toString();
                try (Stream<Path> groups = Files.list(accountDir)) {
                    List<Path> groupDirs = groups
                            .filter(Files::isDirectory)
                            .filter(g -> Files.isDirectory(g.resolve(STORE_LEAF)))
                            .sorted()
                            .toList();
                    for (Path groupDir : groupDirs) {
                        String group = groupDir.getFileName().toString();
                        accounts.computeIfAbsent(account + "\0" + group,
                                k -> create(account, group, groupDir.resolve(STORE_LEAF), true));
                        loaded++;
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("[ImageSearch] 扫描底库根目录失败，按空库启动: {} -> {}", base.toAbsolutePath(), ex.getMessage());
        }
        log.info("[ImageSearch] 已加载 {} 个账号的底库（根目录 {}）", loaded, base.toAbsolutePath());
    }

    /**
     * 关闭全部账号的向量存储。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, ImageSearchStore> e : accounts.entrySet()) {
            try {
                e.getValue().shutdown();
            } catch (Exception ex) {
                log.debug("[ImageSearch] 关闭账号 '{}' 底库失败: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    // ==================== 门面：与改造前保持同名同签名 ====================

    /**
     * 底库是否可用（是否配置了特征模型）。
     *
     * <p>特征模型是全局配置，与账号无关，故不需要当前账号上下文。</p>
     *
     * @return true 表示已配置特征模型
     */
    public boolean available() {
        return current().available();
    }

    /**
     * 当前配置的特征模型标识。
     *
     * @return 模型标识；未配置时为空串
     */
    public String currentModel() {
        return current().currentModel();
    }

    /**
     * 当前配置的向量存储实现名。
     *
     * @return 存储实现名
     */
    public String currentStorageEngine() {
        return current().currentStorageEngine();
    }

    /**
     * 抽取图像特征（用当前账号的抽取器缓存）。
     *
     * @param imageData 图像字节
     * @return 归一化特征向量
     */
    public float[] extract(byte[] imageData) {
        return current().extract(imageData);
    }

    /**
     * 入库一条记录到当前账号的底库。
     *
     * @param name      展示名
     * @param imageData 原图字节
     * @param vector    特征向量
     * @return 入库后的记录
     */
    public ImageSearchStore.StoredImage add(String name, byte[] imageData, float[] vector) {
        return current().add(name, imageData, vector);
    }

    /**
     * 在当前账号的底库中检索。
     *
     * @param query  查询向量
     * @param topK   返回条数上限
     * @param minSim 相似度下限
     * @return 命中列表
     */
    public List<ImageSearchStore.Match> search(float[] query, int topK, double minSim) {
        return current().search(query, topK, minSim);
    }

    /**
     * 分页列出当前账号的底库记录。
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字
     * @return 分页结果
     */
    public Map<String, Object> list(int page, int size, String keyword) {
        return current().list(page, size, keyword);
    }

    /**
     * 从当前账号的底库删除一条记录。
     *
     * @param id 记录标识
     * @return 是否删除成功
     */
    public boolean remove(long id) {
        return current().remove(id);
    }

    /**
     * 读取当前账号底库中某条记录的原图。
     *
     * @param id 记录标识
     * @return 原图字节；不存在返回 {@code null}
     */
    public byte[] readOriginal(long id) {
        return current().readOriginal(id);
    }

    /**
     * 读取当前账号底库中某条记录的缩略图。
     *
     * @param id 记录标识
     * @return 缩略图字节；不存在返回 {@code null}
     */
    public byte[] readThumbnail(long id) {
        return current().readThumbnail(id);
    }

    /**
     * 当前账号底库的记录数。
     *
     * @return 记录数
     */
    public int size() {
        return current().size();
    }

    /**
     * 当前账号底库的向量维度。
     *
     * @return 维度；未构建时为 0
     */
    public int dimension() {
        return current().dimension();
    }

    /**
     * 列出某账号底库中已存的分组名（DB 权威）。
     *
     * <p>口径来自 {@link ImageLibraryLinkService#listGroups(String)}：按账号查所有分组时，
     * 历史 {@code NULL} 行已在联动层归一化为 {@code default}，**不含常见兜底**——
     * 调用方（{@code /groups}）负责把默认组 {@code default} 补入选项，空库时
     * 用户也必须能选到它。</p>
     *
     * @param account 账号标识
     * @return 分组名集合（已去重；排序交给 {@code TreeSet} 调用方）
     */
    public java.util.Set<String> listGroups(String account) {
        return library.listGroups(account);
    }

    // ==================== 内部：账号解析 ====================

    /**
     * 以图搜图相关分组配置变更：使受影响账号的底库实例失效，下次访问按新配置重建。
     *
     * <p>图像相关配置（如特征模型）在底库实例内被缓存（{@code extractor}），
     * 不失效就永远是旧配置；这里直接移除实例，由 {@code storeOf} 懒建时按最新配置重取，
     * 并回读磁盘上已持久化的底库快照，避免设置变更让列表误以为数据丢失。</p>
     *
     * <p>用户级写入只失效**触发者自己的**账号：在某一用户身份下重建全部账号会把该用户的
     * 覆盖串到其他账号。</p>
     *
     * @param event 配置变更事件
     */
    @org.springframework.context.event.EventListener
    public void onSettingsChanged(com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent event) {
        if (event == null || !"image_search".equals(event.group())) {
            return;
        }
        if (event.userScoped()) {
            // 用户级变更只淘汰当前账号默认组的实例：以图搜图按账号默认组运行
            String account = AiUserContext.requireUserId();
            String group = AiLibraryGroups.DEFAULT;
            String key = account + "\0" + group;
            ImageSearchStore removed = accounts.remove(key);
            if (removed != null) {
                shutdownStore(removed, "图像设置变更（用户级）");
                log.info("[ImageSearch] 图像设置变更（用户级），已失效账号 '{}' 的底库实例", account);
            }
            return;
        }
        if (!accounts.isEmpty()) {
            log.info("[ImageSearch] 图像设置变更（全局），失效全部账号底库实例（账号数 {}）", accounts.size());
            for (ImageSearchStore store : accounts.values()) {
                shutdownStore(store, "图像设置变更（全局）");
            }
            accounts.clear();
        }
    }

    /**
     * 安全关闭一个已失效的账号底库实例。
     *
     * @param store  待关闭实例
     * @param reason 触发原因（仅用于日志）
     */
    private static void shutdownStore(ImageSearchStore store, String reason) {
        try {
            store.shutdown();
        } catch (Exception ex) {
            log.debug("[ImageSearch] {} 关闭旧底库实例失败: {}", reason, ex.getMessage());
        }
    }

    /**
     * 当前账号的底库实例（不存在则懒建）。
     *
     * @return 底库实例
     */
    private ImageSearchStore current() {
        return storeOf(AiUserContext.requireUserId(), AiLibraryGroups.DEFAULT);
    }

    /**
     * 取指定账号的底库实例，不存在时创建并加载。
     *
     * @param account 账号标识
     * @param group   分组名
     * @return 底库实例
     */
    private ImageSearchStore storeOf(String account, String group) {
        Path dir = accountDir(account, group).resolve(STORE_LEAF);
        return accounts.computeIfAbsent(account + "\0" + group, k -> create(account, group, dir, Files.isDirectory(dir)));
    }

    /**
     * 创建并加载一个账号的底库实例。
     *
     * @param account 账号标识
     * @param group   分组名
     * @param dir     底库目录
     * @param existing 该目录是否在磁盘上已存在（已存在才需要读快照，新建目录无需尝试）
     * @return 底库实例
     */
    private ImageSearchStore create(String account, String group, Path dir, boolean existing) {
        return new AccountImageSearch(account, group, dir, settingReader, library, keywordExtractor);
    }

    /**
     * 兼容旧版单目录布局：把 {@code <base>/image-search-store} 自动归位到
     * {@code <base>/admin/default/imagesearch}。
     *
     * <p><b>为什么需要。</b>目录改名后，任何仍按旧路径读写的构建（回滚旧包、
     * 或其它会话并发发上去的旧版本）会去找 {@code ./image-search-store}，找不到就
     * 按空库启动并新建一个空目录——用户看到的是「图都没了」。数据其实还在，
     * 只是布局对不上。与其留一个人为的运维动作，不如让代码自愈：发现遗留目录就地
     * 归位并打一条明确的日志，这样无论哪个版本先起来，数据都不会「看起来丢了」。</p>
     *
     * <p><b>安全阀</b>：① 只有目录里确实有 {@code index.json} 才认（避免把无关目录
     * 搬走）；② 目标已存在时**不覆盖**，只告警让人工判断；③ 整段吞异常，迁移失败
     * 绝不能把启动带崩。</p>
     *
     * @param base 底库根目录
     */
    private void adoptLegacyLayout(Path base) {
        Path legacy = base.resolve(LEGACY_DIR);
        Path target = base.resolve(LEGACY_OWNER).resolve(LEGACY_GROUP).resolve(STORE_LEAF);
        try {
            if (!Files.isDirectory(legacy) || !Files.isRegularFile(legacy.resolve("index.json"))) {
                return;
            }
            if (Files.exists(target)) {
                log.warn("[ImageSearch] 同时存在旧布局目录 {} 与新布局目录 {}，不自动迁移，请人工确认（本进程按新布局运行）",
                        legacy.toAbsolutePath(), target.toAbsolutePath());
                return;
            }
            Files.createDirectories(target.getParent());
            try {
                Files.move(legacy, target);
            } catch (IOException moveEx) {
                // 失败原因不一定是跨文件系统，但无论哪种都不能丢数据：
                // 退化为「复制一份、原目录保留」，宁可多占一份磁盘，也不冒删数据的风险。
                copyDirectory(legacy, target);
                log.warn("[ImageSearch] 旧布局目录移动失败（{}），已改为复制到 {}，原目录保留: {}",
                        moveEx.getMessage(), target.toAbsolutePath(), legacy.toAbsolutePath());
                return;
            }
            log.info("[ImageSearch] 检测到旧版单目录布局，已自动归位到账号目录: {} -> {}（旧版构建/回滚不会再看到空库）",
                    legacy.toAbsolutePath(), target.toAbsolutePath());
        } catch (Exception ex) {
            log.warn("[ImageSearch] 旧布局自动归位失败，按当前布局继续: {} -> {}",
                    legacy.toAbsolutePath(), ex.getMessage());
        }
    }

    /**
     * 递归复制目录（跨文件系统迁移时的退化路径）。
     *
     * @param from 源目录
     * @param to   目标目录
     * @throws IOException 复制失败
     */
    private static void copyDirectory(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * 底库根目录。
     *
     * @return 根目录路径
     */
    private Path baseDir() {
        return Paths.get(System.getProperty(BASE_DIR_PROPERTY, "."));
    }

    /**
     * 某账号的目录（{@code <base>/<账号>/<分组>}）。
     *
     * @param account 账号标识
     * @param group   分组名
     * @return 账号目录
     */
    private Path accountDir(String account, String group) {
        return baseDir().resolve(sanitize(account)).resolve(sanitize(group));
    }

    /**
     * 把账号标识收敛成安全的目录名。
     *
     * <p>账号标识直接来自用户名，必须防止它把路径带出根目录：去掉路径分隔符与
     * 上跳段，并挡掉 Windows 下的盘符/保留字符。空或全非法字符时退化为
     * {@code _unknown}，宁可归到一个显式的兜底目录，也不要让
     * {@code Paths.resolve} 把写入引到根目录之外。</p>
     *
     * @param name 原始标识
     * @return 可安全作为单级目录名的字符串
     */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "_unknown";
        }
        String s = name.trim()
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_");
        s = s.replace("..", "_");
        s = s.replaceAll("^[.\\s]+|[.\\s]+$", "");
        return s.isBlank() ? "_unknown" : s;
    }

    /**
     * 按（账号, 分组）隔离的底库实例。
     *
     * <p>结构与 {@link FaceStores.AccountFace} 对齐：账号标识、分组名、底库目录
     * 全部作为实例字段持有，构造时完成目录创建与快照加载。它不参与任何跨账号共享，
     * 每个（账号, 分组）组合拥有唯一的目录、唯一的向量索引和唯一的一份快照。</p>
     *
     * @since 4.0.0.45
     */
    private static final class AccountImageSearch extends ImageSearchStore {

        /**
         * @param account 账号标识
         * @param group   分组名
         * @param dir     底库目录
         * @param settingReader 设置读取器
         * @param library 底库元数据与向量的联动层
         * @param keywordExtractor 图片关键词提取器
         */
        AccountImageSearch(String account, String group, Path dir, GlobalSettingReader settingReader,
                           ImageLibraryLinkService library, ImageKeywordExtractor keywordExtractor) {
            super(account, group, dir, settingReader, library, keywordExtractor);
        }
    }
}
