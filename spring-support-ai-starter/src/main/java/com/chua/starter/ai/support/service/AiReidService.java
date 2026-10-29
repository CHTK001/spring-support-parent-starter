package com.chua.starter.ai.support.service;

import com.chua.deeplearning.support.reid.PersonTrack;

import java.util.List;

/**
 * AI 人脸重识别服务接口。
 *
 * <p>与人脸识别（{@link AiFaceService}）的分工：</p>
 * <ul>
 *   <li>人脸识别回答「这张脸是谁」——1:1 核对或 1:N 定库检索，底库是<b>登记过的人</b>；</li>
 *   <li>重识别回答「<b>这个人</b>刚才从哪个摄像头走进了哪个摄像头」——
 *       底库是<b>实时观测到的轨迹</b>，不需要事先登记。</li>
 * </ul>
 *
 * <p>配置读取自 {@code reid} 分组全局设置，配置变更通过
 * {@link com.chua.starter.ai.support.configuration.reid.ReidClientManager#rebuild()} 热更新。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface AiReidService {

    /**
     * 观测一帧场景图：检测人脸 → 逐张提特征 → 归并进跨帧轨迹 → <b>落库</b>。
     *
     * <p>同一个人在不同摄像头出现时会自动并入同一条轨迹。
     * 观测会写入重识别底库（{@code sys_ai_reid_library}），
     * 因此轨迹数据在进程重启后依然存在。</p>
     *
     * <p><b>分组</b>：观测落进指定分组，轨迹也只在同分组内归并 ——
     * 分组是隔离的第一层，「访客」组的人不该并进「员工」组的轨迹里。</p>
     *
     * @param group    分组名（空值按默认组 {@code default}）
     * @param cameraId 摄像头标识；为空时使用配置的默认摄像头
     * @param sourceId 来源帧标识；为空时自动生成
     * @param imageData 图片字节
     * @param lat      纬度（可空，观测位置）
     * @param lng      经度（可空，观测位置）
     * @return 观测结果
     */
    ObserveResult observe(String group, String cameraId, String sourceId, byte[] imageData,
                          java.math.BigDecimal lat, java.math.BigDecimal lng);

    /**
     * 观测一帧（不带位置）。
     *
     * @param group     分组名
     * @param cameraId  摄像头标识
     * @param sourceId  来源帧标识
     * @param imageData 图片字节
     * @return 观测结果
     */
    default ObserveResult observe(String group, String cameraId, String sourceId, byte[] imageData) {
        return observe(group, cameraId, sourceId, imageData, null, null);
    }

    /**
     * 以图搜人：在已有轨迹中检索最相似的人。
     *
     * @param groups    分组名列表（空/未指定 = 只查默认组，**不等于**查全部）
     * @param imageData 图片字节
     * @param topK      返回条数；≤0 时使用配置值
     * @return 命中列表（按相似度降序）
     */
    List<ReidHit> search(List<String> groups, byte[] imageData, int topK);

    /**
     * 列出轨迹。
     *
     * <p>轨迹<b>从重识别底库汇聚</b>（按 track 分组），因此进程重启后依然可见。</p>
     *
     * @param group     分组名（空值按默认组）
     * @param cameraId  只看某个摄像头的轨迹；为空表示全部
     * @param crossOnly 只看跨摄像头轨迹
     * @param limit     返回上限；≤0 表示不限
     * @return 轨迹列表
     */
    List<Track> tracks(String group, String cameraId, boolean crossOnly, int limit);

    /**
     * 按 ID 取轨迹详情。
     *
     * @param group   分组名（空值按默认组）
     * @param trackId 轨迹 ID
     * @return 轨迹；不存在返回 {@code null}
     */
    Track track(String group, String trackId);

    /**
     * 指定分组的轨迹总数。
     *
     * @param group 分组名（空值按默认组）
     * @return 数量
     */
    int trackCount(String group);

    /**
     * 清空指定分组的全部轨迹。
     *
     * @param group 分组名（空值按默认组）
     * @return 清空的轨迹数
     */
    int clear(String group);

    /**
     * 当前账号已有数据的分组名列表（供前端分组选择器）。
     *
     * <p>总是包含 {@link AiLibraryGroups#DEFAULT}：即使某账号还没有任何数据，
     * 前端也该有一个可选项，而不是空下拉框。</p>
     *
     * @return 分组名列表（去重、排序）
     */
    List<String> groups();

    /**
     * 重识别能力是否就绪（检测与特征模型均已配置）。
     *
     * @return 就绪返回 true
     */
    boolean available();

    /**
     * 一次观测的结果。
     *
     * @param cameraId    摄像头标识
     * @param sourceId    来源帧标识
     * @param faceCount   本帧检出人脸数
     * @param trackIds    本帧涉及的轨迹 ID 列表
     * @param matched     其中命中已有轨迹的数量
     * @param newTracks   其中新建轨迹的数量
     * @param faceMatches 其中走人脸识别的数量（脸清晰）
     * @param reidMatches 其中走重识别的数量（脸模糊/远处）
     * @param faces       逐张脸的检测框与归属轨迹（供前端在图上叠框）
     */
    record ObserveResult(String cameraId,
                          String sourceId,
                          int faceCount,
                          List<String> trackIds,
                          int matched,
                          int newTracks,
                          int faceMatches,
                          int reidMatches,
                          List<FaceBox> faces) {

        /**
         * 规范构造器：集合不可变。
         *
         * @param trackIds 轨迹 ID 列表
         * @param faces    人脸框列表
         */
        public ObserveResult {
            trackIds = trackIds == null ? List.of() : List.copyOf(trackIds);
            faces = faces == null ? List.of() : List.copyOf(faces);
        }

        /**
         * 兼容旧构造（不含分流统计）。
         *
         * @param cameraId    摄像头标识
         * @param sourceId    来源帧标识
         * @param faceCount   检出人脸数
         * @param trackIds    轨迹 ID 列表
         * @param matched     命中已有轨迹数
         * @param newTracks   新建轨迹数
         * @param faces       人脸框列表
         */
        public ObserveResult(String cameraId, String sourceId, int faceCount,
                             List<String> trackIds, int matched, int newTracks,
                             List<FaceBox> faces) {
            this(cameraId, sourceId, faceCount, trackIds, matched, newTracks, 0, 0, faces);
        }
    }

    /**
     * 分流结果类型：这张脸走了哪条路。
     *
     * <p><b>为什么要标注类型。</b>清晰人脸与模糊人脸走的是两条完全不同的路，
     * 回答的问题也不同：清晰人脸走<b>人脸识别</b>回答「这是谁」，模糊人脸走
     * <b>重识别</b>回答「这个人去过哪」。两者一起运行时，前端必须能区分
     * 「这一条是人脸识别认出来的」与「这一条是重识别轨迹命中的」，
     * 否则用户看到的结果无法解释。</p>
     */
    enum MatchType {

        /**
         * 人脸识别命中（1:N 定库检索）：脸清晰，认出了是谁。
         */
        FACE("人脸识别"),

        /**
         * 重识别命中（跨摄像头轨迹）：脸不够清晰，只知道这个人去过哪。
         */
        REID("重识别");

        /**
         * 中文名称
         */
        private final String label;

        MatchType(String label) {
            this.label = label;
        }

        /**
         * 中文名称。
         *
         * @return 名称
         */
        public String label() {
            return label;
        }
    }

    /**
     * 一张人脸的检测框与归属轨迹（前端叠框用）。
     *
     * @param x       左上角 X（原图像素）
     * @param y       左上角 Y
     * @param width   宽
     * @param height  高
     * @param trackId 归属轨迹
     * @param score   与轨迹的相似度
     * @param matched 是否命中已有轨迹
     * @param confidence 检测置信度（决定分流到哪条路）
     * @param matchType   分流类型（人脸识别 / 重识别）
     * @param faceName    认出的姓名（人脸识别命中时非空）
     */
    record FaceBox(float x, float y, float width, float height,
                    String trackId, double score, boolean matched,
                    float confidence, String matchType, String faceName) {

        /**
         * 兼容旧构造（只有框与轨迹）。
         *
         * @param x       左上角 X
         * @param y       左上角 Y
         * @param width   宽
         * @param height  高
         * @param trackId 归属轨迹
         * @param score   相似度
         * @param matched 是否命中
         */
        public FaceBox(float x, float y, float width, float height,
                       String trackId, double score, boolean matched) {
            this(x, y, width, height, trackId, score, matched, 0f, null, null);
        }
    }

    /**
     * 一条跨帧轨迹（对外视图，由重识别底库汇聚）。
     *
     * @param trackId      轨迹 ID
     * @param cameraIds    出现过的摄像头（去重）
     * @param firstSeen    首次观测时间（毫秒）
     * @param lastSeen     最近观测时间（毫秒）
     * @param observations 观测次数
     * @param crossCamera  是否跨摄像头
     * @param lat          最近一次观测的纬度（可空）
     * @param lng          最近一次观测的经度（可空）
     * @param faceName     关联的人脸姓名（可空）
     * @param thumbnail    最近一次观测的缩略图（可空）
     */
    record Track(String trackId,
                 List<String> cameraIds,
                 long firstSeen,
                 long lastSeen,
                 int observations,
                 boolean crossCamera,
                 java.math.BigDecimal lat,
                 java.math.BigDecimal lng,
                 String faceName,
                 String thumbnail) {

        /**
         * 规范构造器：集合不可变。
         *
         * @param cameraIds 摄像头集合
         */
        public Track {
            cameraIds = cameraIds == null ? List.of() : List.copyOf(cameraIds);
        }
    }

    /**
     * 以图搜人的命中项。
     *
     * @param trackId    轨迹 ID
     * @param similarity 相似度
     * @param track      轨迹详情
     * @param matchType  分流类型（人脸识别 / 重识别）
     * @param faceName   认出的姓名（人脸识别命中时非空）
     */
    record ReidHit(String trackId, double similarity, Track track,
                   String matchType, String faceName) {

        /**
         * 兼容旧构造。
         *
         * @param trackId    轨迹 ID
         * @param similarity 相似度
         * @param track      轨迹详情
         */
        public ReidHit(String trackId, double similarity, Track track) {
            this(trackId, similarity, track, null, null);
        }
    }
}
