// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 会话工作目录的**文件读取**端点（文本 / 原始字节）。
 *
 * <p>背景（2026-10-08）：前端文本预览走 {@code /v2/sessions/{id}/workspace/file}、
 * 图片预览走 {@code .../workspace/raw-file}，但后端只实现了 {@code /workspace/tree} ——
 * 这两个端点恒 404，所以「文本文件无法预览」（用户报障 2.1）。</p>
 *
 * <p>单独成一个控制器：同样的方法原先加在 {@code AiSessionController} 里，部署后
 * 运行时却始终 404（同类的 tree 正常），怀疑旧类/缓存阴影，故独立成类规避。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class AiWorkspaceFileController {

    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 会话执行目录解析器：会话绑定了项目目录时，工具 CWD 在 {@code <项目>/<会话id>}
     */
    private final com.chua.starter.ai.support.workspace.SessionWorkspaceResolver sessionWorkspaceResolver;

    /**
     * 工作目录路径解析器（统一 {@code <基路径>/<账号>} 口径与空配置兜底
     * {@code ~/ai-workspace}；此前本类与 {@code WorkspacePathResolver} 各算各的，
     * 空配置一处返回 null 一处兜底，导致工作区路径不一致）
     */
    private final com.chua.starter.ai.support.workspace.WorkspacePathResolver workspacePathResolver;

    /**
     * 在会话工作区内解析路径。
     *
     * <p><b>为什么要试两层</b>：agent 报出来的相对路径，是相对**它自己的工作目录**
     * {@code <base>/<账号>/<会话id>} 的（工具 CWD 就在那里，例如它写的是
     * {@code output/pelican_bike.svg}，实际落在 {@code .../<会话id>/output/pelican_bike.svg}）。
     * 而本接口的工作区根取的是 {@code <base>/<账号>}，少了一层会话 id ——
     * 直接拼就必然 404：文件明明在，点开却"加载失败"。</p>
     *
     * <p>所以先按原样解析，不存在再补一层会话 id 试一次。两条路径**都过同一道围栏**
     * （解析后必须仍在工作区内，否则一律拒绝），不会因为多试一次而放开越权。</p>
     *
     * @param root  会话工作区根
     * @param id    会话标识
     * @param path  请求里的路径（相对或绝对）
     * @return 解析后的目标路径；越界时返回 null（由调用方决定 403 还是 404）
     */
    private Path resolveInWorkspace(Path root, String id, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path direct = root.resolve(path).normalize();
        if (direct.startsWith(root) && Files.exists(direct)) {
            return direct;
        }
        Path perSession = root.resolve(id).resolve(path).normalize();
        if (perSession.startsWith(root) && Files.exists(perSession)) {
            return perSession;
        }
        // 都不存在：把"越界"和"不存在"区分开，交给调用方给出准确状态码
        return direct.startsWith(root) ? direct : null;
    }

    /**
     * 读取工作目录内的文本文件（前端文本预览）。
     *
     * @param id   会话标识（仅用于路由，工作目录按账号解析，与 tree 同口径）
     * @param path 相对工作目录的路径，或工作目录内的绝对路径
     * @return {content, extension, name, size}
     */
    @GetMapping("/{id}/workspace/file")
    public ReturnResult<Map<String, Object>> workspaceFile(@PathVariable("id") String id,
                                                          @RequestParam("path") String path) {
        Path root = resolveWorkspaceRoot(id);
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "工作目录未配置");
        }
        Path target = resolveInWorkspace(root, id, path);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "路径越界");
        }
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "文件不存在（已按工作区根与会话子目录两种口径查找）");
        }
        try {
            long size = Files.size(target);
            if (size > 2L * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件过大（上限 2MB）");
            }
            String name = target.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot) : "";
            Map<String, Object> data = new LinkedHashMap<>(4);
            data.put("content", Files.readString(target, StandardCharsets.UTF_8));
            data.put("extension", ext);
            data.put("name", name);
            data.put("size", size);
            return ReturnResult.ok(data);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取失败: " + e.getMessage());
        }
    }

    /**
     * 读取工作目录内的原始字节（图片等二进制预览）。
     *
     * @param id   会话标识
     * @param path 相对/绝对路径（越界与不存在返回 404）
     * @return 原始字节 + 按后缀推断的 Content-Type
     */
    @GetMapping("/{id}/workspace/raw-file")
    public ResponseEntity<byte[]> workspaceRawFile(@PathVariable("id") String id,
                                                   @RequestParam("path") String path) {
        Path root = resolveWorkspaceRoot(id);
        if (root == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Path target = resolveInWorkspace(root, id, path);
        if (target == null || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            String lower = target.getFileName().toString().toLowerCase();
            String ct = lower.endsWith(".png") ? "image/png"
                    : (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ? "image/jpeg"
                    : lower.endsWith(".gif") ? "image/gif"
                    : lower.endsWith(".webp") ? "image/webp"
                    : lower.endsWith(".svg") ? "image/svg+xml"
                    : "application/octet-stream";
            return ResponseEntity.ok().header("Content-Type", ct).body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 列出工作区内某个目录的直接子节点（前端侧栏按需展开目录）。
     *
     * <p>背景（2026-11-17 验收）：前端 {@code WorkspaceSidebar.loadChildren} 一直在调
     * {@code /workspace/children}，后端此前只有 {@code /workspace/tree}，请求恒 404，
     * 只能靠 tree 兜底——新建/重命名后的目录展开不出来。</p>
     *
     * <p>{@code path} 既接受相对工作区根的路径，也接受 {@code /workspace/tree} 返回的绝对路径；
     * 解析后必须仍在该会话工作区根内，否则 403。</p>
     *
     * @param id   会话标识
     * @param path 目录路径（缺省或 {@code .} 表示工作区根）
     * @return {@code {children: [{name,path,isDirectory,size?,children:[]}]}}
     */
    @GetMapping("/{id}/workspace/children")
    public ReturnResult<Map<String, Object>> workspaceChildren(@PathVariable("id") String id,
                                                               @RequestParam(value = "path", required = false) String path) {
        Path root = resolveWorkspaceRoot(id);
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "工作目录未配置");
        }
        Path dir = (path == null || path.isBlank() || ".".equals(path.trim()))
                ? root : resolveInWorkspace(root, id, path);
        if (dir == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "路径越界");
        }
        if (!Files.isDirectory(dir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目录不存在");
        }
        List<Map<String, Object>> children = listChildren(dir);
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("path", dir.toString());
        result.put("children", children);
        return ReturnResult.ok(result);
    }

    /**
     * 重命名工作区内的文件或目录（前端侧栏右键「重命名」）。
     *
     * <p>只允许改同目录下的名字：{@code newName} 不接受路径分隔符与 {@code .}/{@code ..}，
     * 目标路径解析后仍须落在工作区根内，已存在同名则 409，避免静默覆盖。</p>
     *
     * @param id   会话标识
     * @param body {@code {path, newName}}
     * @return {@code {success, isDirectory, newPath}}
     */
    @PostMapping("/{id}/workspace/rename")
    public ReturnResult<Map<String, Object>> workspaceRename(@PathVariable("id") String id,
                                                             @RequestBody Map<String, Object> body) {
        Path root = resolveWorkspaceRoot(id);
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "工作目录未配置");
        }
        String path = body == null ? null : Objects.toString(body.get("path"), null);
        String newName = body == null ? null : Objects.toString(body.get("newName"), null);
        if (path == null || path.isBlank() || newName == null || newName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path 与 newName 不能为空");
        }
        String name = newName.trim();
        if (name.contains("/") || name.contains("\\") || ".".equals(name) || "..".equals(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能包含路径分隔符");
        }
        Path source = resolveInWorkspace(root, id, path);
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "路径越界");
        }
        if (!Files.exists(source)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        if (source.equals(root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能重命名工作区根目录");
        }
        Path target = source.resolveSibling(name).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "路径越界");
        }
        if (Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同名文件已存在");
        }
        boolean directory = Files.isDirectory(source);
        try {
            Files.move(source, target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "重命名失败: " + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("success", true);
        data.put("isDirectory", directory);
        data.put("newPath", target.toString());
        return ReturnResult.ok(data);
    }

    /**
     * 删除工作区内的文件或目录（前端侧栏右键「删除」，已在确认框二次确认）。
     *
     * <p>目录递归删除；工作区根自身不允许删除。</p>
     *
     * @param id   会话标识
     * @param path 相对/绝对路径（须在工作区内）
     * @return {@code {success, isDirectory}}
     */
    @DeleteMapping("/{id}/workspace/file")
    public ReturnResult<Map<String, Object>> workspaceDeleteFile(@PathVariable("id") String id,
                                                                 @RequestParam("path") String path) {
        Path root = resolveWorkspaceRoot(id);
        if (root == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "工作目录未配置");
        }
        Path target = resolveInWorkspace(root, id, path);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "路径越界");
        }
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        if (target.equals(root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能删除工作区根目录");
        }
        boolean directory = Files.isDirectory(target);
        try {
            FileSystemUtils.deleteRecursively(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除失败: " + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("success", true);
        data.put("isDirectory", directory);
        return ReturnResult.ok(data);
    }

    /**
     * HTML 预览的资源基址：按路径返回工作区内的静态文件。
     *
     * <p>前端把 {@code <base href>} 指向本端点，相对资源（css/js/图片）由此加载。
     * 注意前端传的是**被预览文件的完整路径**（如 {@code .../html-preview/src/page/index.html/}），
     * 相对资源会落到该文件之下；这里在直接解析失败时，回溯路径中最深的“文件”段，
     * 用它所在目录续接剩余段，兼容这种基址形态。</p>
     *
     * @param id      会话标识
     * @param request 用于取 {@code /html-preview/} 之后的相对路径
     * @return 文件字节（按后缀推断 Content-Type）；找不到返回 404
     */
    @GetMapping("/{id}/workspace/html-preview/**")
    public ResponseEntity<byte[]> workspaceHtmlPreview(@PathVariable("id") String id,
                                                       HttpServletRequest request) {
        Path root = resolveWorkspaceRoot(id);
        if (root == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        String uri = request.getRequestURI();
        int marker = uri.indexOf("/html-preview/");
        String relative = marker < 0 ? "" : uri.substring(marker + "/html-preview/".length());
        Path target = resolveHtmlResource(root, id, relative);
        if (target == null || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok()
                    .header("Content-Type", contentTypeOf(target))
                    .body(Files.readAllBytes(target));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 解析 HTML 预览请求的实际文件。
     *
     * <p><b>为什么不是简单拼路径</b>：前端把 {@code <base href>} 设成
     * {@code /workspace/html-preview/<被预览文件路径>/}（见
     * {@code workspace.api.ts#getWorkspaceHtmlPreviewBaseUrl}），而侧栏节点的
     * {@code path} 是**绝对路径**（{@code C:\...\admin\proj\index.html}）。
     * 于是相对资源 {@code styles.css} 会请求
     * {@code /html-preview/C:/.../proj/index.html/styles.css} —— 多了一层文件名。</p>
     *
     * <p>本方法先把 URI 段解码，按「绝对 / 相对」两种口径落到工作区根下的路径；
     * 若该路径不是文件，再沿它的祖先向上找最深的**已存在文件**，把它当目录基址，
     * 用剩余段重新拼接。所有结果都必须仍在工作区根内。</p>
     *
     * @param root     工作区根
     * @param id       会话标识
     * @param relative {@code /html-preview/} 之后的路径（可能带 URL 编码）
     * @return 目标文件；越界或不存在返回 null
     */
    private Path resolveHtmlResource(Path root, String id, String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String rel;
        try {
            rel = java.net.URLDecoder.decode(relative, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            rel = relative;
        }
        rel = rel.replace('\\', '/');
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        if (rel.isBlank()) {
            return null;
        }
        Path target;
        try {
            Path parsed = Paths.get(rel);
            if (parsed.isAbsolute()) {
                target = parsed.normalize();
            } else {
                Path direct = resolveInWorkspace(root, id, rel);
                target = direct == null ? root.resolve(rel).normalize() : direct;
            }
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
        if (!target.startsWith(root)) {
            return null;
        }
        if (Files.isRegularFile(target)) {
            return target;
        }
        Path probe = target;
        while (probe != null && probe.startsWith(root) && !probe.equals(root)) {
            if (Files.isRegularFile(probe)) {
                Path base = probe.getParent();
                Path rest = probe.relativize(target);
                if (base == null || rest.toString().isEmpty()) {
                    return null;
                }
                Path resolved = base.resolve(rest).normalize();
                return resolved.startsWith(root) && Files.isRegularFile(resolved) ? resolved : null;
            }
            probe = probe.getParent();
        }
        return null;
    }

    /**
     * 列出目录的一层子节点（目录节点带空 children，供前端继续按需展开）。
     *
     * @param dir 目录
     * @return 节点数组；不可读时返回空数组
     */
    private List<Map<String, Object>> listChildren(Path dir) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> sorted = stream
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))
                    .limit(500)
                    .toList();
            for (Path child : sorted) {
                Map<String, Object> node = new LinkedHashMap<>(4);
                node.put("name", child.getFileName().toString());
                node.put("path", child.toString());
                boolean directory = Files.isDirectory(child);
                node.put("isDirectory", directory);
                if (directory) {
                    node.put("children", new ArrayList<>());
                } else {
                    try {
                        node.put("size", Files.size(child));
                    } catch (IOException ignored) {
                        node.put("size", 0L);
                    }
                }
                nodes.add(node);
            }
        } catch (IOException e) {
            log.warn("[Workspace] 列目录失败 {}: {}", dir, e.getMessage());
        }
        return nodes;
    }

    /**
     * 按后缀推断 Content-Type（HTML 预览用）。
     *
     * @param target 文件
     * @return MIME 类型
     */
    private String contentTypeOf(Path target) {
        String lower = target.getFileName().toString().toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return "text/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json;charset=UTF-8";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html;charset=UTF-8";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }

    /**
     * 解析当前账号的工作目录（{@code <基路径>/<账号标识>}），口径与
     * {@code AiSessionController#workspaceTree} 完全一致。
     *
     * @return 工作目录；未配置或越界返回 null
     */
    private Path resolveAgentWorkspace() {
        // 统一口径：直接复用 WorkspacePathResolver（<基路径>/<账号>，
        // 空配置兜底 ~/ai-workspace，越界/建目录失败抛 ApiException 而非静默返回 null）。
        return workspacePathResolver.agentWorkspace();
    }

    /**
     * 解析会话的工作区根：绑定了项目目录就用项目，否则回退**会话归属人**的账号工作目录。
     *
     * <p>为什么不是「当前登录账号」：HTML 预览里 {@code <base href>} 指向本服务的
     * 相对资源请求由浏览器直接发出，带不上鉴权头（该端点按设计不放令牌进 URL），
     * 匿名上下文解析账号会得到 {@code public}，资源全部 404。会话 id 本身即定位依据，
     * 归属账号从会话记录取。</p>
     *
     * @param sessionId 会话标识（数字主键字符串）
     * @return 工作区根；基路径未配置时返回 null
     */
    private Path resolveWorkspaceRoot(String sessionId) {
        Path project = sessionWorkspaceResolver.projectRoot(sessionId);
        if (project != null) {
            return project;
        }
        String owner = sessionWorkspaceResolver.ownerUserId(sessionId);
        return owner == null ? resolveAgentWorkspace() : workspacePathResolver.agentWorkspace(owner);
    }
}