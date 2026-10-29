package com.chua.starter.filesystem.support.task;

import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import lombok.Getter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 上传任务管理器：任务化、异步化的文件上传核心。
 *
 * <p>上传动作只做一件事——创建任务并把分片落到本地暂存区，随后立即返回任务号；
 * 真正的「校验 → 分片合并 → 写入 FileStorage SPI → 清理暂存」全部由后台完成：</p>
 *
 * <ul>
 *   <li><b>时间轮调度</b>（{@code common-starter} 的 {@link Timer}）——
 *       分片齐全后的合并动作、过期任务回收均挂在时间轮上，O(1) 注册、到点触发；</li>
 *   <li><b>虚拟线程执行</b>——每个任务的合并与写入跑在独立虚拟线程上
 *       （{@code Thread.ofVirtual()}），慢任务互不阻塞，IO 等待近乎零成本；</li>
 *   <li><b>分片暂存</b>——分片按 {@code 暂存目录/{taskId}/{index}.part} 顺序落盘，
 *       合并时按序拼接为完整文件后一次性 {@code putObject}，避免大文件常驻堆内存。</li>
 * </ul>
 *
 * <p>任务状态机：{@code UPLOADING → MERGING → COMPLETED / FAILED}，另含 {@code CANCELLED}。
 * 全部任务保存在内存（{@link ConcurrentHashMap}），进程重启即清空——任务化上传面向
 * 「大文件、弱网、可观察」场景，持久化由调用方按需扩展。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class UploadTaskManager {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", UploadTaskManager.class);

    /**
     * 分片暂存根目录
     */
    private final Path chunkRoot;

    /**
     * 分片合并后、写入存储前的完整文件暂存目录
     */
    private final Path mergeRoot;

    /**
     * 文件存储模板（写入目标）
     */
    private final FileStorageTemplate fileStorageTemplate;

    /**
     * 时间轮：合并触发、过期回收
     */
    private final Timer timer;

    /**
     * 虚拟线程执行器：每个合并任务一个虚拟线程
     */
    private final ExecutorService mergeExecutor;

    /**
     * 全部任务（taskId → 任务）
     */
    private final Map<String, UploadTask> tasks = new ConcurrentHashMap<>();

    /**
     * 每任务一把「合并/完成」互斥锁（细粒度，避免全表竞争）
     */
    private final ConcurrentHashMap<String, Object> mergeLocks = new ConcurrentHashMap<>();

    /**
     * 任务过期时长（毫秒）：超时未完成即回收分片
     */
    private final long taskExpireMillis;

    /**
     * 推送监听器（SSE 连接）：任务状态/进度变化时回调，入参为变化的任务
     */
    private final List<java.util.function.Consumer<UploadTask>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 回收调度句柄（关闭时取消）
     */
    private final TimerTask sweepTask;

    /**
     * 构造。
     *
     * @param fileStorageTemplate 文件存储模板
     * @param taskExpireMinutes   任务过期分钟数（超时未完成回收分片）
     */
    public UploadTaskManager(FileStorageTemplate fileStorageTemplate, long taskExpireMinutes) {
        this.fileStorageTemplate = fileStorageTemplate;
        this.taskExpireMillis = TimeUnit.MINUTES.toMillis(Math.max(taskExpireMinutes, 1));
        try {
            this.chunkRoot = Files.createTempDirectory("upload-task-chunks");
            this.mergeRoot = Files.createTempDirectory("upload-task-merge");
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建上传任务暂存目录", e);
        }
        // 时间轮：512 槽、每 tick 100ms —— 合并触发延迟与过期回收的粒度足够
        this.timer = Timer.newTimer(512, 100, TimeUnit.MILLISECONDS);
        // 虚拟线程：每个合并任务独占一条，IO 阻塞不占用平台线程
        this.mergeExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("upload-merge-", 0).factory());
        // 定期回收过期任务（每 5 分钟一轮）
        this.sweepTask = timer.scheduleAtFixedRate(this::sweepExpiredTasks, 5, 5, TimeUnit.MINUTES);
    }

    // ==================== 推送（SSE） ====================

    /**
     * 注册推送监听器（每个 SSE 连接一个）。
     *
     * @param listener 监听器
     */
    public void addListener(java.util.function.Consumer<UploadTask> listener) {
        listeners.add(listener);
    }

    /**
     * 移除推送监听器（连接关闭时调用）。
     *
     * @param listener 监听器
     */
    public void removeListener(java.util.function.Consumer<UploadTask> listener) {
        listeners.remove(listener);
    }

    /**
     * 发布任务变化（快照为不可变对象引用，由接收方自行取值）。
     *
     * @param task 变化的任务
     */
    private void publish(UploadTask task) {
        for (java.util.function.Consumer<UploadTask> listener : listeners) {
            try {
                listener.accept(task);
            } catch (Exception e) {
                MODULE_LOG.warn("上传任务推送失败（忽略单连接异常）: {}", e.getMessage());
            }
        }
    }

    // ==================== 任务生命周期 ====================

    /**
     * 创建上传任务。
     *
     * @param fileName  文件名（调用方原始名）
     * @param totalSize 文件总字节数（可为 0 表示未知）
     * @param chunkSize 分片大小（字节；0 表示不分片，整文件一个分片）
     * @param provider  存储提供者名（null/空 → 默认提供者）
     * @param bucketDir 存储桶（目录路径；null → 提供者默认桶）
     * @return 任务
     */
    public UploadTask createTask(String fileName, long totalSize, long chunkSize,
                                 String provider, String bucketDir) {
        UploadTask task = new UploadTask(fileName, totalSize, chunkSize, provider, bucketDir);
        tasks.put(task.getTaskId(), task);
        publish(task);
        MODULE_LOG.info("上传任务已创建: id={} file={} size={} chunks={} provider={}",
                task.getTaskId(), fileName, totalSize, task.getTotalChunks(), provider);
        return task;
    }

    /**
     * 写入一个分片（落本地暂存，不触发合并）。
     *
     * @param taskId 任务号
     * @param index  分片序号（0 起）
     * @param bytes  分片内容
     * @return 任务；任务不存在返回 {@code null}
     * @throws IOException 分片落盘失败
     */
    public UploadTask putChunk(String taskId, int index, byte[] bytes) throws IOException {
        UploadTask task = tasks.get(taskId);
        if (task == null || task.getState() != UploadTask.State.UPLOADING) {
            return task;
        }
        Path dir = chunkRoot.resolve(taskId);
        Files.createDirectories(dir);
        Files.write(dir.resolve(index + ".part"), bytes);
        task.markChunkReceived(index, bytes.length);
        publish(task);
        return task;
    }

    /**
     * 标记分片上传完毕，把合并动作挂上时间轮（到期后由虚拟线程执行）。
     *
     * @param taskId 任务号
     * @return 任务；不存在或状态不允许返回 {@code null}
     */
    public UploadTask complete(String taskId) {
        UploadTask task = tasks.get(taskId);
        if (task == null || task.getState() != UploadTask.State.UPLOADING) {
            return null;
        }
        // 短延迟挂轮：给「最后一个分片的 putChunk 落盘」留出可见性窗口
        timer.schedule(() -> mergeExecutor.submit(() -> merge(taskId)), 200, TimeUnit.MILLISECONDS);
        return task;
    }

    /**
     * 取消任务：停止调度并清理分片。
     *
     * @param taskId 任务号
     * @return 被取消的任务；不存在返回 {@code null}
     */
    public UploadTask cancel(String taskId) {
        UploadTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        task.setState(UploadTask.State.CANCELLED);
        cleanupChunks(taskId);
        publish(task);
        MODULE_LOG.info("上传任务已取消: id={}", taskId);
        return task;
    }

    /**
     * 删除任务：从内存移除，并尝试删除已写入存储的对象。
     *
     * <p>与 {@link #cancel} 的区别：cancel 只是把状态置为 CANCELLED（记录仍在），
     * delete 则彻底移除记录并连带清理存储对象。调用方应先用 {@link #get}
     * 判断任务是否存在。</p>
     *
     * @param taskId 任务号
     * @return 删除前是否存在（true=存在并已删除）
     */
    public boolean delete(String taskId) {
        UploadTask task = tasks.remove(taskId);
        if (task == null) {
            return false;
        }
        cleanupChunks(taskId);
        String key = task.getObjectKey();
        if (key != null && !key.isBlank()) {
            try {
                if (task.getProvider() == null) {
                    fileStorageTemplate.deleteObject(key);
                } else {
                    fileStorageTemplate.deleteObject(task.getProvider(), key);
                }
                MODULE_LOG.info("上传任务已删除（含存储对象）: id={} key={}", taskId, key);
            } catch (Exception e) {
                MODULE_LOG.warn("删除存储对象失败（忽略，仅移除任务记录）: id={} key={} - {}", taskId, key, e.getMessage());
            }
        } else {
            MODULE_LOG.info("上传任务已删除（无存储对象）: id={}", taskId);
        }
        return true;
    }

    /**
     * 查询任务。
     *
     * @param taskId 任务号
     * @return 任务；不存在返回 {@code null}
     */
    public UploadTask get(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 登记一条「同步直传已完成」的任务（小文件 {@code /simple} 路径用）。
     *
     * <p>小文件同步直传不走分片任务链路：写入存储后立即返回，不经过
     * {@link #createTask} → {@link #putChunk} → {@link #complete}。
     * 若不登记，任务表（{@code GET /v2/file/upload-task/tasks}）与 SSE
     * 就永远看不到它，前端「文件导航」分组为空，用户以为上传丢了。</p>
     *
     * @param fileName   原始文件名
     * @param totalSize  文件总字节数
     * @param provider   存储提供者名（可空）
     * @param bucketDir  存储桶/目录路径（可空）
     * @param objectKey  已写入存储的对象键
     * @return 登记后的任务（状态 = COMPLETED）
     */
    public UploadTask registerSyncCompleted(String fileName, long totalSize,
                                            String provider, String bucketDir, String objectKey,
                                            String ext, String mime, Integer width, Integer height) {
        UploadTask task = new UploadTask(fileName, totalSize, 0, provider, bucketDir);
        task.setState(UploadTask.State.COMPLETED);
        task.markChunkReceived(0, (int) Math.min(totalSize, Integer.MAX_VALUE));
        task.setObjectKey(objectKey);
        task.setExt(ext);
        task.setMime(mime);
        task.setWidth(width);
        task.setHeight(height);
        tasks.put(task.getTaskId(), task);
        publish(task);
        MODULE_LOG.info("小文件同步直传已登记: id={} file={} size={} key={}",
                task.getTaskId(), fileName, totalSize, objectKey);
        return task;
    }

    /**
     * 全部任务快照（按创建时间倒序）。
     *
     * @return 任务列表
     */
    public List<UploadTask> list() {
        return tasks.values().stream()
                .sorted(Comparator.comparingLong(UploadTask::getCreatedAt).reversed())
                .toList();
    }

    /**
     * 列出已注册（已开启）的存储提供者，供前端上传时选择 bucket。
     *
     * @return 提供者名列表
     */
    public List<String> listProviders() {
        return fileStorageTemplate.getStorageNames();
    }

    // ==================== 合并与清理（虚拟线程内执行） ====================

    /**
     * 分片合并并写入存储。仅当全部分片到齐才执行；状态流转
     * {@code UPLOADING → MERGING → COMPLETED / FAILED}。
     *
     * @param taskId 任务号
     */
    private void merge(String taskId) {
        UploadTask task = tasks.get(taskId);
        if (task == null || task.getState() != UploadTask.State.UPLOADING) {
            return;
        }
        synchronized (mergeLocks.computeIfAbsent(taskId, k -> new Object())) {
            if (task.getState() != UploadTask.State.UPLOADING || !task.allChunksReceived()) {
                return;
            }
            task.setState(UploadTask.State.MERGING);
            publish(task);
            Path chunkDir = chunkRoot.resolve(taskId);
            Path mergedFile = mergeRoot.resolve(taskId + ".tmp");
            try {
                List<Path> parts = listSortedParts(chunkDir);
                Files.createDirectories(mergeRoot);
                concatParts(parts, mergedFile);
                writeStorage(task, mergedFile);
                task.setState(UploadTask.State.COMPLETED);
                task.touch();
                publish(task);
                MODULE_LOG.info("上传任务合并完成: id={} key={}", taskId, task.getObjectKey());
            } catch (Exception e) {
                task.setState(UploadTask.State.FAILED);
                task.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                publish(task);
                MODULE_LOG.error("上传任务合并失败: id={}", taskId, e);
            } finally {
                cleanupChunks(taskId);
                deleteQuietly(mergedFile);
                mergeLocks.remove(taskId);
            }
        }
    }

    /**
     * 按序拼接分片为完整文件（流式，不整文件驻留堆）。
     */
    private void concatParts(List<Path> parts, Path target) throws IOException {
        try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for (Path part : parts) {
                Files.copy(part, out);
            }
        }
    }

    /**
     * 把合并后的文件写入 FileStorage SPI。
     */
    private void writeStorage(UploadTask task, Path mergedFile) {
        FileStorage storage = task.getProvider() == null
                ? null : fileStorageTemplate.getStorage(task.getProvider());
        PutObjectRequest.PutObjectRequestBuilder builder = PutObjectRequest.builder()
                .fileName(task.getFileName())
                .file(mergedFile.toFile());
        if (task.getBucketDir() != null && !task.getBucketDir().isBlank()) {
            builder.filePath(task.getBucketDir());
        }
        PutObjectResult result = storage != null
                ? storage.putObject(builder.build())
                : fileStorageTemplate.putObject(builder.build());
        if (result == null || ObjectResult.ResultCode.FAILURE == result.getResultCode()) {
            throw new IllegalStateException("文件存储写入失败: "
                    + (result == null ? "无结果" : result.getMessage()));
        }
        task.setObjectKey(result.getKey());
        // 合并后仍是落盘文件：顺带探测元数据（扩展名 / MIME / 图片宽高）
        applyMeta(task, mergedFile.toFile());
    }

    /**
     * 探测并登记文件元数据（扩展名、MIME、图片宽高）。
     *
     * @param task 任务（就地写入元数据字段）
     * @param file 已落盘文件（分片合并后 / 同步直传暂存）
     */
    private void applyMeta(UploadTask task, java.io.File file) {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] head = in.readNBytes(128);
            String ext = extOf(task.getFileName());
            task.setExt(ext);
            task.setMime(mimeOf(ext));
            if (isImage(ext)) {
                int[] wh = imageDimension(head, ext);
                if (wh != null) {
                    task.setWidth(wh[0]);
                    task.setHeight(wh[1]);
                }
            }
        } catch (IOException ignored) {
            // 元数据探测失败不影响主流程
        }
    }

    /** 从文件名解析扩展名（小写、无点）。 */
    private static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 从文件名与字节内容探测元数据（扩展名 / MIME / 图片宽高）。
     *
     * @param fileName 原始文件名
     * @param content  文件字节（可为 null，图片宽高需真实头部）
     * @return 包含 ext/mime/width/height 的 Map（width/height 非图片或解析失败时缺省）
     */
    public static Map<String, Object> probeMetadata(String fileName, byte[] content) {
        Map<String, Object> meta = new java.util.HashMap<>();
        String ext = extOf(fileName);
        meta.put("ext", ext);
        meta.put("mime", mimeOf(ext));
        if (isImage(ext) && content != null && content.length > 0) {
            int[] wh = imageDimension(content, ext);
            if (wh != null) {
                meta.put("width", wh[0]);
                meta.put("height", wh[1]);
            }
        }
        return meta;
    }

    /** 常见扩展名 → MIME。 */
    private static String mimeOf(String ext) {
        if (ext == null || ext.isEmpty()) {
            return "application/octet-stream";
        }
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "txt", "log" -> "text/plain";
            case "md" -> "text/markdown";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "csv" -> "text/csv";
            case "zip" -> "application/zip";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            default -> "application/octet-stream";
        };
    }

    /** 图片扩展名判定。 */
    private static boolean isImage(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext)
                || "gif".equals(ext) || "bmp".equals(ext) || "webp".equals(ext);
    }

    /**
     * 从文件头解析图片宽高（PNG/JPEG/GIF/BMP/WebP）。
     *
     * @param head 文件头部字节（建议 ≥128 字节）
     * @param ext  扩展名
     * @return [宽, 高]；无法解析返回 null
     */
    private static int[] imageDimension(byte[] head, String ext) {
        if (head == null || head.length < 8) {
            return null;
        }
        int[] wh;
        switch (ext) {
            case "png" -> wh = pngDimension(head);
            case "jpg", "jpeg" -> wh = jpegDimension(head);
            case "gif" -> wh = gifDimension(head);
            case "bmp" -> wh = bmpDimension(head);
            case "webp" -> wh = webpDimension(head);
            default -> {
                return null;
            }
        }
        // 合理性校验：解析出的尺寸必须在合理范围内，否则视为误判（非图片/异常头）
        if (wh == null || wh[0] <= 0 || wh[1] <= 0
                || wh[0] > 40000 || wh[1] > 40000) {
            return null;
        }
        return wh;
    }

    private static int[] pngDimension(byte[] h) {
        // PNG 签名 8 字节 + IHDR: 宽=16..19, 高=20..23（大端）
        if (h.length < 24 || (h[0] & 0xFF) != 0x89 || h[1] != 0x50 || h[2] != 0x4E || h[3] != 0x47) {
            return null;
        }
        return new int[]{be(h, 16), be(h, 20)};
    }

    private static int[] jpegDimension(byte[] h) {
        // 扫描 SOF0/1/2 标记（FF C0..C2）：高度 = 偏移+5..+6，宽度 = +7..+8（大端）
        int i = 2;
        while (i + 9 < h.length) {
            if ((h[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marker = h[i + 1] & 0xFF;
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                return new int[]{be(h, i + 7), be(h, i + 5)};
            }
            int len = ((h[i + 2] & 0xFF) << 8) | (h[i + 3] & 0xFF);
            i += 2 + len;
        }
        return null;
    }

    private static int[] gifDimension(byte[] h) {
        // "GIF8" + 宽=6..7, 高=8..9（小端）
        if (h.length < 10 || h[0] != 'G' || h[1] != 'I' || h[2] != 'F') {
            return null;
        }
        return new int[]{le(h, 6), le(h, 8)};
    }

    private static int[] bmpDimension(byte[] h) {
        // "BM" + 宽=18..21, 高=22..25（小端，有符号）
        if (h.length < 26 || h[0] != 'B' || h[1] != 'M') {
            return null;
        }
        return new int[]{le(h, 18), Math.abs(le(h, 22))};
    }

    private static int[] webpDimension(byte[] h) {
        // "RIFF" + "WEBP"
        if (h.length < 30 || h[0] != 'R' || h[1] != 'I' || h[2] != 'F' || h[3] != 'F'
                || h[8] != 'W' || h[9] != 'E' || h[10] != 'B' || h[11] != 'P') {
            return null;
        }
        // VP8X（extended）：宽 = 24..26 三位小端 +1，高 = 27..29 +1
        if (h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'X' && h.length >= 30) {
            int w = (h[24] & 0xFF) | ((h[25] & 0xFF) << 8) | ((h[26] & 0xFF) << 16);
            int hh = (h[27] & 0xFF) | ((h[28] & 0xFF) << 8) | ((h[29] & 0xFF) << 16);
            return new int[]{w + 1, hh + 1};
        }
        // VP8 (lossy)：宽 = 26..29 小端 & 0x3FFF，高 = 28..31 & 0x3FFF
        if (h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == ' ' && h.length >= 32) {
            int w = le(h, 26) & 0x3FFF;
            int hh = le(h, 28) & 0x3FFF;
            return new int[]{w, hh};
        }
        // VP8L (lossless)：宽 = 24 起 14bit，高 = 下 14bit
        if (h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'L' && h.length >= 28) {
            int bits = (h[21] & 0xFF) | ((h[22] & 0xFF) << 8) | ((h[23] & 0xFF) << 16) | ((h[24] & 0xFF) << 24);
            int w = (bits & 0x3FFF) + 1;
            int hh = ((bits >> 14) & 0x3FFF) + 1;
            return new int[]{w, hh};
        }
        return null;
    }

    private static int be(byte[] h, int off) {
        return ((h[off] & 0xFF) << 24) | ((h[off + 1] & 0xFF) << 16)
                | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
    }

    private static int le(byte[] h, int off) {
        return (h[off] & 0xFF) | ((h[off + 1] & 0xFF) << 8)
                | ((h[off + 2] & 0xFF) << 16) | ((h[off + 3] & 0xFF) << 24);
    }

    /**
     * 列出分片文件并按序号升序。
     */
    private List<Path> listSortedParts(Path chunkDir) throws IOException {
        List<Path> parts = new ArrayList<>();
        if (!Files.isDirectory(chunkDir)) {
            return parts;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDir, "*.part")) {
            for (Path p : stream) {
                parts.add(p);
            }
        }
        parts.sort(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().replace(".part", ""))));
        return parts;
    }

    /**
     * 注册周期心跳回调（挂时间轮；用于 SSE 连接保活）。
     *
     * @param pinger       心跳回调
     * @param intervalSecs 间隔秒数
     * @return 心跳调度句柄（连接关闭时应 {@code timer.cancel(handle)}）
     */
    public TimerTask heartbeat(java.util.function.Consumer<String> pinger, long intervalSecs) {
        return timer.scheduleAtFixedRate(() -> pinger.accept("ping"), intervalSecs, intervalSecs, TimeUnit.SECONDS);
    }

    /**
     * 取消心跳调度（SSE 连接关闭时调用）。
     *
     * @param handle 心跳调度句柄
     */
    public void cancelHeartbeat(TimerTask handle) {
        if (handle != null) {
            timer.cancel(handle);
        }
    }

    /**
     * 回收过期任务：超时仍在 UPLOADING 的任务标记失败并清理分片。
     */
    private void sweepExpiredTasks() {
        long now = System.currentTimeMillis();
        for (UploadTask task : tasks.values()) {
            if (task.getState() == UploadTask.State.UPLOADING
                    && now - task.getLastActiveAt() > taskExpireMillis) {
                task.setState(UploadTask.State.FAILED);
                task.setMessage("上传超时（超过 " + (taskExpireMillis / 60000) + " 分钟无活动）");
                cleanupChunks(task.getTaskId());
                publish(task);
                MODULE_LOG.warn("上传任务已超时回收: id={}", task.getTaskId());
            }
        }
    }

    /**
     * 清理任务分片目录。
     */
    private void cleanupChunks(String taskId) {
        Path dir = chunkRoot.resolve(taskId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                deleteQuietly(p);
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            MODULE_LOG.warn("分片清理失败: id={}", taskId, e);
        }
    }

    /**
     * 静默删除文件。
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    /**
     * 关闭：取消回收调度、停轮、停执行器（应用下线时调用）。
     */
    public void shutdown() {
        if (sweepTask != null) {
            timer.cancel(sweepTask);
        }
        timer.shutdown();
        mergeExecutor.shutdown();
        // 清空暂存
        deleteTree(chunkRoot);
        deleteTree(mergeRoot);
    }

    /**
     * 递归删除目录。
     */
    private void deleteTree(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // 关闭期清理失败可忽略
        }
    }

    /**
     * 上传任务。
     */
    @Getter
    public static class UploadTask {

        /**
         * 任务号
         */
        private final String taskId;

        /**
         * 原始文件名
         */
        private final String fileName;

        /**
         * 文件总字节数（未知为 0）
         */
        private final long totalSize;

        /**
         * 分片大小（字节）
         */
        private final long chunkSize;

        /**
         * 存储提供者名（null → 默认提供者）
         */
        private final String provider;

        /**
         * 存储桶（目录路径）
         */
        private final String bucketDir;

        /**
         * 创建时间（毫秒）
         */
        private final long createdAt = System.currentTimeMillis();

        /**
         * 已收分片数
         */
        private final AtomicInteger receivedChunks = new AtomicInteger();

        /**
         * 已收字节数
         */
        private final AtomicLong receivedBytes = new AtomicLong();

        /**
         * 最后活动时间（毫秒）
         */
        private volatile long lastActiveAt = System.currentTimeMillis();

        /**
         * 任务状态
         */
        private volatile State state = State.UPLOADING;

        /**
         * 附加消息（失败原因等）
         */
        private volatile String message;

        /**
         * 存储侧对象键（合并成功后回填）
         */
        private volatile String objectKey;

        /**
         * 文件扩展名（小写，无点，如 "png"）
         */
        private volatile String ext;

        /**
         * MIME 类型（如 "image/png"）
         */
        private volatile String mime;

        /**
         * 图片宽度（像素；非图片为 null）
         */
        private volatile Integer width;

        /**
         * 图片高度（像素；非图片为 null）
         */
        private volatile Integer height;

        /**
         * 构造。
         */
        UploadTask(String fileName, long totalSize, long chunkSize, String provider, String bucketDir) {
            this.taskId = java.util.UUID.randomUUID().toString().replace("-", "");
            this.fileName = fileName;
            this.totalSize = Math.max(totalSize, 0);
            this.chunkSize = Math.max(chunkSize, 0);
            this.provider = provider;
            this.bucketDir = bucketDir;
        }

        /**
         * 分片总数（chunkSize<=0 视为单分片）。
         */
        public int getTotalChunks() {
            if (chunkSize <= 0) {
                return 1;
            }
            if (totalSize <= 0) {
                return 1;
            }
            return (int) ((totalSize + chunkSize - 1) / chunkSize);
        }

        /**
         * 是否全部分片已到齐（分片数未知时以显式 complete 为准，恒放行）。
         */
        public boolean allChunksReceived() {
            return totalSize <= 0 || receivedChunks.get() >= getTotalChunks();
        }

        /**
         * 记录一个分片到达。
         */
        void markChunkReceived(int index, int bytes) {
            receivedChunks.incrementAndGet();
            receivedBytes.addAndGet(bytes);
            lastActiveAt = System.currentTimeMillis();
            // index 幂等性：文件名覆盖写入，重复传同片不放大计数以外的副作用
            message = null;
        }

        /**
         * 刷新活跃时间。
         */
        void touch() {
            lastActiveAt = System.currentTimeMillis();
        }

        public int getReceivedChunks() {
            return receivedChunks.get();
        }

        public long getReceivedBytes() {
            return receivedBytes.get();
        }

        public void setState(State state) {
            this.state = state;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
        }

        public String getExt() {
            return ext;
        }

        public void setExt(String ext) {
            this.ext = ext;
        }

        public String getMime() {
            return mime;
        }

        public void setMime(String mime) {
            this.mime = mime;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        /**
         * 任务状态。
         */
        public enum State {
            /** 分片接收中 */
            UPLOADING,
            /** 合并中 */
            MERGING,
            /** 已完成（已写入存储） */
            COMPLETED,
            /** 失败 */
            FAILED,
            /** 已取消 */
            CANCELLED
        }
    }
}
