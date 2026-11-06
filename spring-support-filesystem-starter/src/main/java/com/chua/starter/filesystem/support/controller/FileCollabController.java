package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.collab.FileCollabStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协同文档接口（{@code /v2/file/collab}）。
 *
 * <p>文件资产右键「创建协同」落库的入口：把表格文件（Univer 支持）升级为
 * 多人实时协同文档。协同语义：快照 + 版本乐观锁 + SSE 广播——客户端保存
 * 快照时携带期望版本，版本不匹配拒绝写入（防并发覆盖）；SSE 推送
 * {@code changed} 事件，其他客户端拉最新快照。</p>
 *
 * <p>用户身份由前端显式传入 {@code userId}（内部登录态功能，前端从用户
 * store 取当前用户标识）：创建者记录 {@code userId}；私有文档仅创建者与
 * 被授权用户可见可编辑。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/file/collab")
@Tag(name = "协同文档接口")
public class FileCollabController {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileCollabController.class);

    /**
     * 协同文档存储器（全局单例）
     */
    private final FileCollabStore collabStore;

    /**
     * 上传任务管理器（复用其时间轮驱动 SSE 心跳）
     */
    private final com.chua.starter.filesystem.support.task.UploadTaskManager uploadTaskManager;

    /**
     * 心跳间隔（秒）
     */
    private static final int PING_INTERVAL_SECONDS = 30;

    /**
     * 创建控制器。
     *
     * @param collabStore       协同文档存储器
     * @param uploadTaskManager 上传任务管理器（时间轮，供 SSE 心跳复用）
     */
    public FileCollabController(FileCollabStore collabStore,
                                com.chua.starter.filesystem.support.task.UploadTaskManager uploadTaskManager) {
        this.collabStore = collabStore;
        this.uploadTaskManager = uploadTaskManager;
    }

    /**
     * 创建协同文档（文件资产右键入口）。
     *
     * @param userId      当前用户标识（前端从用户 store 传入）
     * @param bucket      源文件所在桶
     * @param objectKey   源文件对象键
     * @param fileName    展示文件名（含扩展名，决定表格类型）
     * @param visibility  可见性：public / private
     * @param userIds     私有时的授权用户（逗号分隔；公开时忽略）
     * @return 新创建的协同文档快照
     */
    @Operation(summary = "创建协同文档")
    @PostMapping("/create")
    public ReturnResult<Map<String, Object>> create(
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "objectKey", required = false, defaultValue = "") String objectKey,
            @RequestParam(value = "fileName", required = false, defaultValue = "") String fileName,
            @RequestParam(value = "visibility", required = false, defaultValue = "public") String visibility,
            @RequestParam(value = "userIds", required = false, defaultValue = "") String userIds,
            @RequestParam(value = "path", required = false, defaultValue = "") String path,
            @RequestParam(value = "type", required = false, defaultValue = "") String type) {
        if (fileName == null || fileName.isBlank()) {
            return ReturnResult.illegal("fileName 不能为空");
        }
        FileCollabStore.Visibility vis = "private".equalsIgnoreCase(visibility)
                ? FileCollabStore.Visibility.PRIVATE : FileCollabStore.Visibility.PUBLIC;
        Set<String> users = parseUserIds(userIds);
        String owner = userId == null || userId.isBlank() ? "anonymous" : userId;
        FileCollabStore.FileCollab collab = collabStore.create(bucket, objectKey, fileName,
                owner, vis, users, path, type);
        MODULE_LOG.info("创建协同文档: {} owner={} visibility={} users={} path={} type={}",
                collab.id(), owner, vis, users, collab.path(), collab.type());
        return ReturnResult.ok(toSummaryMap(collab, owner));
    }

    /**
     * 列出当前用户可见的协同文档（公开全员可见；私有仅创建者与授权用户）。
     *
     * <p>支持目录过滤（{@code path}）与关键字过滤（{@code keyword}，匹配文件名
     * 或目录路径）。path 为空表示全部目录（跨目录检索）；path 非空表示只列该目录下。</p>
     *
     * @param userId   当前用户标识（前端从用户 store 传入）
     * @param path     目录过滤（空 = 全部目录；"x/y" = 仅该目录下）
     * @param keyword  关键字过滤（空 = 不过滤；匹配文件名/路径）
     * @return 协同文档列表（新→旧）
     */
    @Operation(summary = "列出协同文档")
    @GetMapping("/list")
    public ReturnResult<List<Map<String, Object>>> list(
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId,
            @RequestParam(value = "path", required = false, defaultValue = "") String path,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword) {
        String uid = userId == null || userId.isBlank() ? "anonymous" : userId;
        // 摘要（不含快照）：见 toSummaryMap 的说明 —— 列表带上内容会让打开页变成
        // 「把库里所有协同文档都下载一遍」
        return ReturnResult.ok(collabStore.list(uid, path, keyword)
                .stream().map(c -> toSummaryMap(c, uid)).toList());
    }

    /**
     * 协同文档版本号（轮询专用，响应只有几十字节）。
     *
     * <p>协同编辑器此前每 15s 调一次详情接口做「版本变了没」的检查，而详情接口
     * 每次都把整份快照传回来 —— 编辑期间等于每 15s 白下载一次全文并解析一遍，
     * 大文档上就是周期性卡顿。这里给一个只回 {@code version/updatedAt} 的轻量接口，
     * 只有版本真的变了才去取全文。</p>
     *
     * @param id 协同文档号
     * @return {@code {id, version, updatedAt}}；文档不存在返回错误
     */
    @Operation(summary = "协同文档版本号（轻量轮询）")
    @GetMapping("/{id}/version")
    public ReturnResult<Map<String, Object>> version(@PathVariable("id") String id) {
        FileCollabStore.FileCollab collab = collabStore.get(id);
        if (collab == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", collab.id());
        m.put("version", collab.version());
        m.put("updatedAt", collab.updatedAt());
        return ReturnResult.ok(m);
    }

    /**
     * 协同文档详情（含当前快照与版本）。
     *
     * @param id     协同文档号
     * @param userId 当前用户标识（用于计算 canWrite / isOwner）
     * @return 文档详情
     */
    @Operation(summary = "协同文档详情")
    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable("id") String id,
                                                    @RequestParam(value = "userId", required = false) String userId) {
        FileCollabStore.FileCollab collab = collabStore.get(id);
        if (collab == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        return ReturnResult.ok(toMap(collab, userId));
    }

    /**
     * 保存表格快照（版本乐观锁）。
     *
     * <p>请求体为 Univer 工作簿快照的 JSON 字符串。实现上<b>不使用
     * {@code @RequestBody}</b>（实测框架对 String 型 body 有 Content-Type 强校验，
     * {@code application/json} 会被拦为 C0400S0002），只从
     * {@code request.getInputStream()} 读原始字节。</p>
     *
     * <p><b>调用方必须避免 {@code application/x-www-form-urlencoded}。</b>
     * Servlet 容器在该类型下会先把 body 当表单参数消费掉，
     * 此时 {@code getInputStream()} 读到的是空串 —— 表现为「保存成功、版本 +1，
     * 但快照内容为空」，刷新后表格全丢。实测同一份快照：
     * {@code application/json} / {@code text/plain} / 不带 Content-Type 均正常，
     * 唯独 form-urlencoded 会存成空。前端 {@code saveFileCollabSnapshot}
     * 已显式声明 {@code text/plain} 规避。</p>
     *
     * @param id               协同文档号
     * @param userId           当前用户标识（写权限校验）
     * @param expectedVersion  期望的当前版本
     * @param request          HTTP 请求（读原始 body 为快照 JSON）
     * @return 更新后的文档；版本冲突返回错误提示
     */
    @Operation(summary = "保存协同快照（版本乐观锁）")
    @PutMapping("/{id}/snapshot")
    public ReturnResult<Map<String, Object>> saveSnapshot(
            @PathVariable("id") String id,
            @RequestParam(value = "userId", required = false, defaultValue = "") String userId,
            @RequestParam(value = "expectedVersion", required = false, defaultValue = "0") long expectedVersion,
            jakarta.servlet.http.HttpServletRequest request) {
        FileCollabStore.FileCollab exist = collabStore.get(id);
        if (exist == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        // 写权限先于读 body：无权限不该白白消费一次请求体，也避免半途失败留下歧义
        if (!FileCollabStore.canWrite(exist, userId)) {
            return ReturnResult.illegal("没有该协同文档的编辑权限，请联系创建者授权");
        }
        String body;
        try (java.io.InputStream in = request.getInputStream()) {
            body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return ReturnResult.illegal("读取快照内容失败");
        }
        FileCollabStore.FileCollab updated = collabStore.saveSnapshot(id, body, expectedVersion);
        if (updated == null) {
            FileCollabStore.FileCollab current = collabStore.get(id);
            if (current == null) {
                return ReturnResult.illegal("协同文档不存在");
            }
            // 冲突也算一次保存尝试（仅 MODULE_LOG 记录，不再写入文件操作日志）
            return ReturnResult.error("版本冲突：本地快照已过期，请刷新后重试（当前版本 " + current.version() + "）");
        }
        return ReturnResult.ok(toSummaryMap(updated, userId));
    }

    /**
     * 更新可见性与授权用户（仅创建者可改）。
     *
     * <p>与终端共享一致：被授权用户能编辑内容，但不能反向改授权名单，
     * 否则一次授权就能扩散成「自己给自己加人」。</p>
     *
     * @param id         协同文档号
     * @param userId     操作者用户标识
     * @param visibility 可见性：public / private
     * @param userIds    私有时的授权用户（逗号分隔；公开时忽略）
     * @return 更新后的文档
     */
    @Operation(summary = "更新协同可见性与授权用户")
    @PutMapping("/{id}/access")
    public ReturnResult<Map<String, Object>> updateAccess(
            @PathVariable("id") String id,
            @RequestParam(value = "userId", required = false, defaultValue = "") String userId,
            @RequestParam(value = "visibility", required = false, defaultValue = "public") String visibility,
            @RequestParam(value = "userIds", required = false, defaultValue = "") String userIds) {
        FileCollabStore.FileCollab exist = collabStore.get(id);
        if (exist == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        if (!FileCollabStore.isOwner(exist, userId)) {
            return ReturnResult.illegal("只有创建者可以修改协同权限");
        }
        FileCollabStore.Visibility vis = "private".equalsIgnoreCase(visibility)
                ? FileCollabStore.Visibility.PRIVATE : FileCollabStore.Visibility.PUBLIC;
        Set<String> users = parseUserIds(userIds);
        FileCollabStore.FileCollab updated = collabStore.updateAccess(id, vis, users);
        if (updated == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        return ReturnResult.ok(toSummaryMap(updated, userId));
    }

    /**
     * 删除协同文档（仅创建者可删）。
     *
     * @param id     协同文档号
     * @param userId 操作者用户标识
     * @return 删除结果
     */
    @Operation(summary = "删除协同文档")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable("id") String id,
                                        @RequestParam(value = "userId", required = false, defaultValue = "") String userId) {
        FileCollabStore.FileCollab exist = collabStore.get(id);
        if (exist == null) {
            return ReturnResult.ok(false);
        }
        if (!FileCollabStore.isOwner(exist, userId)) {
            return ReturnResult.illegal("只有创建者可以删除该协同文档");
        }
        return ReturnResult.ok(collabStore.delete(id));
    }

    /**
     * 重命名协同文档（可写即可改名）。
     *
     * @param id       协同文档号
     * @param userId   当前用户标识
     * @param fileName 新展示文件名（含扩展名）
     * @return 更新后的文档
     */
    @Operation(summary = "重命名协同文档")
    @PutMapping("/{id}/rename")
    public ReturnResult<Map<String, Object>> rename(
            @PathVariable("id") String id,
            @RequestParam(value = "userId", required = false, defaultValue = "") String userId,
            @RequestParam(value = "fileName", required = false, defaultValue = "") String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ReturnResult.illegal("fileName 不能为空");
        }
        FileCollabStore.FileCollab exist = collabStore.get(id);
        if (exist == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        if (!FileCollabStore.canWrite(exist, userId)) {
            return ReturnResult.illegal("没有该协同文档的编辑权限，请联系创建者授权");
        }
        FileCollabStore.FileCollab updated = collabStore.rename(id, fileName);
        if (updated == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        return ReturnResult.ok(toSummaryMap(updated, userId));
    }

    /**
     * 复制协同文档（新文档，文件名追加「 副本」）。
     *
     * @param id     源协同文档号
     * @param userId 新文档创建者
     * @return 新文档
     */
    @Operation(summary = "复制协同文档")
    @PostMapping("/{id}/copy")
    public ReturnResult<Map<String, Object>> copy(
            @PathVariable("id") String id,
            @RequestParam(value = "userId", required = false, defaultValue = "anonymous") String userId) {
        FileCollabStore.FileCollab exist = collabStore.get(id);
        if (exist == null) {
            return ReturnResult.illegal("源协同文档不存在");
        }
        String uid = userId == null || userId.isBlank() ? "anonymous" : userId;
        // 复制要读到源内容，按写权限判定（与终端共享 canWrite 同口径）
        if (!FileCollabStore.canWrite(exist, uid)) {
            return ReturnResult.illegal("没有该协同文档的编辑权限，请联系创建者授权");
        }
        FileCollabStore.FileCollab copy = collabStore.duplicate(id, uid);
        if (copy == null) {
            return ReturnResult.illegal("源协同文档不存在");
        }
        return ReturnResult.ok(toSummaryMap(copy, uid));
    }

    /**
     * 协同文档历史记录（版本快照列表，新→旧）。
     *
     * @param id 协同文档号
     * @return 历史条目列表：{version, updatedAt, size, preview}
     */
    @Operation(summary = "协同文档历史记录")
    @GetMapping("/{id}/history")
    public ReturnResult<List<Map<String, Object>>> history(@PathVariable("id") String id) {
        List<FileCollabStore.SnapshotEntry> entries = collabStore.history(id);
        if (entries == null) {
            return ReturnResult.illegal("协同文档不存在");
        }
        return ReturnResult.ok(entries.stream()
                .map(FileCollabController::toHistoryMap).toList());
    }

    /**
     * 取指定版本的历史快照（管理员「回退」= 复制该版本出来成为一个新文件）。
     *
     * @param id      协同文档号
     * @param version 版本号
     * @return 该版本快照内容
     */
    @Operation(summary = "取指定版本的历史快照")
    @GetMapping("/{id}/history/{version}")
    public ReturnResult<Map<String, Object>> historyVersion(
            @PathVariable("id") String id,
            @PathVariable("version") long version) {
        FileCollabStore.SnapshotEntry entry = collabStore.versionAt(id, version);
        if (entry == null) {
            return ReturnResult.illegal("版本不存在");
        }
        return ReturnResult.ok(toHistoryMap(entry));
    }

    /**
     * 历史条目转 Map（含快照内容，供回退复制用）。
     *
     * @param entry 历史条目
     * @return Map 快照
     */
    private static Map<String, Object> toHistoryMap(FileCollabStore.SnapshotEntry entry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", entry.version());
        m.put("updatedAt", entry.updatedAt());
        m.put("snapshot", entry.snapshot() == null ? "" : entry.snapshot());
        m.put("size", bytesOf(entry.snapshot()));
        return m;
    }

    /**
     * 快照 UTF-8 字节数（文件大小展示用）。
     *
     * @param snapshot 快照
     * @return 字节数
     */
    private static long bytesOf(String snapshot) {
        if (snapshot == null) {
            return 0;
        }
        return snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * SSE 实时推送：协同文档变更（保存快照 / 权限变更）即时下发。
     *
     * <p>事件约定：</p>
     * <ul>
     *   <li>{@code init} —— 连接建立后先发一次文档当前版本；</li>
     *   <li>{@code changed} —— 文档更新（携带最新版本号，客户端据此拉快照）；</li>
     *   <li>{@code ping} —— 30 秒心跳，防止代理层断开空闲连接。</li>
     * </ul>
     *
     * <p>实现说明：本协同为轻量级（快照式），SSE 只做「变更通知」，
     * 不承载编辑操作本身——客户端收到 {@code changed} 后拉最新快照重放。</p>
     *
     * <p><b>变更通知从哪来</b>：{@link FileCollabStore} 在每次写入成功后广播
     * {@link FileCollabStore.CollabChange}，本端点把它转成 SSE 事件推给订阅者。
     * 订阅时按 id 过滤：同一条流只关心自己打开的那个文档。</p>
     *
     * @param id 协同文档号
     * @return SSE 流
     */
    @Operation(summary = "协同变更实时推送（SSE）")
    @GetMapping(value = "/{id}/events", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(
            @PathVariable("id") String id) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        // 心跳：30 秒一次，复用时间轮（UploadTaskManager 的 heartbeat 通道），
        // 不另建调度线程；连接关闭时取消句柄。
        java.util.function.Consumer<String> pinger = ping -> {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("ping").data("ok"));
            } catch (Exception e) {
                emitter.complete();
            }
        };
        com.chua.common.support.task.timer.TimerTask pingHandle =
                uploadTaskManager.heartbeat(pinger, PING_INTERVAL_SECONDS);
        // 变更通知：存储层写入成功后回调这里，转成 changed 事件
        java.util.function.Consumer<FileCollabStore.CollabChange> changeListener = change -> {
            if (change == null || !id.equals(change.id())) {
                return;
            }
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", change.id());
                payload.put("version", change.version());
                payload.put("updatedAt", change.updatedAt());
                payload.put("kind", change.kind());
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("changed").data(payload));
            } catch (Exception e) {
                // 连接已断：让 emitter 走完，onCompletion 会清理监听器
                emitter.complete();
            }
        };
        collabStore.addListener(changeListener);
        Runnable cleanup = () -> {
            try {
                uploadTaskManager.cancelHeartbeat(pingHandle);
            } catch (Exception ignored) {
                // 取消句柄失败可忽略
            }
            try {
                collabStore.removeListener(changeListener);
            } catch (Exception ignored) {
                // 移除监听器失败可忽略
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        try {
            FileCollabStore.FileCollab collab = collabStore.get(id);
            Map<String, Object> init = new LinkedHashMap<>();
            init.put("id", id);
            init.put("version", collab == null ? 0 : collab.version());
            init.put("updatedAt", collab == null ? 0 : collab.updatedAt());
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("init").data(init));
        } catch (Exception e) {
            emitter.complete();
            cleanup.run();
        }
        return emitter;
    }

    /**
     * 解析逗号分隔的用户 id 集合。
     *
     * @param userIds 原始字符串（可为空）
     * @return 去空去重的用户 id 集合
     */
    private static Set<String> parseUserIds(String userIds) {
        if (userIds == null || userIds.isBlank()) {
            return new java.util.LinkedHashSet<>();
        }
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String item : userIds.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 协同文档转 Map（前端统一结构）。
     *
     * <p>额外下发 {@code canWrite} / {@code isOwner}：字段口径与终端共享
     * {@code TerminalShareController#toMap} 完全一致，前端据此决定「权限 /
     * 重命名 / 删除」等入口是否可用，而不是自己再猜一遍规则。</p>
     *
     * @param collab 协同文档
     * @param userId 当前用户标识（用于计算权限位；null/空 = 匿名）
     * @return Map 快照
     */
    private static Map<String, Object> toMap(FileCollabStore.FileCollab collab, String userId) {
        Map<String, Object> m = toSummaryMap(collab, userId);
        m.put("snapshot", collab.snapshot() == null ? "" : collab.snapshot());
        return m;
    }

    /**
     * 协同文档转「摘要 Map」（<b>不含快照</b>）。
     *
     * <p><b>为什么列表必须用摘要。</b>此前 {@code /list} 用的是含快照的完整结构，
     * 于是打开协同管理页要把**每一个**协同文档的整份内容都传下来：一个 Univer 工作簿
     * 快照动辄几百 KB，十几个文档就是几 MB 的 JSON —— 打开慢、内存高、Vue 还要接管
     * 这些大字符串。列表本身只需要元信息（名称/时间/版本/大小/权限），
     * 内容由「打开/预览/下载」时按 id 单独取（{@code GET /v1/file-collab/{id}}）。</p>
     *
     * <p>同理，{@code create/save/rename/access/copy} 的响应也走摘要：调用方只用
     * {@code id}/{@code version}，把刚存上去的快照再回传一遍等于把保存流量翻倍。</p>
     *
     * @param collab 协同文档
     * @param userId 当前用户标识（用于计算权限位；null/空 = 匿名）
     * @return Map 摘要（含 size，不含 snapshot）
     */
    private static Map<String, Object> toSummaryMap(FileCollabStore.FileCollab collab, String userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", collab.id());
        m.put("bucket", collab.bucket());
        m.put("objectKey", collab.objectKey());
        m.put("fileName", collab.fileName());
        m.put("owner", collab.owner());
        m.put("visibility", collab.visibility().name());
        m.put("allowedUserIds", collab.allowedUserIds());
        m.put("createdAt", collab.createdAt());
        m.put("updatedAt", collab.updatedAt());
        m.put("version", collab.version());
        m.put("path", collab.path());
        m.put("type", collab.type());
        // 文件大小：快照 UTF-8 字节数（卡片展示用；不需要把内容传下去）
        m.put("size", bytesOf(collab.snapshot()));
        m.put("canWrite", FileCollabStore.canWrite(collab, userId));
        m.put("isOwner", FileCollabStore.isOwner(collab, userId));
        return m;
    }
}