package com.chua.starter.ai.support.service;

import com.chua.common.support.vector.VectorStorage;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.manage.service.OnnxModelDirsChangedEvent;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 人脸底库的**门面**：按当前登录账号把请求路由到该账号自己的人脸库。
 *
 * <p><b>与以图搜图保持同一套模型</b>：数据按账号隔离（各自一个索引文件 + 一份快照），
 * 隔离之后功能对所有登录用户开放，不再要求管理员。</p>
 *
 * <p><b>为什么需要这一层。</b>改造前人脸库是全局一份：所有账号共用一个 jvector
 * 默认相对路径的索引文件，且**没有任何元数据持久化** —— 实测「登记后重启，1:N 命中
 * 从 1 条变 0 条」，因为 jvector 自身的落盘不可靠。以图搜图之所以能跨重启保留，
 * 是因为它自己存 {@code index.json}（含向量），启动时清空索引再按快照重建。</p>
 *
 * <p>本类把这些做法搬到人脸侧：每个账号
 * {@code <根>/<账号>/face/} 下有两个文件 ——</p>
 * <ul>
 *   <li>{@code face-index.vectors}：jvector 的向量索引（可重建，不是权威）</li>
 *   <li>{@code index.json}：<b>只存 id + 向量</b>，启动据此重建索引；
 *       <b>档案的权威是 DB</b>（{@code sys_ai_face_library}），
 *       metadata 只保留在内存快照里做存量迁移/列表兜底，不重复落盘 ——
 *       DB 不存特征，向量重建只能靠 index.json。</li>
 * </ul>
 *
 * <p><b>模型不重复加载</b>：检测/特征/活体等 ONNX 模型由 {@link FaceClientManager}
 * 单例持有，各账号只是各自建一条管线、换一个向量存储；管线本身很轻。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceStores {

    /** 索引文件名（jvector 会在其后追加 .vectors） */
    private static final String INDEX_NAME = "face-index";
    /** 快照文件名 */
    private static final String SNAPSHOT_NAME = "index.json";
    /** 账号目录下的子目录名 */
    private static final String LEAF = "face";
    /** 登记图子目录名（账号目录下，与索引/快照同级） */
    private static final String IMAGE_DIR = "img";
    /** 底库根目录的系统属性名（缺省为进程工作目录） */
    private static final String BASE_DIR_PROPERTY = "face.store.dir";
    /** 设置分组名（用于过滤设置变更事件） */
    private static final String GROUP = "face";

    /** 账号 -> 该账号的人脸库 */
    private final Map<String, AccountFace> accounts = new ConcurrentHashMap<>();

    /**
     * 应用就绪闩。
     *
     * <p><b>为什么需要它</b>：模型根（{@code ModelRegistry.modelRootDir}）由
     * {@code OnnxExternalDirectoryService} 在启动期设置，而 Tomcat 在那之前就已经开始接请求。
     * 实测过这条竞态 —— 账号库先建（05:27:56）、模型根后设（05:27:58），
     * 于是 {@code arc-face} 按默认根解析成 {@code models/onnx/arc-face.onnx}，
     * 1:N 与特征提取全部失败。这里让账号库的创建**等就绪**再继续，
     * 并注意只能在创建那一处等（已有账号的后续请求不阻塞）。</p>
     */
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    /** 应用就绪闩的最长等待秒数（就绪事件不来时不能让请求永远挂着） */
    private static final long READY_WAIT_SECONDS = 120L;

    /** 模型与设置的单一来源（共享，不按账号复制） */
    private final FaceClientManager models;

    private final GlobalSettingReader settingReader;

    /**
     * 底库元数据与向量的联动层（DB 权威）。
     *
     * <p>{@link #snapshot}（index.json）仍保留，但角色已降级为<b>向量重建的来源</b>
     * 而不是唯一权威 —— 档案的权威改由 DB 承担，理由见该类注释。</p>
     */
    private final FaceLibraryLinkService library;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动时不做任何事：人脸库是懒建的，某个账号从没用过就不该凭空多出目录。
     */
    @PostConstruct
    public void init() {
        log.info("[Face] 人脸底库按账号隔离已启用（根目录 {}）", baseDir().toAbsolutePath());
    }

    /**
     * 关闭全部账号的向量存储。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, AccountFace> e : accounts.entrySet()) {
            try {
                e.getValue().close();
            } catch (Exception ex) {
                log.debug("[Face] 关闭账号 '{}' 人脸库失败: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    /**
     * 设置变更后重建**所有**账号的管线。
     *
     * <p>模型与维度是全局配置，改了要全账号生效；快照保存着向量，
     * 因此重建后逐个账号按快照重新灌入，数据不会丢。</p>
     *
     * @param event 设置变更事件
     */
    @EventListener
    public void onSettingsChanged(FaceSettingsChangedEvent event) {
        // 必须按分组过滤：该事件对**所有**分组都会发布，不过滤就会「改任何配置都重建人脸库」，
        // 既浪费（每个账号都要重灌向量）也可能打断正在进行的识别。
        // 与 FaceClientManager#onFaceSettingsChanged 保持同一判据。
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        // 用户级写入只重建**触发者自己的**账号库：账号库按账号隔离，在某一用户的身份下
        // 重建全部账号会把该用户的覆盖串到其他账号，故定向重建；
        // 未建过库的账号无需处理，下次按需构建即用最新配置。
        if (event.userScoped()) {
            reloadAccount(event.userId(), "人脸设置变更（用户级）");
            return;
        }
        reloadAll("人脸设置变更");
    }

    /**
     * 重建指定账号的人脸库（用户级设置变更专用）。
     *
     * @param userId 账号标识（为空表示无归属，此时不重建，避免按错误身份重建）
     * @param reason 触发原因（只用于日志）
     */
    private void reloadAccount(String userId, String reason) {
        if (userId == null || userId.isBlank()) {
            log.debug("[Face] {} 但事件未携带账号，跳过重建", reason);
            return;
        }
        AccountFace account = accounts.get(userId);
        if (account == null) {
            return;
        }
        try {
            account.reload();
            log.info("[Face] {}，重建账号 '{}' 的人脸库", reason, userId);
        } catch (Exception ex) {
            log.warn("[Face] 账号 '{}' 人脸库重建失败: {}", userId, ex.getMessage());
        }
    }

    /**
     * 应用就绪：放行账号库创建，并重建启动期可能已经用错模型根建好的账号库。
     *
     * <p>放行与重建缺一不可：只放行则启动期建坏的那份会一直留着；
     * 只重建则后续请求仍可能抢在模型根设置之前建库。</p>
     *
     * @param event 就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        readyLatch.countDown();
        reloadAll("应用就绪");
    }

    /**
     * ONNX 外部目录变更：模型根可能刚被改到正确位置，重建全部账号。
     *
     * @param event 目录变更事件
     */
    @EventListener
    public void onOnnxDirsChanged(OnnxModelDirsChangedEvent event) {
        reloadAll("ONNX 外部目录变更（模型根=" + event.modelRoot() + "）");
    }

    /**
     * 重建全部账号的人脸库。
     *
     * @param reason 触发原因（只用于日志）
     */
    private void reloadAll(String reason) {
        if (accounts.isEmpty()) {
            return;
        }
        log.info("[Face] {}，重建全部账号的人脸库（账号数 {}）", reason, accounts.size());
        for (Map.Entry<String, AccountFace> e : accounts.entrySet()) {
            try {
                e.getValue().reload();
            } catch (Exception ex) {
                log.warn("[Face] 账号 '{}' 人脸库重建失败: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    // ==================== 对外能力（按当前账号） ====================

    /**
     * 当前账号的人脸管线。
     *
     * @return 人脸管线；检测模型未配置时返回 {@code null}
     */
    public FacePipeline currentPipeline() {
        return st().pipeline();
    }

    /**
     * 当前账号的向量存储。
     *
     * @return 向量存储；未就绪时返回 {@code null}
     */
    public VectorStorage currentVectorStorage() {
        return st().storage();
    }

    /**
     * 按**向量**登记（快照持久化的正路）。
     *
     * @param id       人脸标识
     * @param feature  特征向量
     * @param metadata 元数据
     * @return 是否登记成功
     */
    public boolean enroll(String id, float[] feature, Map<String, Object> metadata) {
        AccountFace st = st();
        FacePipeline pipeline = st.pipeline();
        if (pipeline == null) {
            return false;
        }
        boolean ok = pipeline.enroll(id, feature, metadata == null ? Map.of() : metadata, id);
        if (!ok) {
            return false;
        }
        Entry prev = st.snapshot.get(id);
        st.put(id, feature, metadata);
        try {
            st.persist();
        } catch (RuntimeException ex) {
            // 快照没落盘 = 这次登记等于没发生。必须把内存态和向量一起撤掉：
            // 只回滚内存的话，jvector 里会多出一条没有档案指向的向量，
            // identify 仍能命中它，用户却查不到这条人脸 —— 比登记失败更难排查。
            restoreSnapshot(st, id, prev);
            deleteVectorQuietly(st, id);
            throw ex;
        }
        // DB 权威：元数据写库（内部已处理「向量失败则回滚 DB 行」）。
        // 放在快照之后 —— 快照是向量重建的来源，它没了，重启后向量也补不回来；
        // DB 写失败由联动层回滚，不会留下半截状态。
        library.insert(st.account, id, strOf(metadata, "name"), metadata, null, null, null);
        return true;
    }

    /**
     * 从向量索引中删掉一条。
     *
     * <p>此前 {@link #remove(String)} 写成 {@code if (storage == null) return false}，
     * 于是「进程刚启动、本账号还没做过任何增删查」时删除会静默跳过向量清理 ——
     * 快照里没这条了，jvector 里却留着，继续被 {@link #identify} 命中（幽灵向量）。
     * 以图搜图侧早就修过这个坑（见 {@code ImageSearchStore#remove} 的注释），
     * 人脸侧当时照抄了旧版本。删除是唯一能制造幽灵向量的操作，这里必须尽力完成：
     * 存储不可用时就用账号自己的索引路径现建一个，删完立刻关掉。</p>
     *
     * @param st 账号库
     * @param id 人脸标识
     */
    private void deleteVectorQuietly(AccountFace st, String id) {
        VectorStorage storage = st.storage();
        boolean temporary = false;
        if (storage == null) {
            try {
                storage = models.buildStorageFor(st.dir.resolve(INDEX_NAME));
                temporary = true;
            } catch (Exception ex) {
                log.warn("[Face] 删除前重建向量存储失败，向量可能在索引中残留: id={} -> {}", id, ex.getMessage());
                return;
            }
        }
        try {
            if (!storage.remove(id)) {
                // 返回 false = 索引里本就没有（如已随重建被清空），属正常，
                // 但要与「删除失败」区分开，避免排查时误判
                log.debug("[Face] 向量索引中无此条目（可安全忽略）: id={}", id);
            }
        } catch (Exception ex) {
            log.warn("[Face] 向量存储删除失败: id={} -> {}", id, ex.getMessage());
        } finally {
            if (temporary) {
                try {
                    storage.close();
                } catch (Exception ignored) {
                    // 临时句柄关不掉不影响正确性：真正的存储仍由 AccountFace 持有
                }
            }
        }
    }

    /**
     * 记录一条到内存快照（不落盘）。
     *
     * @param st    账号库
     * @param id    标识
     * @param entry 条目；为 {@code null} 表示删除
     */
    private static void restoreSnapshot(AccountFace st, String id, Entry entry) {
        if (entry == null) {
            st.snapshot.remove(id);
        } else {
            st.snapshot.put(id, entry);
        }
    }

    /**
     * 从图片登记（检测 → 对齐 → 特征 → 入库），并写入快照。
     *
     * <p><b>只接受单人脸。</b>底层 {@code extractFeatureWithMeta} 只会取面积最大的
     * 一张脸出特征，过去多脸合影被静默截成「只登记了其中一个人」，用户却收到
     * 「登记成功」——底库里少了人、当事人毫不知情，等到 1:N 检索认不出人才会发现。
     * 这里把检出的总脸数一并带出来，多于 1 张直接拒绝，让用户裁剪后重试。</p>
     *
     * @param id       人脸标识
     * @param imageData 图片字节
     * @param metadata 元数据
     * @return 是否登记成功
     * @throws MultiFaceEnrollException 图片中检出多于 1 张人脸
     */
    public boolean enrollFromImage(String id, byte[] imageData, Map<String, Object> metadata) {
        AccountFace st = st();
        FacePipeline pipeline = st.pipeline();
        if (pipeline == null) {
            return false;
        }
        // 先取特征再按向量入库：这样才拿得到要写进快照的向量。
        // 与 FacePipeline.enrollPipeline 内部走的是同一条抽取路径。
        var result = pipeline.extractFeatureWithMeta(imageData);
        if (result == null || result.feature() == null || result.feature().length == 0) {
            return false;
        }
        if (result.faceCount() > 1) {
            throw new MultiFaceEnrollException(result.faceCount());
        }
        boolean ok = pipeline.enroll(id, result.feature(), metadata == null ? Map.of() : metadata, id);
        if (!ok) {
            return false;
        }
        Entry prev = st.snapshot.get(id);
        st.put(id, result.feature(), metadata);
        try {
            st.persist();
        } catch (RuntimeException ex) {
            restoreSnapshot(st, id, prev);
            deleteVectorQuietly(st, id);
            throw ex;
        }
        // 快照已落盘，才轮到写图片文件。反过来做的话，图片写失败会留下一条
        // 「有档案但没图」的记录，底库统计卡的「有图/无图」从此对不上。
        //
        // 一并落存登记时用的裁剪脸：底库要「看图 + 画特征点」，
        // 而缩略图此前只在登记它的浏览器里，换浏览器就没有图。
        saveFaceImage(id, FaceImageKind.CROP, result.faceImage());
        // 同时留一份原图。特征点必须画在原图上才有意义（见 originalImage 的说明），
        // 裁剪脸上只有一张脸，画出来的点永远是「对齐的五点」，看不出任何问题。
        saveFaceImage(id, FaceImageKind.ORIGINAL, imageData);
        // DB 权威：元数据写库。缩略图一并入库，让换浏览器/换机器时列表仍有图 ——
        // 底库图片只在本机磁盘上，DB 里没有副本就等于「换个环境全库无图」。
        // DB 写失败要回滚快照与向量，不能让三处状态分叉。
        if (!library.insert(st.account, id, strOf(metadata, "name"), metadata,
                base64Of(result.faceImage()), null, null)) {
            restoreSnapshot(st, id, prev);
            deleteVectorQuietly(st, id);
            deleteFaceImage(id);
            try {
                st.persist();
            } catch (RuntimeException ex) {
                log.error("[Face] 账号 '{}' 登记失败后回滚快照也失败: id={} -> {}",
                        st.account, id, ex.getMessage());
            }
            return false;
        }
        return true;
    }

    /**
     * 字节数组转 base64（缩略图入库用）。
     *
     * @param data 字节；为空返回 {@code null}
     * @return base64 字符串；入参为空返回 {@code null}
     */
    private static String base64Of(byte[] data) {
        return data == null || data.length == 0 ? null : Base64.getEncoder().encodeToString(data);
    }

    /**
     * 取档案里的字符串字段（展示名落 name 列用）。
     *
     * @param metadata 档案；可为空
     * @param key      键
     * @return 字符串值；不存在返回 {@code null}
     */
    private static String strOf(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object v = metadata.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 读一条人脸登记时落存的**裁剪图**。
     *
     * @param id 人脸标识
     * @return PNG 字节；未落存（改造前的老数据 / 落存失败）时返回 空
     */
    public byte[] faceImage(String id) {
        return readFaceImage(id, FaceImageKind.CROP);
    }

    /**
     * 读一条人脸登记时落存的**原图**。
     *
     * <p>特征点对话框必须画在这张上：关键点是在原图坐标系里由检测框 + 关键点模型
     * 算出来的，画在裁剪脸上只会得到「已经对齐好的五点」，无论模型偏没偏都看不出问题，
     * 等于这个功能没在检查任何东西。改造前只存了裁剪脸，所以对话框一直在画裁剪图。</p>
     *
     * <p>改造前登记的老数据没有原图，返回 {@code null}；前端此时回退到裁剪图。</p>
     *
     * @param id 人脸标识
     * @return 原图字节；未落存时返回 {@code null}
     */
    public byte[] originalImage(String id) {
        return readFaceImage(id, FaceImageKind.ORIGINAL);
    }

    /**
     * 读取指定种类的登记图。
     *
     * @param id   人脸标识
     * @param kind 图种类
     * @return 图片字节；未落存时返回 {@code null}
     */
    private byte[] readFaceImage(String id, FaceImageKind kind) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            Path file = faceImagePath(id, kind);
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (Exception e) {
            log.warn("[Face] 读登记图失败: id={} kind={} -> {}", id, kind, e.getMessage());
            return null;
        }
    }

    /**
     * 落存登记时的人脸图（best-effort）。
     *
     * <p>裁剪脸用于列表缩略图（视野一致、体积小），原图用于特征点标注。
     * 两个都留，才能兼顾「一眼扫完整个底库」和「在真实照片上核对五官位置」。</p>
     *
     * <p>失败只记 WARN，绝不影响登记结果 —— 图属于增强展示，向量与档案才是主体。</p>
     *
     * @param id   人脸标识
     * @param kind 图种类
     * @param data 图片字节（可能为 {@code null}）
     */
    private void saveFaceImage(String id, FaceImageKind kind, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        try {
            Path file = faceImagePath(id, kind);
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (Exception e) {
            log.warn("[Face] 登记图落存失败（不影响登记）: id={} kind={} -> {}", id, kind, e.getMessage());
        }
    }

    /**
     * 删除一条人脸时同步删掉它的登记图（best-effort）。
     *
     * @param id 人脸标识
     */
    private void deleteFaceImage(String id) {
        for (FaceImageKind kind : FaceImageKind.values()) {
            try {
                Files.deleteIfExists(faceImagePath(id, kind));
            } catch (Exception e) {
                log.debug("[Face] 删除登记图失败: id={} kind={} -> {}", id, kind, e.getMessage());
            }
        }
    }

    /**
     * 登记图的种类（同一 {@code id} 下的两个并存文件）。
     *
     * <p>裁剪脸统一落成 {@code .png}（人脸图在管线里已经转过码），原图保留上传时的
     * 原始字节——它只是给人看的，没必要再解一遍编码。</p>
     */
    private enum FaceImageKind {
        /** 裁剪后的人脸图，列表缩略图用它 */
        CROP(".png"),
        /** 登记时的原始照片，特征点标注用它 */
        ORIGINAL(".orig");

        private final String suffix;

        FaceImageKind(String suffix) {
            this.suffix = suffix;
        }

        /**
         * 文件名后缀。
         *
         * @return 含点号的后缀
         */
        String suffix() {
            return suffix;
        }
    }

    /**
     * 某条人脸登记图的路径（{@code <账号>/face/img/<id><后缀>}）。
     *
     * @param id   人脸标识
     * @param kind 图种类
     * @return 文件路径
     */
    private Path faceImagePath(String id, FaceImageKind kind) {
        return st().dir.resolve(IMAGE_DIR).resolve(sanitize(id) + kind.suffix());
    }

    /**
     * 1:N 识别（取最大人脸）。
     *
     * @param imageData 图片字节
     * @return 识别结果；管线未就绪时返回 {@code null}
     */
    public FaceIdentifyHit identify(byte[] imageData) {
        FacePipeline pipeline = st().pipeline();
        return pipeline == null ? null : pipeline.identifyPipelineLargest(imageData);
    }

    /**
     * 删除一条人脸（索引 + 快照同步）。
     *
     * @param id 人脸标识
     * @return 是否删除成功
     */
    public boolean remove(String id) {
        AccountFace st = st();
        Entry prev = st.snapshot.remove(id);
        if (prev == null) {
            return false;
        }
        try {
            st.persist();
        } catch (RuntimeException ex) {
            // 快照没删掉 = 这次删除等于没发生。图、向量、DB 行都还没动，内存态还原即可
            restoreSnapshot(st, id, prev);
            throw ex;
        }
        // DB 行：DB 化之前登记的存量人脸本来就没有 DB 行，那也算删除成功，
        // 只有 DB 真的出错才算失败 —— 此时必须把快照也还原，否则三处状态就此分叉：
        // 列表里已看不到这个人，但 DB 里那行还在，日后按 DB 统计又会把他算回来。
        if (!library.deleteMeta(st.account, id)) {
            restoreSnapshot(st, id, prev);
            try {
                st.persist();
            } catch (RuntimeException ex) {
                log.error("[Face] 账号 '{}' 删除失败后回滚快照也失败，人脸可能已从列表消失: id={} -> {}",
                        st.account, id, ex.getMessage());
            }
            return false;
        }
        // 档案本体已删干净，图与向量才是「附属品」：删失败只影响可看性/检索，
        // 且向量残留会在下次重建索引时自动清掉，不阻断本次删除。
        deleteFaceImage(id);
        deleteVectorQuietly(st, id);
        return true;
    }

    /**
     * 当前账号已登记的人脸标识列表。
     *
     * @return 标识列表（按登记顺序）
     */
    public List<String> ids() {
        return new ArrayList<>(st().snapshot.keySet());
    }

    /**
     * 当前账号已登记的人脸条目（含元数据，不含向量）。
     *
     * <p>档案数据来自数据库 `sys_ai_face_library`；只有账号目录里还没有 DB 行
     * 时才回落到快照（迁移兜底），避免老数据在补建前从列表消失。</p>
     *
     * @return 条目列表
     */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = library.listMine(st().account);
        if (rows.isEmpty() && !st().snapshot.isEmpty()) {
            rows = snapshotRows(st());
        }
        for (Map<String, Object> row : rows) {
            String id = String.valueOf(row.get("id"));
            row.put("hasImage", Files.exists(faceImagePath(id, FaceImageKind.CROP)));
            // 原图是否存在：特征点对话框画在原图上，老数据没有原图时要回退到裁剪图，
            // 前端据此决定提示「已按裁剪脸显示」还是直接画原图
            row.put("hasOriginalImage", Files.exists(faceImagePath(id, FaceImageKind.ORIGINAL)));
        }
        return rows;
    }

    /**
     * 快照行转列表行（仅在数据库还没有当前账号档案时的迁移兜底）。
     *
     * @param st 账号库
     * @return 条目列表
     */
    private static List<Map<String, Object>> snapshotRows(AccountFace st) {
        List<Map<String, Object>> out = new ArrayList<>(st.snapshot.size());
        for (Map.Entry<String, Entry> e : st.snapshot.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getKey());
            row.put("metadata", e.getValue().metadata() == null ? Map.of() : e.getValue().metadata());
            out.add(row);
        }
        return out;
    }

    /**
     * 把 {@code ? extends Map} 收敛成 {@code String -> Object}，供列表读取 DB 反序列化的档案。
     *
     * @param raw 原始 map
     * @return 字符串键 map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMeta(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    /**
     * 分页 + 关键词 + 状态筛选的条目列表（万级底库与表格视图的数据源）。
     *
     * <p><b>档案以数据库为准</b>：先取 {@code sys_ai_face_library} 当前账号行，
     * 再按档案元数据过滤/排序；`index.json` 只承担向量重建，不再参与列表口径。
     * 真正的开销在 {@link Files#exists} —— 本方法**单遍**顺带把全库统计也算了
     * （旧 {@code FaceController.list()} 把 {@code list()} 调了两遍，等于两遍 stat + 两遍遍历）。</p>
     *
     * <p><b>筛选口径与前端一致：</b>关键词对 id 与元数据的字符串字段（姓名/标签/身份证/备注等）
     * 做大小写不敏感的包含匹配；状态 {@code thumb}/{@code noThumb} 按服务端登记图是否存在分。</p>
     *
     * @param keyword   关键词（可空）
     * @param status    状态筛选：{@code thumb}=有图、{@code noThumb}=无图，其余/空=全部
     * @param sortField 排序字段（可空）：{@code id} / {@code hasImage} / 或元数据键
     *                  （{@code name}/{@code age}/{@code idCard} 等）；空 = 登记顺序
     * @param sortOrder 排序方向：{@code asc} / {@code desc}；空/其余 = asc
     * @param page      页码（从 1 开始）；&le;0 表示不切片
     * @param size      每页条数；&le;0 表示不切片
     * @return {@code items}（本页条目）+ {@code total}（命中总数）+ {@code stats}（全库统计，不受筛选影响）
     */
    public Map<String, Object> listPage(String keyword, String status, String sortField,
                                        String sortOrder, int page, int size) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        boolean onlyThumb = "thumb".equals(status);
        boolean onlyNoThumb = "noThumb".equals(status);
        List<Map<String, Object>> rows = list();
        List<Map<String, Object>> matched = new ArrayList<>(rows.size());
        int statTotal = rows.size();
        int statWithImage = 0;
        int statWithIdCard = 0;
        for (Map<String, Object> row : rows) {
            String id = String.valueOf(row.get("id"));
            boolean hasImage = Boolean.TRUE.equals(row.get("hasImage"));
            boolean hasOriginal = Boolean.TRUE.equals(row.get("hasOriginalImage"));
            Map<String, Object> metadata = row.get("metadata") instanceof Map<?, ?> m
                    ? castMeta(m) : Map.of();
            if (hasImage) {
                statWithImage++;
            }
            if (metadata.get("idCard") instanceof String idCard && !idCard.isBlank()) {
                statWithIdCard++;
            }
            if (onlyThumb && !hasImage) {
                continue;
            }
            if (onlyNoThumb && hasImage) {
                continue;
            }
            if (!kw.isEmpty() && !matchesKeyword(id, metadata, kw)) {
                continue;
            }
            matched.add(row);
        }
        sortRows(matched, sortField, sortOrder);
        // 命中总数必须在切片前取：下面分页会把 matched 重赋值为本页子列表，
        // 若切片后再取 matched.size()，total 恒等于本页条数（size），
        // 前端「total > pageSize」永远为 false，翻页条不渲染、翻页不可达。
        int totalMatched = matched.size();
        List<Map<String, Object>> items = matched;
        if (page > 0 && size > 0) {
            long fromL = (long) (page - 1) * size;
            int from = (int) Math.min(fromL, matched.size());
            int to = (int) Math.min(fromL + size, matched.size());
            matched = new ArrayList<>(matched.subList(from, to));
            items = matched;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", statTotal);
        stats.put("withImage", statWithImage);
        stats.put("withIdCard", statWithIdCard);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", totalMatched);
        out.put("stats", stats);
        return out;
    }

    /**
     * 关键词是否命中该条：id 与元数据里的字符串字段做大小写不敏感的包含匹配。
     *
     * @param id       人脸标识
     * @param metadata 登记元数据
     * @param kw       已转小写的关键词（非空）
     * @return 是否命中
     */
    private static boolean matchesKeyword(String id, Map<String, Object> metadata, String kw) {
        if (id != null && id.toLowerCase(Locale.ROOT).contains(kw)) {
            return true;
        }
        for (Object v : metadata.values()) {
            if (v instanceof String s && s.toLowerCase(Locale.ROOT).contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按表格列排序（服务端排序：分页下只排当前页会给出错误的次序感）。
     *
     * <p>字段白名单：{@code id} 走标识、{@code hasImage} 走布尔、其余按元数据键取值。
     * 比较统一「null 后置、数字按数值、其余按字符串忽略大小写」，
     * 非白名单字段直接不动（保持登记顺序，而不是静默排成全 null 的假有序）。</p>
     *
     * @param rows      待排序的命中行（原地）
     * @param sortField 排序字段；空 = 不排序
     * @param sortOrder {@code desc} = 降序，其余 = 升序
     */
    private static void sortRows(List<Map<String, Object>> rows, String sortField, String sortOrder) {
        if (sortField == null || sortField.isBlank()) {
            return;
        }
        String field = sortField.trim();
        boolean desc = "desc".equalsIgnoreCase(sortOrder);
        Comparator<Map<String, Object>> cmp = (a, b) -> {
            Object va;
            Object vb;
            if ("id".equals(field)) {
                va = a.get("id");
                vb = b.get("id");
            } else if ("hasImage".equals(field)) {
                va = a.get("hasImage");
                vb = b.get("hasImage");
            } else {
                va = metadataValue(a, field);
                vb = metadataValue(b, field);
            }
            int r = compareValues(va, vb);
            return desc ? -r : r;
        };
        rows.sort(cmp);
    }

    /**
     * 取行的元数据值。
     *
     * @param row   行
     * @param field 元数据键
     * @return 值（缺失为 {@code null}）
     */
    private static Object metadataValue(Map<String, Object> row, String field) {
        Object meta = row.get("metadata");
        return meta instanceof Map<?, ?> m ? m.get(field) : null;
    }

    /**
     * 通用比较：null 后置；两个数字按数值；两个布尔 false &lt; true；其余按字符串。
     *
     * @param a 左值
     * @param b 右值
     * @return 比较结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (a instanceof Boolean ba && b instanceof Boolean bb) {
            return ba.compareTo(bb);
        }
        if (a.getClass() == b.getClass() && a instanceof Comparable ca) {
            return ca.compareTo(b);
        }
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    /**
     * 更新一条人脸的**元数据**（向量与快照里的特征都不动）。
     *
     * <p>为什么要有独立入口：{@code enroll} 需要图片或向量，而「只改姓名/年龄/身份证」
     * 这类操作不该要求重新上传照片、也不该重跑检测 —— 重跑一次既慢，又可能因原照片
     * 质量被判失败，把一次纯文本修改变成一次可能失败的登记。</p>
     *
     * <p>实现要点：档案权威在 DB，向量重建靠快照，两处都要写（任一失败即回滚）。</p>
     *
     * <p><b>关于向量索引</b>：实测索引**不回传档案** ——
     * {@code identify} 的 hit 是 {@code {id, score, feature}}（没有 metadata 字段），
     * {@code search} 的 hit 里那个 {@code metadata} 只是 {@code {score}}，不是登记时写入的档案。
     * 所以档案不由索引承载，调用方（前端）是拿 hit 的 id 回列表/登记簿取档案。
     * 下面重推一次向量**只为保证索引与档案一致**（防御索引与档案分叉），
     * 不是为了让识别回传新档案 —— 后者本来就不由索引负责。</p>
     *
     * @param id       人脸标识
     * @param metadata 新元数据（整体替换；{@code null} 视为空档案）
     * @return 是否更新成功（条目不存在返回 false）
     */
    public boolean updateMetadata(String id, Map<String, Object> metadata) {
        AccountFace st = st();
        Entry old = st.snapshot.get(id);
        if (old == null) {
            return false;
        }
        // 复用 put：它会把 feature 拷一份、metadata 拷成新的可变更
        st.put(id, old.feature(), metadata);
        try {
            st.persist();
        } catch (RuntimeException ex) {
            // 写盘失败 = 这次修改没生效，还原成旧档案（向量索引随后才动，不受影响）
            restoreSnapshot(st, id, old);
            throw ex;
        }
        // DB 权威：同步元数据行。DB 失败要把快照一并还原 ——
        // 只改一半的话，界面读到的是新档案、DB 里是旧档案，日后按 DB 导出/统计又变回旧值。
        //
        // 注意这里必须连异常一起兜住：updateMeta 内部的 find() 走 MyBatis-Plus，
        // 底层 SQL 一旦出错（如保留字被当成列别名）抛的是 RuntimeException 而不是返回 false，
        // 只判返回值会让快照保留新档案、DB 保留旧档案 —— 界面与库从此分叉且无人回滚。
        boolean dbOk;
        try {
            dbOk = library.updateMeta(st.account, id, strOf(metadata, "name"), metadata, null);
        } catch (RuntimeException ex) {
            log.error("[Face] 账号 '{}' 元数据写入 DB 抛异常，回滚快照: id={} -> {}",
                    st.account, id, ex.getMessage());
            dbOk = false;
        }
        if (!dbOk) {
            restoreSnapshot(st, id, old);
            try {
                st.persist();
            } catch (RuntimeException ex) {
                log.error("[Face] 账号 '{}' 更新失败后回滚快照也失败，档案可能已不一致: id={} -> {}",
                        st.account, id, ex.getMessage());
            }
            return false;
        }
        // 防御索引与快照分叉：用同一特征重推一次（档案本身不随索引回传，见上）
        FacePipeline pipeline = st.pipeline();
        if (pipeline != null && old.feature() != null && old.feature().length > 0) {
            try {
                pipeline.enroll(id, old.feature(), metadata == null ? Map.of() : metadata, id);
            } catch (Exception ex) {
                log.warn("[Face] 账号 '{}' 元数据更新后同步向量索引失败: id={} -> {}",
                        st.account, id, ex.getMessage());
            }
        }
        return true;
    }

    /**
     * 当前账号已登记的人脸数量。
     *
     * @return 数量
     */
    public int size() {
        return st().snapshot.size();
    }

    /**
     * 人脸能力是否就绪。
     *
     * @return true 表示检测模型已配置
     */
    public boolean available() {
        return st().pipeline() != null;
    }

    // ==================== 内部：账号解析与快照 ====================

    /**
     * 当前账号的人脸库（不存在则懒建）。
     *
     * @return 账号人脸库
     */
    private AccountFace st() {
        String account = AiUserContext.requireUserId();
        return accounts.computeIfAbsent(account, this::create);
    }

    /**
     * 创建一个账号的人脸库并加载快照。
     *
     * @param account 账号标识
     * @return 账号人脸库
     */
    private AccountFace create(String account) {
        // 等应用就绪再建：模型根由 OnnxExternalDirectoryService 在启动期设置，
        // 抢在它之前建库会按默认根解析模型而失败（实测过的竞态）。有界等待，
        // 就绪事件不来也不会把请求永久挂住。
        try {
            if (!readyLatch.await(READY_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[Face] 等待应用就绪超时（{}s），账号 '{}' 的人脸库按当前模型根构建",
                        READY_WAIT_SECONDS, account);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        AccountFace st = new AccountFace(account, accountDir(account));
        st.reload();
        return st;
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
     * 某账号的人脸库目录（{@code <根>/<账号>/face}）。
     *
     * @param account 账号标识
     * @return 目录
     */
    private Path accountDir(String account) {
        return baseDir().resolve(sanitize(account)).resolve(LEAF);
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
     * 一个人脸快照条目。
     *
     * @param feature  特征向量
     * @param metadata 元数据
     */
    private record Entry(float[] feature, Map<String, Object> metadata) {
    }

    /**
     * 单个账号的人脸库：管线 + 向量存储 + 快照。
     */
    private final class AccountFace {

        /** 账号标识 */
        private final String account;
        /** 本账号目录 */
        private final Path dir;
        /** 快照：id -> 条目。向量重建的来源与运行时缓存（档案权威在 DB） */
        private final Map<String, Entry> snapshot = new LinkedHashMap<>();
        /** 向量存储 */
        private volatile VectorStorage storage;
        /** 人脸管线（按当前设置构建） */
        private volatile FacePipeline pipeline;

        AccountFace(String account, Path dir) {
            this.account = account;
            this.dir = dir;
        }

        FacePipeline pipeline() {
            return pipeline;
        }

        VectorStorage storage() {
            return storage;
        }

        /**
         * 重建：读快照 → 建存储与管线 → **先清空索引** → 按快照重新灌入向量。
         *
         * <p>「先清空」是必须的：jvector 有自己的侧录文件（{@code <indexPath>.vectors}），
         * 里面留着历史 id。若不清空，删除过的条目会以「幽灵向量」形式继续参与检索 ——
         * 快照里已经没有它，{@code identify} 却还能命中。以图搜图侧早就定了这条口径
         * （向量只存在快照里，索引完全可由它重建；档案权威在 DB）。</p>
         */
        synchronized void reload() {
            // 重建必须绑定「该账号自己」的配置上下文。
            // 用户覆盖优先的取值走 AiUserContext（见 GlobalSettingReader#getStr），
            // 而重建线程上往往没有该账号的身份：由用户 A 触发却要重建**所有**账号，
            // 或启动期（ApplicationReadyEvent）根本没有请求上下文。
            // 不绑定的话，各账号的管线会全部按触发者（或全局）的模型构建 ——
            // 那比「改了不生效」更糟：一改就把别人的模型串掉。
            AiUserContext.runWith(new UserResume().setUserId(account), this::doReload);
        }

        /**
         * 重建主体（此时的线程身份已绑定为 {@link #account}）。
         */
        private void doReload() {
            close();
            try {
                Files.createDirectories(dir);
            } catch (Exception ex) {
                log.warn("[Face] 账号 '{}' 目录创建失败: {} -> {}", account, dir, ex.getMessage());
            }
            loadSnapshot();
            // 存量补建放在这里、且在「模型未配置」早返回**之前**：
            // DB 化之前登记的人脸只有 index.json 里的一行记录。若把这一步放到
            // pipeline 就绪之后，恰好没配人脸模型的账号将永远补不上 DB 行 ——
            // 而这类账号恰恰最可能是「历史登记过、后来不用了」，最需要档案留底。
            // 补建全部成功后才把磁盘快照规范为「只留向量」；任一补建失败则保留
            // 旧 metadata，避免把还没进 DB 的基础数据提前抹掉。
            if (backfillToDb()) {
                try {
                    persist();
                } catch (RuntimeException ex) {
                    log.warn("[Face] 账号 '{}' 快照规范化为向量专用后写盘失败: {} -> {}",
                            account, dir.resolve(SNAPSHOT_NAME), ex.getMessage());
                }
            }
            storage = models.buildStorageFor(dir.resolve(INDEX_NAME));
            pipeline = models.buildPipelineWith(storage);
            if (pipeline == null) {
                log.info("[Face] 账号 '{}' 人脸检测模型未配置，人脸能力未启用", account);
                return;
            }
            try {
                storage.clear();
            } catch (Exception ex) {
                log.debug("[Face] 清空向量索引失败（继续按快照灌入）: {}", ex.getMessage());
            }
            int restored = 0;
            for (Map.Entry<String, Entry> e : snapshot.entrySet()) {
                try {
                    if (pipeline.enroll(e.getKey(), e.getValue().feature(),
                            e.getValue().metadata() == null ? Map.of() : e.getValue().metadata(), e.getKey())) {
                        restored++;
                    } else {
                        log.warn("[Face] 账号 '{}' 快照条目灌入失败: id={}", account, e.getKey());
                    }
                } catch (Exception ex) {
                    log.warn("[Face] 账号 '{}' 快照条目灌入异常: id={} -> {}", account, e.getKey(), ex.getMessage());
                }
            }
            log.info("[Face] 账号 '{}' 人脸库就绪: {}/{} 条（{}）",
                    account, restored, snapshot.size(), dir.resolve(SNAPSHOT_NAME));
        }

        /**
         * 把快照里存在、DB 里没有的条目补建入库（DB 化前的存量迁移）。
         *
         * <p>幂等：只补 DB 缺的那部分，已有行<b>不覆盖</b>。这不是偷懒 ——
         * DB 里可能有用户在界面上改过的新档案（姓名、备注），拿 index.json 里的
         * 旧副本盖回去等于把用户刚填的资料抹掉。补建的只是「从没进过 DB 的」那批。</p>
         *
         * <p>不调 {@code persist}：本方法不改变快照，只读。</p>
         *
         * @return 是否所有待补条目都已成功入库；任一失败返回 false
         */
        private boolean backfillToDb() {
            if (snapshot.isEmpty()) {
                return true;
            }
            java.util.Set<String> existing = library.listExistingVectorIds(account);
            int created = 0;
            boolean allOk = true;
            for (Map.Entry<String, Entry> e : snapshot.entrySet()) {
                if (existing.contains(e.getKey())) {
                    continue;
                }
                if (library.insert(account, e.getKey(), strOf(e.getValue().metadata(), "name"),
                        e.getValue().metadata(), null, null, null)) {
                    created++;
                } else {
                    allOk = false;
                    log.warn("[Face] 账号 '{}' 存量档案补建失败: id={}",
                            account, e.getKey());
                }
            }
            if (created > 0) {
                log.info("[Face] 账号 '{}' 存量档案补建入库: {} 条（底库共 {} 条）",
                        account, created, snapshot.size());
            }
            return allOk;
        }

        /**
         * 记录一条到快照（不落盘，调用方随后 persist）。
         *
         * @param id       标识
         * @param feature  特征
         * @param metadata 元数据
         */
        void put(String id, float[] feature, Map<String, Object> metadata) {
            snapshot.put(id, new Entry(feature == null ? new float[0] : feature.clone(),
                    metadata == null ? Map.of() : new LinkedHashMap<>(metadata)));
        }

        /**
         * 把快照写入磁盘。
         *
         * <p><b>失败必须抛出，不能只告警。</b>此前这里是 {@code catch + log.warn}，
         * 于是 {@code enroll/remove/updateMetadata} 在磁盘写失败时照样返回成功：
         * 用户看到「登记成功」，{@code index.json} 里却没有这条，重启后人脸凭空消失，
         * 而 jvector 里的向量还在 —— 底库与索引从此对不上，且没有任何报错指出来源。
         * 删除路径同理：快照没删掉，图和向量却已经删了，重启后人脸回来但认不出。
         * 写盘失败属于「操作没有真正生效」，必须让调用方感知并回滚。</p>
         *
         * <p><b>磁盘快照只写 {@code id + feature}（向量重建来源），不写 metadata。</b>
         * 档案的权威在 DB（{@code sys_ai_face_library}），快照里的 metadata 只保留在
         * 内存中，用于旧格式文件的一次性存量迁移/列表兜底；落盘不重复保存，
         * 避免「怎么 DB 是权威、index.json 里还有基础数据」的二次来源。</p>
         *
         * @throws IllegalStateException 写盘失败
         */
        synchronized void persist() {
            Path file = dir.resolve(SNAPSHOT_NAME);
            try {
                Files.createDirectories(dir);
                List<Map<String, Object>> rows = new ArrayList<>(snapshot.size());
                for (Map.Entry<String, Entry> e : snapshot.entrySet()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", e.getKey());
                    row.put("feature", e.getValue().feature());
                    rows.add(row);
                }
                byte[] raw = objectMapper.writeValueAsBytes(rows);
                Path tmp = file.resolveSibling(SNAPSHOT_NAME + ".tmp");
                Files.write(tmp, raw);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                log.error("[Face] 账号 '{}' 人脸快照写入失败: {} -> {}", account, file, ex.getMessage(), ex);
                throw new IllegalStateException("人脸快照写入失败: " + file, ex);
            }
        }

        /**
         * 读快照。文件不存在按空库处理；损坏时保留上一份内存状态并告警。
         */
        private void loadSnapshot() {
            Path file = dir.resolve(SNAPSHOT_NAME);
            snapshot.clear();
            if (!Files.isRegularFile(file)) {
                return;
            }
            try {
                List<Map<String, Object>> rows = objectMapper.readValue(Files.readAllBytes(file),
                        new TypeReference<List<Map<String, Object>>>() {
                        });
                for (Map<String, Object> row : rows) {
                    if (row == null) {
                        continue;
                    }
                    Object id = row.get("id");
                    if (id == null || String.valueOf(id).isBlank()) {
                        continue;
                    }
                    float[] feature = toFloats(row.get("feature"));
                    if (feature.length == 0) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> meta = row.get("metadata") instanceof Map
                            ? (Map<String, Object>) row.get("metadata") : Map.of();
                    snapshot.put(String.valueOf(id), new Entry(feature, meta));
                }
            } catch (Exception ex) {
                log.warn("[Face] 账号 '{}' 人脸快照解析失败，按空库启动: {} -> {}",
                        account, file, ex.getMessage());
            }
        }

        /**
         * 把 JSON 数组转成 float[]。
         *
         * @param raw 原始值
         * @return 向量；无法转换时返回空数组
         */
        private float[] toFloats(Object raw) {
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return new float[0];
            }
            float[] out = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object v = list.get(i);
                out[i] = v instanceof Number n ? n.floatValue() : 0f;
            }
            return out;
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
                    log.debug("[Face] 关闭账号 '{}' 向量存储失败: {}", account, ex.getMessage());
                }
                storage = null;
                pipeline = null;
            }
        }
    }
}
