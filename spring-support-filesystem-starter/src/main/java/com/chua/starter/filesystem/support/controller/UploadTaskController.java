package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.task.UploadTaskManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 上传任务接口：任务化、异步化的分片上传。
 *
 * <p>上传动作只创建任务（同步返回任务号），分片与合并全部在后台完成：
 * 分片经 {@link #putChunk} 落本地暂存区，{@link #complete} 后由
 * {@code common-starter} 时间轮调度、虚拟线程执行合并并写入 {@code FileStorage} SPI。</p>
 *
 * <p>典型时序：</p>
 * <pre>
 * 1. GET  /v2/file/upload-task/buckets   → 可用 bucket（后台已开启的提供者）
 * 2. POST /v2/file/upload-task/task      → 创建任务，得 taskId / totalChunks
 * 3. PUT  /v2/file/upload-task/task/{id}/chunk?index=n  （body 为原始分片字节，可并发）
 * 4. POST /v2/file/upload-task/task/{id}/complete        → 触发后台合并
 * 5. GET  /v2/file/upload-task/tasks 或 /task/{id}       → 轮询任务状态
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/file/upload-task")
@Tag(name = "上传任务接口")
public class UploadTaskController {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", UploadTaskController.class);

    /**
     * 上传任务管理器
     */
    private final UploadTaskManager uploadTaskManager;

    /**
     * 文件存储模板（小文件同步直传走它，不建任务）
     */
    private final com.chua.starter.filesystem.support.template.FileStorageTemplate fileStorageTemplate;

    /**
     * 小文件同步直传阈值（MB，超过才走任务化分片链路）
     */
    private final long syncMaxBytes;

    public UploadTaskController(UploadTaskManager uploadTaskManager,
                                com.chua.starter.filesystem.support.template.FileStorageTemplate fileStorageTemplate,
                                com.chua.starter.filesystem.support.properties.FileStorageProperties properties) {
        this.uploadTaskManager = uploadTaskManager;
        this.fileStorageTemplate = fileStorageTemplate;
        this.syncMaxBytes = Math.max(properties.getTaskSyncMaxMb(), 0) * 1024L * 1024L;
    }

    /**
     * 列出后台已开启的存储桶（提供者），供前端上传时选择。
     *
     * @return 提供者名列表
     */
    @Operation(summary = "列出可用 bucket")
    @GetMapping("/buckets")
    public ReturnResult<List<String>> buckets() {
        return ReturnResult.ok(uploadTaskManager.listProviders());
    }

    /**
     * 创建上传任务。
     *
     * @param request 任务描述（文件名、总大小、分片大小、bucket 等）
     * @return 任务快照（含 taskId 与分片总数）
     */
    @Operation(summary = "创建上传任务")
    @PostMapping("/task")
    public ReturnResult<Map<String, Object>> create(@RequestBody TaskCreateRequest request) {
        if (request == null || request.fileName() == null || request.fileName().isBlank()) {
            return ReturnResult.illegal("fileName 不能为空");
        }
        UploadTaskManager.UploadTask task = uploadTaskManager.createTask(
                request.fileName(),
                request.totalSize() == null ? 0 : request.totalSize(),
                request.chunkSize() == null ? 0 : request.chunkSize(),
                request.provider(),
                request.bucket());
        return ReturnResult.ok(toMap(task));
    }

    /**
     * 单文件直传：小文件同步写入存储立即返回；大文件走任务链路（后台合并）。
     *
     * <p>分流阈值 {@code plugin.filesystem.task-sync-max-mb}（默认 5MB）：
     * 不超过阈值时同步 {@code putObject} 并直接返回 {@code state=COMPLETED}（含对象键），
     * 不建任务、不经时间轮；超过阈值才创建任务，返回任务号供轮询。</p>
     *
     * @param file     文件
     * @param fileBucket 存储桶（可选）
     * @param ossType    存储提供者（可选）
     * @param request    HTTP 请求（未直接使用，Swagger 文档需要）
     * @return 任务快照（小文件直接 COMPLETED；大文件为 UPLOADING 供轮询）
     */
    @Operation(summary = "单文件直传（小文件同步 / 大文件任务化）")
    @RequestMapping(value = "/simple", method = {RequestMethod.PUT, RequestMethod.POST})
    public ReturnResult<Map<String, Object>> simple(
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @Parameter(description = "存储桶") @RequestParam(value = "fileBucket", required = false) String fileBucket,
            @Parameter(description = "存储提供者") @RequestParam(value = "ossType", required = false) String ossType,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.illegal("文件不能为空");
        }
        String provider = ossType == null || ossType.isBlank() ? null : ossType;
        String bucket = fileBucket == null || fileBucket.isBlank() ? null : fileBucket;
        try {
            // 小文件：同步直传，不建任务
            if (file.getSize() <= syncMaxBytes) {
                return ReturnResult.ok(syncUpload(file, provider, bucket));
            }
            byte[] bytes = file.getBytes();
            UploadTaskManager.UploadTask task = uploadTaskManager.createTask(
                    file.getOriginalFilename(), file.getSize(), 0, provider, bucket);
            uploadTaskManager.putChunk(task.getTaskId(), 0, bytes);
            uploadTaskManager.complete(task.getTaskId());
            return ReturnResult.ok(toMap(task));
        } catch (IOException e) {
            MODULE_LOG.error("单文件直传读取失败: {}", file.getOriginalFilename(), e);
            return ReturnResult.error("读取上传内容失败");
        }
    }

    /**
     * 小文件同步直传：立即写入存储并返回完成任务快照。
     */
    private Map<String, Object> syncUpload(org.springframework.web.multipart.MultipartFile file,
                                           String provider, String bucket) throws IOException {
        com.chua.common.support.storage.FileStorage storage = provider == null
                ? null : fileStorageTemplate.getStorage(provider);
        com.chua.common.support.storage.request.PutObjectRequest.PutObjectRequestBuilder builder =
                com.chua.common.support.storage.request.PutObjectRequest.builder()
                        .fileName(file.getOriginalFilename())
                        .inputStream(file.getInputStream());
        if (bucket != null) {
            builder.filePath(bucket);
        }
        com.chua.common.support.storage.result.PutObjectResult result = storage != null
                ? storage.putObject(builder.build())
                : fileStorageTemplate.putObject(builder.build());
        if (result == null
                || com.chua.common.support.storage.result.ObjectResult.ResultCode.FAILURE == result.getResultCode()) {
            MODULE_LOG.error("小文件同步直传失败: {} provider={}", file.getOriginalFilename(), provider);
            throw new IllegalStateException("文件保存失败");
        }
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("taskId", "");
        map.put("fileName", file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        map.put("totalSize", file.getSize());
        map.put("receivedBytes", file.getSize());
        map.put("chunkSize", 0);
        map.put("totalChunks", 1);
        map.put("receivedChunks", 1);
        map.put("provider", provider == null ? "" : provider);
        map.put("bucket", bucket == null ? "" : bucket);
        map.put("state", "COMPLETED");
        map.put("message", "");
        map.put("objectKey", result.getKey() == null ? "" : result.getKey());
        map.put("createdAt", System.currentTimeMillis());
        return map;
    }

    /**
     * 上传一个分片（body 为原始分片字节；同名分片重复上传幂等覆盖）。
     *
     * @param taskId 任务号
     * @param index  分片序号（0 起）
     * @param bytes  分片内容
     * @return 任务快照（含已收分片数/总片数，供断点续传判断）
     */
    @Operation(summary = "上传分片")
    @RequestMapping(value = "/task/{taskId}/chunk", method = {RequestMethod.PUT, RequestMethod.POST})
    public ReturnResult<Map<String, Object>> chunk(
            @PathVariable("taskId") String taskId,
            @Parameter(description = "分片序号（0 起）") @RequestParam("index") int index,
            @RequestBody byte[] bytes
    ) {
        if (index < 0) {
            return ReturnResult.illegal("分片序号不合法");
        }
        try {
            UploadTaskManager.UploadTask task = uploadTaskManager.putChunk(taskId, index, bytes);
            if (task == null) {
                return ReturnResult.illegal("任务不存在或已不在接收分片");
            }
            return ReturnResult.ok(toMap(task));
        } catch (IOException e) {
            MODULE_LOG.error("分片落盘失败: id={} index={}", taskId, index, e);
            return ReturnResult.error("分片保存失败");
        }
    }

    /**
     * 分片上传完毕，触发后台合并（时间轮调度 + 虚拟线程执行）。
     *
     * @param taskId 任务号
     * @return 任务快照
     */
    @Operation(summary = "完成分片，触发合并")
    @PostMapping("/task/{taskId}/complete")
    public ReturnResult<Map<String, Object>> complete(@PathVariable("taskId") String taskId) {
        UploadTaskManager.UploadTask task = uploadTaskManager.complete(taskId);
        if (task == null) {
            return ReturnResult.illegal("任务不存在或状态不允许完成");
        }
        return ReturnResult.ok(toMap(task));
    }

    /**
     * SSE 实时推送：任务创建/分片进度/状态变化即时下发，替代前端轮询。
     *
     * <p>事件约定：</p>
     * <ul>
     *   <li>{@code init} —— 连接建立后先发一次全量任务列表（数组）；</li>
     *   <li>{@code task} —— 单个任务变化（与 {@link #tasks()} 返回结构一致）；</li>
     *   <li>{@code ping} —— 30 秒心跳，防止代理层断开空闲连接。</li>
     * </ul>
     *
     * @return SSE 流（客户端断开或超时自动清理监听器）
     */
    @Operation(summary = "任务状态实时推送（SSE）")
    @GetMapping(value = "/events", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        // 心跳：30 秒一次，注册在时间轮上（复用管理器的时间轮，不另起线程）
        java.util.function.Consumer<UploadTaskManager.UploadTask> listener = task -> {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("task").data(toMap(task)));
            } catch (Exception e) {
                // 发送失败（客户端断开）即完成连接，触发监听器清理
                emitter.complete();
            }
        };
        java.util.function.Consumer<String> pinger = ping -> {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("ping").data("ok"));
            } catch (Exception e) {
                emitter.complete();
            }
        };
        uploadTaskManager.addListener(listener);
        Runnable cleanup = () -> uploadTaskManager.removeListener(listener);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        // 连接建立：先发全量快照，再挂 30 秒心跳（句柄随连接清理一并取消）
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("init").data(uploadTaskManager.list().stream()
                            .map(UploadTaskController::toMap).toList()));
        } catch (Exception e) {
            emitter.complete();
            return emitter;
        }
        com.chua.common.support.task.timer.TimerTask pingHandle =
                uploadTaskManager.heartbeat(pinger, 30);
        Runnable cleanupAll = () -> {
            uploadTaskManager.removeListener(listener);
            uploadTaskManager.cancelHeartbeat(pingHandle);
        };
        emitter.onCompletion(cleanupAll);
        emitter.onTimeout(cleanupAll);
        emitter.onError(t -> cleanupAll.run());
        return emitter;
    }

    /**
     * 任务列表（按创建时间倒序）。
     *
     * @return 任务快照列表
     */
    @Operation(summary = "任务列表")
    @GetMapping("/tasks")
    public ReturnResult<List<Map<String, Object>>> tasks() {
        return ReturnResult.ok(uploadTaskManager.list().stream().map(UploadTaskController::toMap).toList());
    }

    /**
     * 任务详情。
     *
     * @param taskId 任务号
     * @return 任务快照
     */
    @Operation(summary = "任务详情")
    @GetMapping("/task/{taskId}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable("taskId") String taskId) {
        UploadTaskManager.UploadTask task = uploadTaskManager.get(taskId);
        if (task == null) {
            return ReturnResult.illegal("任务不存在");
        }
        return ReturnResult.ok(toMap(task));
    }

    /**
     * 取消任务（停止调度并清理分片）。
     *
     * @param taskId 任务号
     * @return 被取消的任务快照
     */
    @Operation(summary = "取消任务")
    @DeleteMapping("/task/{taskId}")
    public ReturnResult<Map<String, Object>> cancel(@PathVariable("taskId") String taskId) {
        UploadTaskManager.UploadTask task = uploadTaskManager.cancel(taskId);
        if (task == null) {
            return ReturnResult.illegal("任务不存在");
        }
        return ReturnResult.ok(toMap(task));
    }

    /**
     * 任务快照转 Map（避免为响应单独建 DTO 的样板）。
     */
    private static Map<String, Object> toMap(UploadTaskManager.UploadTask task) {
        return Map.ofEntries(
                Map.entry("taskId", task.getTaskId()),
                Map.entry("fileName", task.getFileName() == null ? "" : task.getFileName()),
                Map.entry("totalSize", task.getTotalSize()),
                Map.entry("receivedBytes", task.getReceivedBytes()),
                Map.entry("chunkSize", task.getChunkSize()),
                Map.entry("totalChunks", task.getTotalChunks()),
                Map.entry("receivedChunks", task.getReceivedChunks()),
                Map.entry("provider", task.getProvider() == null ? "" : task.getProvider()),
                Map.entry("bucket", task.getBucketDir() == null ? "" : task.getBucketDir()),
                Map.entry("state", task.getState().name()),
                Map.entry("message", task.getMessage() == null ? "" : task.getMessage()),
                Map.entry("objectKey", task.getObjectKey() == null ? "" : task.getObjectKey()),
                Map.entry("createdAt", task.getCreatedAt()));
    }

    /**
     * 创建任务请求体。
     *
     * @param fileName  文件名（必填）
     * @param totalSize 文件总字节数（0 = 未知，以 complete 为准）
     * @param chunkSize 分片大小（字节；0 = 不分片）
     * @param provider  存储提供者名（空 = 默认提供者）
     * @param bucket    存储桶/目录路径（空 = 提供者默认桶）
     */
    public record TaskCreateRequest(
            String fileName,
            Long totalSize,
            Long chunkSize,
            String provider,
            String bucket) {
    }
}
