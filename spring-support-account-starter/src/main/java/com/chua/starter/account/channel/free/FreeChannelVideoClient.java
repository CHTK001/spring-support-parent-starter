// [P3C A20 豁免] A20/A30/A31: 含错误文案等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.free;

import com.chua.common.support.ai.generation.VideoGenerationResult;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoClientSetting;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.spi.VideoChannelProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 免费渠道聚合 {@link VideoClient}。
 *
 * <p><b>归属</b>：与 {@link FreeChannelImageClient} 一致，免费渠道是账号模块的聚合出口，
 * 视频模型属于账号模块内置的免登录文生视频渠道（{@code video-doubao} / {@code video-qwen}），
 * 因此本实现只在账号模块维护。</p>
 *
 * <p>免费渠道的模型目录把各免登录渠道的视频模型并入 {@code free-channel}
 * （账号模块启动时由物化器写进 {@code sys_account_channel_model}），若没有本实现，
 * 这些模型行在 {@code /v2/ai/generate/models} 里会被「供应商无视频实现」过滤掉，
 * 或选中后解析不到客户端。本类按模型标识反查内置文生视频渠道并转交，
 * 让免费渠道下的视频模型真正可出片。</p>
 *
 * <p><b>同步转异步</b>：内置渠道的 {@code generate} 是同步调用（浏览器面板驱动，
 * 单次数十秒到数分钟），而 {@link VideoClient} 是异步契约（{@code createTask} 立即
 * 返回任务号 + {@code queryTask} 轮询）。本类在客户端内部桥接：{@code createTask}
 * 起后台线程执行同步生成，任务态存进程内静态表——解析器的客户端缓存过期后会重建
 * 实例来轮询旧任务，任务态必须跨实例可见。AI 侧的视频流程（提交后轮询）保持零改动。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Spi({"free-channel"})
public class FreeChannelVideoClient implements VideoClient {

    /**
     * 免费渠道的 {@code VideoClient} SPI 扩展名。
     */
    public static final String EXTENSION = "free-channel";

    /**
     * 进程内任务表：taskId → 任务态。
     *
     * <p>static 保证跨客户端实例可见（解析器缓存过期后会 new 新实例来轮询旧任务）。
     * 容量超过 {@link #MAX_TASKS} 时先淘汰已终结任务，防止长期运行内存膨胀。</p>
     */
    private static final Map<String, TaskState> TASKS = new ConcurrentHashMap<>();

    /**
     * 任务表容量上限
     */
    private static final int MAX_TASKS = 256;

    /**
     * 客户端配置（provider=free-channel）
     */
    private final VideoClientSetting setting;

    /**
     * 当前模型标识（doubao-video / qwen-video）
     */
    private String model;

    /**
     * 目标宽度（{@code null} 表示按渠道默认）
     */
    private Integer width;

    /**
     * 目标高度（{@code null} 表示按渠道默认）
     */
    private Integer height;

    /**
     * 构造免费渠道视频生成客户端。
     *
     * @param setting 客户端配置
     */
    public FreeChannelVideoClient(VideoClientSetting setting) {
        this.setting = setting;
        this.model = setting == null ? null : setting.getModel();
    }

    /**
     * 设置模型标识（doubao-video / qwen-video 等）。
     *
     * @param model 模型标识
     * @return 当前实例
     */
    @Override
    public VideoClient model(String model) {
        this.model = model;
        return this;
    }

