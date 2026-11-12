package com.chua.starter.ai.support.workspace;

import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

/**
 * 会话自定义工作目录（项目目录）解析器。
 *
 * <p>会话的 {@code sys_ai_session.settings.workspacePath} 是「这个会话在哪个项目里干活」的
 * 唯一来源。聊天工作间、终端 cwd、Git 仓库根都从这里取，避免三处各拼一套路径。</p>
 *
 * <p><b>可写范围</b>只允许两个位置：当前账号自己的
 * {@code <基路径>/<账号>/agent} 子树，以及共享的 {@code <基路径>/public} 子树。
 * 其它路径（含 Electron 里选的任意外部目录）一律视为未设置，会话回落到账号工作目录——
 * 接口是网络入口，不能因为客户端传了绝对路径就把智能体的工作间放到主机任意位置。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionWorkspaceResolver {

    /**
     * 会话自定义工作目录在 settings JSON 里的键名
     */
    public static final String WORKSPACE_PATH_KEY = "workspacePath";

    /**
     * 共享公共目录的目录名（与前端「使用公共目录」一致）
     */
    private static final String PUBLIC_DIRECTORY = "public";

    /**
     * 会话表
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 账号工作目录解析器
     */
    private final WorkspacePathResolver pathResolver;

    /**
     * settings JSON 解析
     */
    private final ObjectMapper objectMapper;

    /**
     * 校验并归一化一个候选工作目录。
     *
     * @param rawPath 客户端提交的绝对路径；空白或 {@code "null"} 视为未设置
     * @return 归一化后的绝对路径；不在允许范围内、不存在或不是目录时返回 {@code null}
     */
    public String normalize(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String text = rawPath.trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        Path candidate;
        try {
            candidate = Paths.get(text).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.info("[Workspace] 工作目录不是合法路径，已忽略: {}", text);
            return null;
        }
        Path agent;
        try {
            agent = pathResolver.agentWorkspace();
        } catch (Exception e) {
            log.warn("[Workspace] 读取账号工作目录失败，忽略本次工作目录设置: {}", e.getMessage());
            return null;
        }
        if (!isAllowed(candidate, agent)) {
            log.info("[Workspace] 工作目录不在允许范围内，已忽略: {}", candidate);
            return null;
        }
        if (!Files.isDirectory(candidate)) {
            log.info("[Workspace] 工作目录不存在或不是目录，已忽略: {}", candidate);
            return null;
        }
        return candidate.toString();
    }

    /**
     * 取会话绑定的项目目录。
     *
     * @param sessionId 会话标识（数字主键；解析不到时返回 {@code null}）
     * @return 项目目录；未绑定或已失效时返回 {@code null}
     */
    public Path projectRoot(String sessionId) {
        Map<String, Object> settings = readSessionSettings(sessionId);
        if (settings == null) {
            return null;
        }
        Object raw = settings.get(WORKSPACE_PATH_KEY);
        if (raw == null) {
            return null;
        }
        String normalized = normalize(Objects.toString(raw, null));
        return normalized == null ? null : Paths.get(normalized);
    }

    /**
     * 取会话的实际执行目录。
     *
     * <p>与 {@link WorkspacePathResolver#sessionWorkspace(String)} 同语义，
     * 只是把根从账号工作目录换成项目目录：项目下已有会话目录就用
     * {@code <项目>/<会话标识>}，否则回退到项目根。</p>
     *
     * @param sessionId 会话标识
     * @return 执行目录（项目优先，没有项目时沿用账号工作目录口径）
     */
    public Path sessionRoot(String sessionId) {
        Path project = projectRoot(sessionId);
        if (project == null) {
            return pathResolver.sessionWorkspace(sessionId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            Path sessionDir = project.resolve(sessionId).normalize();
            if (sessionDir.startsWith(project) && Files.isDirectory(sessionDir)) {
                return sessionDir;
            }
        }
        return project;
    }

    /**
     * 判断候选路径是否落在允许的工作区范围内。
     *
     * @param candidate 候选绝对路径
     * @param agent     当前账号工作目录
     * @return 是否允许
     */
    private boolean isAllowed(Path candidate, Path agent) {
        // 账号自己的 agent 子树（不含 agent 根本身：根就是「未指定项目」）
        if (candidate.startsWith(agent) && !candidate.equals(agent)) {
            return true;
        }
        Path base = agent.getParent() == null ? null : agent.getParent().getParent();
        if (base == null) {
            return false;
        }
        Path publicDir = base.resolve(PUBLIC_DIRECTORY).normalize();
        return candidate.startsWith(publicDir);
    }

    /**
     * 读会话 settings；会话不存在、不属于当前账号或解析失败时返回 {@code null}。
     *
     * @param sessionId 会话标识
     * @return settings 键值对
     */
    private Map<String, Object> readSessionSettings(String sessionId) {
        SysAiSession session = loadSession(sessionId);
        if (session == null || session.getSettings() == null || session.getSettings().isBlank()) {
            return null;
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(session.getSettings(), Map.class);
            if (parsed == null) {
                return null;
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            parsed.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        } catch (Exception e) {
            log.warn("[Workspace] 解析会话 settings 失败 sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 按会话标识加载会话，并校验归属。
     *
     * @param sessionId 会话标识
     * @return 会话；标识不可解析、不存在或不属于当前账号时返回 {@code null}
     */
    private SysAiSession loadSession(String sessionId) {
        if (sessionId == null || !sessionId.trim().matches("\\d+")) {
            return null;
        }
        try {
            SysAiSession session = sessionMapper.selectById(Long.parseLong(sessionId.trim()));
            if (session == null) {
                return null;
            }
            String currentUserId = AiUserContext.currentUserId();
            if (currentUserId != null && !currentUserId.equals(session.getSysUserId())) {
                // 只认归属人：跨账号读别人的项目路径没有意义，也不能让自己落到别人的目录里
                log.info("[Workspace] 会话不属于当前账号，忽略其工作目录 sessionId={}", sessionId);
                return null;
            }
            return session;
        } catch (Exception e) {
            log.warn("[Workspace] 加载会话失败 sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }
}
