package com.chua.starter.ai.support.workspace.terminal;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.SysAiTerminalSession;
import com.chua.starter.ai.support.mapper.SysAiTerminalSessionMapper;
import com.chua.starter.ai.support.workspace.WorkspacePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 终端会话持久化存储：元信息入库 + 输出日志落账号目录。
 *
 * <p>存储口径：</p>
 * <ul>
 *   <li><b>元信息</b>：表 {@code sys_ai_terminal_session}，按
 *       {@code (sys_user_id, chat_session_id)} 唯一，重开终端只更新不新增；</li>
 *   <li><b>输出日志</b>：{@code <workspaceBaseDir>/<账号>/terminal/<hash>.log}，
 *       文件名取 {@code sha256(userId|chatSessionId)} 前 32 位，避免会话标识里的
 *       非法文件名字符与路径长度问题；</li>
 *   <li><b>账号隔离</b>：日志目录按账号划分，且账号标识经 {@link WorkspacePathResolver}
 *       清洗并校验不越界，与业务数据「只按 userId 隔离」的口径一致。</li>
 * </ul>
 *
 * <p>任何磁盘/数据库异常都只记录告警并降级（元信息缺失、日志仅内存），
 * 不阻断终端本身可用。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalSessionStore {

    /**
     * 会话状态：运行中
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * 会话状态：进程已退出
     */
    public static final String STATUS_EXITED = "EXITED";

    /**
     * 会话状态：进程已终止或会话被回收
     */
    public static final String STATUS_STOPPED = "STOPPED";

    /**
     * 日志文件后缀
     */
    private static final String LOG_SUFFIX = ".log";

    /**
     * 摘要算法
     */
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * 摘要源分隔符
     */
    private static final String HASH_SEPARATOR = "|";

    /**
     * 日志文件名长度（摘要前 32 位十六进制字符）
     */
    private static final int HASH_FILE_NAME_LENGTH = 32;

    /**
     * 回放历史时最多读取的日志尾部字节数
     */
    private static final int HISTORY_READ_BYTES = 256 * 1024;

    /**
     * UTF-8 续字节掩码
     */
    private static final int UTF8_CONTINUATION_MASK = 0xC0;

    /**
     * UTF-8 续字节标志
     */
    private static final int UTF8_CONTINUATION_FLAG = 0x80;

    /**
     * 换行字节
     */
    private static final byte LINE_BREAK = '\n';

    /**
     * 终端会话 Mapper
     */
    private final SysAiTerminalSessionMapper mapper;

    /**
     * 工作目录解析器
     */
    private final WorkspacePathResolver pathResolver;

    /**
     * 解析账号终端存储目录。
     *
     * <p>基路径未配置、目录不可建等情况返回 {@code null}，调用方据此降级为仅内存历史。</p>
     *
     * @param userId 账号标识
     * @return 存储目录，不可用返回 {@code null}
     */
    Path terminalStorage(String userId) {
        try {
            return pathResolver.terminalStorage(userId);
        } catch (Exception e) {
            log.warn("[terminal] 解析终端存储目录失败，终端历史将只保留在内存 user={} error={}",
                    userId, TerminalEvents.messageOf(e));
            return null;
        }
    }

    /**
     * 解析会话日志文件路径。
     *
     * @param userId        账号标识
     * @param chatSessionId 聊天会话标识
     * @return 日志文件路径，存储不可用返回 {@code null}
     */
    Path logPath(String userId, String chatSessionId) {
        Path directory = terminalStorage(userId);
        if (directory == null) {
            return null;
        }
        return directory.resolve(historyFileName(userId, chatSessionId));
    }

    /**
     * 打开会话日志写入器。
     *
     * @param userId        账号标识
     * @param chatSessionId 聊天会话标识
     * @return 日志写入器，不可用返回 {@code null}
     */
    TerminalLogWriter openWriter(String userId, String chatSessionId) {
        Path path = logPath(userId, chatSessionId);
        if (path == null) {
            return null;
        }
        try {
            return TerminalLogWriter.open(path);
        } catch (IOException e) {
            log.warn("[terminal] 打开终端输出日志失败 path={} error={}", path, TerminalEvents.messageOf(e));
            return null;
        }
    }

    /**
     * 读取会话日志尾部内容，用于冷启动/会话回收后的历史回放。
     *
     * @param userId        账号标识
     * @param chatSessionId 聊天会话标识
     * @return 历史输出（可能为空字符串）
     */
    String readHistory(String userId, String chatSessionId) {
        Path path = logPath(userId, chatSessionId);
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        try {
            byte[] content = Files.readAllBytes(path);
            int start = (int) Math.max(0L, (long) content.length - HISTORY_READ_BYTES);
            while (start < content.length && (content[start] & UTF8_CONTINUATION_MASK) == UTF8_CONTINUATION_FLAG) {
                start++;
            }
            // 从日志中段读取时跳过不完整的第一行，避免回放出半行内容
            if (start > 0) {
                int lineStart = start;
                while (lineStart < content.length && content[lineStart] != LINE_BREAK) {
                    lineStart++;
                }
                if (lineStart < content.length) {
                    start = lineStart + 1;
                }
            }
            return new String(content, start, content.length - start, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[terminal] 读取终端输出日志失败 path={} error={}", path, TerminalEvents.messageOf(e));
            return "";
        }
    }

    /**
     * 记录/刷新会话为运行中。
     *
     * <p>先按唯一键更新，未命中再插入：同一聊天会话重复打开终端只保留一条记录。</p>
     *
     * @param key          会话键
     * @param shell        终端 shell
     * @param cwd          初始工作目录
     * @param columns      列数
     * @param rows         行数
     */
    void saveRunning(TerminalSessionKey key, String shell, String cwd, int columns, int rows) {
        LocalDateTime now = LocalDateTime.now();
        try {
            int updated = mapper.update(null, new LambdaUpdateWrapper<SysAiTerminalSession>()
                    .eq(SysAiTerminalSession::getUserId, key.userId())
                    .eq(SysAiTerminalSession::getChatSessionId, key.chatSessionId())
                    .set(SysAiTerminalSession::getShell, shell)
                    .set(SysAiTerminalSession::getCwd, cwd)
                    .set(SysAiTerminalSession::getStatus, STATUS_RUNNING)
                    .set(SysAiTerminalSession::getExitCode, null)
                    .set(SysAiTerminalSession::getColumns, columns)
                    .set(SysAiTerminalSession::getRows, rows)
                    .set(SysAiTerminalSession::getUpdateTime, now));
            if (updated > 0) {
                return;
            }
            SysAiTerminalSession row = new SysAiTerminalSession();
            row.setUserId(key.userId());
            row.setChatSessionId(key.chatSessionId());
            row.setShell(shell);
            row.setCwd(cwd);
            row.setStatus(STATUS_RUNNING);
            row.setColumns(columns);
            row.setRows(rows);
            row.setOutputBytes(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            mapper.insert(row);
        } catch (Exception e) {
            log.warn("[terminal] 终端会话元信息写入失败 user={} session={} error={}",
                    key.userId(), key.chatSessionId(), TerminalEvents.messageOf(e));
        }
    }

    /**
     * 标记会话结束。
     *
     * @param key          会话键
     * @param status       结束状态
     * @param exitCode     退出码，主动终止或回收时为空
     * @param outputBytes  累计输出字节数
     */
    void markFinished(TerminalSessionKey key, String status, Integer exitCode, long outputBytes) {
        try {
            mapper.update(null, new LambdaUpdateWrapper<SysAiTerminalSession>()
                    .eq(SysAiTerminalSession::getUserId, key.userId())
                    .eq(SysAiTerminalSession::getChatSessionId, key.chatSessionId())
                    .set(SysAiTerminalSession::getStatus, status)
                    .set(SysAiTerminalSession::getExitCode, exitCode)
                    .set(SysAiTerminalSession::getOutputBytes, outputBytes)
                    .set(SysAiTerminalSession::getUpdateTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("[terminal] 终端会话状态更新失败 user={} session={} error={}",
                    key.userId(), key.chatSessionId(), TerminalEvents.messageOf(e));
        }
    }

    /**
     * 把遗留的运行中记录统一标记为已终止（服务启动时调用）。
     *
     * <p>终端进程只存在于进程内存，服务重启后这些记录已不可能继续运行。
     * 注意：多实例共享同一数据库时，本方法会把其它实例的活跃会话也标记为 STOPPED，
     * 当前部署形态为单体应用，故按此处理；多实例部署需改为按实例标识过滤。</p>
     *
     * @return 更新行数
     */
    int markStaleRunning() {
        try {
            return mapper.update(null, new LambdaUpdateWrapper<SysAiTerminalSession>()
                    .eq(SysAiTerminalSession::getStatus, STATUS_RUNNING)
                    .set(SysAiTerminalSession::getStatus, STATUS_STOPPED)
                    .set(SysAiTerminalSession::getUpdateTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("[terminal] 启动清理遗留终端会话失败 error={}", TerminalEvents.messageOf(e));
            return 0;
        }
    }

    /**
     * 生成会话日志文件名。
     *
     * @param userId        账号标识
     * @param chatSessionId 聊天会话标识
     * @return 日志文件名
     */
    private String historyFileName(String userId, String chatSessionId) {
        String source = userId + HASH_SEPARATOR + chatSessionId;
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            String hex = HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, HASH_FILE_NAME_LENGTH) + LOG_SUFFIX;
        } catch (NoSuchAlgorithmException e) {
            log.warn("[terminal] 摘要算法不可用，改用哈希码命名日志 error={}", TerminalEvents.messageOf(e));
            return Integer.toHexString(source.hashCode()) + LOG_SUFFIX;
        }
    }
}