    /**
     * 设置生成视频的分辨率（换算成宽高比交给渠道）。
     *
     * @param width  宽度
     * @param height 高度
     * @return 当前实例
     */
    @Override
    public VideoClient size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * 提交视频生成任务：起后台线程跑同步渠道生成，立即返回任务号。
     *
     * @param prompt 提示词
     * @return 任务 ID
     */
    @Override
    public String createTask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        VideoChannelProvider channel = resolveChannel(model);
        String taskId = "free-" + UUID.randomUUID();
        evictTerminalIfFull();
        TASKS.put(taskId, new TaskState());
        Thread worker = new Thread(() -> run(taskId, channel, prompt));
        worker.setName("free-video-" + taskId);
        worker.setDaemon(true);
        worker.start();
        return taskId;
    }

    /**
     * 查询任务状态：映射进程内任务态；未知任务返回 {@code null}（调用方视为无变化）。
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    @Override
    public VideoResponse queryTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        TaskState state = TASKS.get(taskId);
        if (state == null) {
            return null;
        }
        return VideoResponse.builder()
                .taskId(taskId)
                .status(state.status)
                .progress(state.progress)
                .videoUrl(state.videoUrl)
                .errorMessage(state.errorMessage)
                .build();
    }

    /**
     * 免费视频渠道暂不支持图生视频：显式拒绝，避免参考图被静默丢弃后照常生成。
     *
     * @param image 参考图字节
     * @return 不会返回
     */
    @Override
    public VideoClient referenceImage(byte[] image) {
        throw new UnsupportedOperationException("免费视频渠道暂不支持图生视频，请改用文生视频");
    }

    /**
     * 后台执行同步生成并回写任务态。
     *
     * @param taskId  任务 ID
     * @param channel 命中的内置视频渠道
     * @param prompt  提示词
     */
    private void run(String taskId, VideoChannelProvider channel, String prompt) {
        TaskState state = TASKS.get(taskId);
        if (state == null) {
            return;
        }
        state.begin();
        try {
            VideoGenerationResult result = channel.generate(prompt, ratio(), null, null,
                    VideoChannelProvider.DEFAULT_TIMEOUT_SECONDS, model);
            state.complete(firstUrl(result));
        } catch (Exception e) {
            String message = e.getMessage();
            state.fail(message == null || message.isBlank() ? "免费视频渠道生成失败" : message);
        }
    }

    /**
     * 按模型标识反查内置文生视频渠道。
     *
     * <p>模型标识来自各渠道自己的 {@link VideoChannelProvider#listModels()}，
     * 因此新增免登录视频渠道时无需改动本类。多个渠道声明同名模型时取优先级最高者；
     * 未指定模型时取声明了模型且优先级最高的渠道。</p>
     *
     * @param model 模型标识，可为空
     * @return 命中的渠道，未命中抛异常
     */
    private static VideoChannelProvider resolveChannel(String model) {
        boolean byModel = model != null && !model.isBlank();
        String target = byModel ? model.trim() : null;
        VideoChannelProvider matched = null;
        for (VideoChannelProvider channel : ChannelRegistry.videoChannels().values()) {
            if (channel == null || !declares(channel)) {
                continue;
            }
            if (byModel && !supports(channel, target)) {
                continue;
            }
            if (matched == null || channel.priority() > matched.priority()) {
                matched = channel;
            }
        }
        if (matched == null) {
            throw new IllegalStateException(byModel
                    ? "免费渠道下没有可用的视频渠道声明模型: " + target
                    : "免费渠道下没有可用的视频渠道");
        }
        return matched;
    }

    /**
     * 渠道是否声明了可用模型。
     *
     * @param channel 文生视频渠道
     * @return true 表示至少有一个模型
     */
    private static boolean declares(VideoChannelProvider channel) {
        List<Map<String, Object>> models = channel.listModels();
        return CollectionUtils.isNotEmpty(models);
    }

    /**
     * 渠道是否声明了指定模型。
     *
     * @param channel 文生视频渠道
     * @param model   模型标识
     * @return true 表示命中
     */
    private static boolean supports(VideoChannelProvider channel, String model) {
        List<Map<String, Object>> models = channel.listModels();
        if (models == null) {
            return false;
        }
        for (Map<String, Object> row : models) {
            Object id = row == null ? null : row.get("id");
            if (id != null && model.equalsIgnoreCase(String.valueOf(id).trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取第一个生成视频的播放地址。
     *
     * @param result 渠道生成结果
     * @return 视频地址
     */
    private static String firstUrl(VideoGenerationResult result) {
        if (result == null || result.videos() == null || result.videos().isEmpty()) {
            throw new IllegalStateException("免费视频渠道未返回视频");
        }
        VideoGenerationResult.GeneratedVideo video = result.videos().get(0);
        String url = video == null ? null : video.videoUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("免费视频渠道返回的视频缺少地址");
        }
        return url;
    }

    /**
     * 宽高换算成渠道的 ratio 参数（约分，如 1920x1080 → {@code 16:9}）。
     *
     * @return 宽高比；尺寸无效时返回 {@code null}（按渠道默认）
     */
    private String ratio() {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        int a = width;
        int b = height;
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        int gcd = a <= 0 ? 1 : a;
        return (width / gcd) + ":" + (height / gcd);
    }

    /**
     * 任务表满时淘汰已终结任务（成功 / 失败），给新任务腾位。
     */
    private static void evictTerminalIfFull() {
        if (TASKS.size() < MAX_TASKS) {
            return;
        }
        TASKS.values().removeIf(state ->
                state.status == VideoResponse.Status.SUCCESS
                        || state.status == VideoResponse.Status.FAILED);
    }

    /**
     * 进程内任务态（字段 volatile：后台线程写、轮询线程读）。
     */
    private static final class TaskState {

        /**
         * 当前状态
         */
        private volatile VideoResponse.Status status = VideoResponse.Status.PENDING;

        /**
         * 进度（0-100）
         */
        private volatile Integer progress = 0;

        /**
         * 生成完成的视频地址
         */
        private volatile String videoUrl;

        /**
         * 失败原因
         */
        private volatile String errorMessage;

        /**
         * 进入执行中。
         */
        private void begin() {
            this.status = VideoResponse.Status.RUNNING;
            this.progress = 50;
        }

        /**
         * 标记成功。
         *
         * @param url 视频地址
         */
        private void complete(String url) {
            this.videoUrl = url;
            this.progress = 100;
            this.status = VideoResponse.Status.SUCCESS;
        }

        /**
         * 标记失败。
         *
         * @param message 失败原因
         */
        private void fail(String message) {
            this.errorMessage = message;
            this.progress = 100;
            this.status = VideoResponse.Status.FAILED;
        }
    }
}
