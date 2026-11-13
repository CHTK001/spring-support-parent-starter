package com.chua.starter.ai.support.workspace;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 会话工作目录解析器。
 *
 * <p>统一 Git 与 Terminal 的目录口径：账号级工作目录为
 * {@code <system.workspaceBaseDir>/<当前账号>/agent}，会话实际执行目录为
 * {@code <账号工作目录>/<会话标识>}（不存在时回退到账号工作目录）。</p>
 *
 * <p>所有对外暴露的路径都必须落在账号工作目录内，防止 {@code ../} 越界。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacePathResolver {

    /**
     * 系统设置分组：系统
     */
    private static final String SETTING_GROUP = "system";

    /**
     * 系统设置键：工作目录基路径
     */
    private static final String SETTING_WORKSPACE_BASE = "workspaceBaseDir";

    /**
     * 账号目录默认值（认证未启用时）
     */
    private static final String DEFAULT_ACCOUNT = "public";

    /**
     * 非安全字符替换符
     */
    private static final String SAFE_SEGMENT_REPLACEMENT = "_";

    /**
     * 账号工作目录末级目录名
     */
    private static final String AGENT_DIRECTORY = "agent";

    /**
     * 账号终端会话存储目录名（与 agent 同级，避免终端日志混进被 Git 管理的工作区）
     */
    private static final String TERMINAL_DIRECTORY = "terminal";

    /**
     * Git 元数据目录名
     */
    private static final String GIT_DIRECTORY = ".git";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 解析当前登录账号的工作目录。
     *
     * @return 账号工作目录绝对路径
     */
    public Path agentWorkspace() {
        return agentWorkspace(AiUserContext.currentUserId());
    }

    /**
     * 解析指定账号的工作目录。
     *
     * @param userId 账号标识
     * @return 账号工作目录绝对路径
     */
    public Path agentWorkspace(String userId) {
        Path basePath = workspaceBase();
        String account = safeSegment(Objects.toString(userId, DEFAULT_ACCOUNT));
        Path workspace = basePath.resolve(account).resolve(AGENT_DIRECTORY).normalize();
        if (!workspace.startsWith(basePath)) {
            log.warn("[Workspace] 工作目录越界，已拒绝: {}", workspace);
            throw new ApiException(ApiErrorCode.FORBIDDEN, "工作目录越界");
        }
        // 与对话链路（AiChatFacadeServiceImpl#resolveSessionWorkspace）同口径：
        // 首次打开终端 / Git 时账号目录可能还不存在，这里统一建好，避免 PTY 因 cwd 不存在而启动失败
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            log.warn("[Workspace] 创建账号工作目录失败 path={} error={}", workspace, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建账号工作目录失败");
        }
        return workspace;
    }

    /**
     * 解析当前登录账号的终端会话存储目录。
     *
     * @return 终端会话存储目录绝对路径
     */
    public Path terminalStorage() {
        return terminalStorage(AiUserContext.currentUserId());
    }

    /**
     * 解析指定账号的终端会话存储目录。
     *
     * <p>目录与 {@code agent} 同级（{@code <基路径>/<账号>/terminal}），不放进用户工作区：
     * 工作区可能被 Git 管理或被整体打包下载，终端输出日志不应成为业务文件。</p>
     *
     * @param userId 账号标识
     * @return 终端会话存储目录绝对路径
     */
    public Path terminalStorage(String userId) {
        Path basePath = workspaceBase();
        String account = safeSegment(Objects.toString(userId, DEFAULT_ACCOUNT));
        Path directory = basePath.resolve(account).resolve(TERMINAL_DIRECTORY).normalize();
        if (!directory.startsWith(basePath)) {
            log.warn("[Workspace] 终端存储目录越界，已拒绝: {}", directory);
            throw new ApiException(ApiErrorCode.FORBIDDEN, "终端存储目录越界");
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            log.warn("[Workspace] 创建终端存储目录失败 path={} error={}", directory, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建终端存储目录失败");
        }
        return directory;
    }

    /**
     * 读取并校验工作目录基路径（系统设置 {@code system.workspaceBaseDir}）。
     *
     * @return 基路径绝对路径
     */
    private Path workspaceBase() {
        String base;
        try {
            base = globalSettingService.getValue(SETTING_GROUP, SETTING_WORKSPACE_BASE).orElse(null);
        } catch (Exception e) {
            log.warn("[Workspace] 读取工作目录基路径失败: {}", e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取工作目录配置失败");
        }
        if (base == null || base.isBlank()) {
            // 未配置时的兜底（用户口径 2026-11-13）：不把「去改 workspaceBaseDir」暴露给客户。
            // 落在进程用户家目录下（普通容器/登录节点都可写），目录结构仍是
            // <base>/<account>/agent，越界校验、并发创建等逻辑保持不变。
            Path fallback = Paths.get(System.getProperty("user.home", "."), "ai-workspace")
                    .toAbsolutePath().normalize();
            log.info("[Workspace] 未配置 workspaceBaseDir，使用默认工作区: {}", fallback);
            return fallback;
        }
        return Paths.get(base.trim()).toAbsolutePath().normalize();
    }

    /**
     * 解析会话执行目录。
     *
     * <p>优先使用 {@code <账号工作目录>/<会话标识>}，目录不存在时回退到账号工作目录。</p>
     *
     * @param sessionId 会话标识
     * @return 会话执行目录
     */
    public Path sessionWorkspace(String sessionId) {
        Path workspace = agentWorkspace();
        if (sessionId == null || sessionId.isBlank()) {
            return workspace;
        }
        Path sessionDir = resolveWithin(workspace, sessionId);
        if (Files.isDirectory(sessionDir)) {
            return sessionDir;
        }
        return workspace;
    }

    /**
     * 从会话目录向上查找 Git 仓库根目录。
     *
     * <p>只在账号工作目录范围内查找，命中 {@code .git} 即返回；未找到返回 {@code null}。</p>
     *
     * @param sessionRoot   会话执行目录
     * @param workspaceRoot 账号工作目录
     * @return Git 仓库根目录，未找到返回 {@code null}
     */
    public Path findRepositoryRoot(Path sessionRoot, Path workspaceRoot) {
        Path current = sessionRoot;
        while (current != null && current.startsWith(workspaceRoot)) {
            if (Files.exists(current.resolve(GIT_DIRECTORY))) {
                return current;
            }
            if (current.equals(workspaceRoot)) {
                break;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 把用户传入的相对路径解析到根目录下，并校验不越界。
     *
     * @param root     根目录
     * @param relative 相对路径
     * @return 绝对路径
     */
    public Path resolveWithin(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "路径不能为空");
        }
        if (relative.indexOf('\u0000') >= 0) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "路径包含非法字符");
        }
        Path candidate = Paths.get(relative.trim());
        if (candidate.isAbsolute()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "只允许使用相对路径");
        }
        Path resolved = root.resolve(candidate).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "路径越界");
        }
        return resolved;
    }

    /**
     * 清洗账号标识，确保其只能作为单个目录名使用。
     *
     * @param value 原始账号标识
     * @return 安全目录名
     */
    private String safeSegment(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9_-]", SAFE_SEGMENT_REPLACEMENT);
        if (safe.isBlank()) {
            return DEFAULT_ACCOUNT;
        }
        return safe;
    }
}
