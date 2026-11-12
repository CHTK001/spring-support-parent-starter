package com.chua.starter.ai.support.workspace.git;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 从 GitHub 克隆仓库到账号工作目录。
 *
 * <p>「从 GitHub 建工作间」的落盘出口。此前工作间只能是本机已有目录或在
 * 账号工作目录下新建空目录，用户想直接从仓库开始一个项目就没有入口。</p>
 *
 * <p>安全边界与会话 Git 能力同口径：目标目录必须由调用方算好并落在账号工作目录内，
 * 本类只负责「把远程仓库浅克隆到该目录」。地址只放行 {@code https://github.com/owner/repo}：
 * 其它协议（{@code ssh:} / {@code git:} / {@code file:}）会带来凭据与本地文件读取问题，
 * 地址里带用户名口令（{@code https://user:pass@github.com/...}）一律拒绝，
 * 避免把凭据写进 {@code .git/config} 留在磁盘上。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class AiWorkspaceGitCloner {

    /**
     * 允许克隆的主机：只认 GitHub，避免把服务端当成任意远程仓库的代理
     */
    private static final Set<String> ALLOWED_HOSTS = Set.of("github.com", "www.github.com");

    /**
     * 仓库路径形态：{@code owner/repo}，允许带 {@code .git} 与结尾斜杠
     */
    private static final Pattern REPOSITORY_PATH = Pattern.compile("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?:\\.git)?/?$");

    /**
     * 浅克隆深度：只要最新的一个提交，大仓库也不会把服务端磁盘拉满
     */
    private static final int CLONE_DEPTH = 1;

    /**
     * 克隆超时（秒）：卡住时让请求尽早失败，而不是把 Web 线程挂到操作系统断连
     */
    private static final int CLONE_TIMEOUT_SECONDS = 180;

    /**
     * GitHub 仓库地址前缀（规范化后统一成这个形式）
     */
    private static final String GITHUB_PREFIX = "https://github.com/";

    /**
     * Git 仓库后缀
     */
    private static final String GIT_SUFFIX = ".git";

    /**
     * 把远程仓库浅克隆到目标目录。
     *
     * @param url    仓库地址，形如 {@code https://github.com/owner/repo}
     * @param target 目标目录，必须尚不存在；由调用方保证落在账号工作目录内
     * @return 克隆后的仓库根目录（即 {@code target}）
     */
    public Path clone(String url, Path target) {
        String normalizedUrl = normalizeUrl(url);
        if (Files.exists(target)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目已存在：" + target.getFileName());
        }
        try {
            Files.createDirectories(target.getParent());
            try (Git ignored = Git.cloneRepository()
                    .setURI(normalizedUrl)
                    .setDirectory(target.toFile())
                    .setDepth(CLONE_DEPTH)
                    .setCloneAllBranches(false)
                    .setTimeout(CLONE_TIMEOUT_SECONDS)
                    .call()) {
                log.info("[Workspace] 已克隆仓库 {} -> {}", normalizedUrl, target);
            }
        } catch (ApiException e) {
            deleteQuietly(target);
            throw e;
        } catch (Exception e) {
            // 半成品目录必须回收：否则下次同名创建会直接撞「项目已存在」
            deleteQuietly(target);
            log.warn("[Workspace] 克隆仓库失败 url={} target={} error={}", normalizedUrl, target, e.getMessage());
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "克隆仓库失败：" + messageOf(e));
        }
        return target;
    }

    /**
     * 从仓库地址推导项目名（仓库名去掉 {@code .git}）。
     *
     * <p>前端「从 GitHub 新建」里名字可留空，留空时用仓库名。</p>
     *
     * @param url 仓库地址
     * @return 仓库名；地址不合法时抛 4xx
     */
    public static String repositoryName(String url) {
        String normalized = normalizeUrl(url);
        String last = normalized.substring(normalized.lastIndexOf('/') + 1);
        return last.endsWith(GIT_SUFFIX) ? last.substring(0, last.length() - GIT_SUFFIX.length()) : last;
    }

    /**
     * 校验并规范化仓库地址。
     *
     * @param url 用户输入的地址
     * @return 规范化后的 {@code https://github.com/owner/repo.git}
     */
    private static String normalizeUrl(String url) {
        String value = Objects.toString(url, "").trim();
        if (value.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请填写 GitHub 仓库地址");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "仓库地址格式不正确");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "仓库地址必须以 https:// 开头");
        }
        if (uri.getUserInfo() != null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "仓库地址里不能带用户名或口令");
        }
        String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "目前只支持 github.com 上的仓库");
        }
        String path = Objects.toString(uri.getPath(), "");
        if (!REPOSITORY_PATH.matcher(path).matches()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "仓库地址应形如 https://github.com/owner/repo");
        }
        String cleaned = path.replaceAll("^/+", "").replaceAll("/+$", "");
        String normalized = GITHUB_PREFIX + cleaned;
        return normalized.endsWith(GIT_SUFFIX) ? normalized : normalized + GIT_SUFFIX;
    }

    /**
     * 删除克隆失败的半成品目录。
     *
     * <p>只做尽力而为的清理：清理失败也不覆盖原始异常，否则用户看到的是
     * 「删除目录失败」而不是真正的克隆失败原因。</p>
     *
     * @param target 待删除目录
     */
    private static void deleteQuietly(Path target) {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    log.debug("[Workspace] 清理克隆残留失败: {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("[Workspace] 清理克隆残留目录失败 target={} error={}", target, e.getMessage());
        }
    }

    /**
     * 提取异常的可读原因（JGit 的异常常把原因包在 cause 里）。
     *
     * @param error 异常
     * @return 可读原因
     */
    private static String messageOf(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = Objects.toString(cause.getMessage(), "");
        return message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
