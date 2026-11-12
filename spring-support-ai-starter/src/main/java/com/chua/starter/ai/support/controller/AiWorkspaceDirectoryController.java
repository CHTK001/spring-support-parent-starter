// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.ai.support.workspace.WorkspacePathResolver;
import com.chua.starter.ai.support.workspace.git.AiWorkspaceGitCloner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 账号工作目录下的「项目」目录管理。
 *
 * <p>前端「添加项目」的落盘出口。目录口径与
 * {@code AiSessionController#workspaceTree} 完全一致：
 * 只允许建在 {@code <system.workspaceBaseDir>/<当前账号>/agent} 之内，
 * 名称只接受单级目录名，解析后必须仍落在该目录下。</p>
 *
 * <p>不复用 {@code /api/fs/directory}：那个控制器是管理员级、按调用方给的绝对路径
 * 直接写整台主机，普通会话操作不该有这种能力；这里把可写范围钉死在账号工作目录内。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/workspace")
@RequiredArgsConstructor
public class AiWorkspaceDirectoryController {

    /**
     * 项目名长度上限（按码点计，与前端输入框的 maxlength 保持同一口径）
     */
    private static final int MAX_NAME_LENGTH = 64;

    /**
     * 项目目录全路径上限：给目录内的文件留余量，避免 Windows MAX_PATH 溢出后变成 500
     */
    private static final int MAX_PATH_LENGTH = 240;

    /**
     * 名称允许的字符：单级目录名，不含路径分隔符、盘符、通配符与控制字符
     */
    private static final Pattern ILLEGAL_NAME_CHARS = Pattern.compile("[^\\\\/:*?\"<>|\\p{Cntrl}]+");

    /**
     * Windows 保留设备名：带扩展名（如 {@code CON.txt}）同样不能作为目录名
     */
    private static final Pattern RESERVED_DEVICE_NAME =
            Pattern.compile("^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$");

    /**
     * 只由点组成的名称：{@code .}、{@code ..}、{@code ...} 在路径语义上都不是普通目录名
     */
    private static final Pattern DOTS_ONLY = Pattern.compile("^\\.+$");

    /**
     * 账号工作目录解析器（口径与会话、终端、Git 一致）
     */
    private final WorkspacePathResolver pathResolver;

    /**
     * GitHub 仓库克隆器（浅克隆 + 失败清理）
     */
    private final AiWorkspaceGitCloner gitCloner;

    /**
     * 在账号工作目录下新建一个项目目录。
     *
     * <p>请求体：{@code { name: string }}。名称非法、已存在、越界时抛 4xx，
     * 前端直接把 {@code message} 展示在输入框下方；只有真正写盘失败才是 5xx。</p>
     *
     * @param body 请求体，允许为 null（按名称为空处理）
     * @return 新建目录的 {@code name} 与绝对 {@code path}
     */
    @PostMapping("/directory")
    public ReturnResult<Map<String, Object>> createDirectory(@RequestBody(required = false) Map<String, Object> body) {
        // 与会话创建（AiSessionController#create）同口径：先确认登录身份。
        // 不能只依赖外层过滤器：未登录时 WorkspacePathResolver 会把账号回退成 public，
        // 目录就会落进所有账号共享的公共工作区。
        AiUserContext.requireUserId();
        String name = Objects.toString(body == null ? null : body.get("name"), "").trim();
        validateName(name);
        Path agentWorkspace = pathResolver.agentWorkspace();
        Path target = agentWorkspace.resolve(name).normalize();
        if (!target.startsWith(agentWorkspace)) {
            log.warn("[Workspace] 项目目录越界，已拒绝: {}", target);
            throw new ApiException(ApiErrorCode.FORBIDDEN, "项目目录越界");
        }
        if (target.toString().length() > MAX_PATH_LENGTH) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "项目目录路径过长（最多 " + MAX_PATH_LENGTH + " 个字符），请缩短基路径或项目名");
        }
        if (Files.exists(target)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目已存在：" + name);
        }
        try {
            Files.createDirectories(target);
        } catch (FileAlreadyExistsException e) {
            // exists 与 createDirectories 之间有竞态：并发同名时按「已存在」而不是 500 处理
            log.info("[Workspace] 项目目录并发创建，按已存在处理: {}", target);
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目已存在：" + name);
        } catch (IOException e) {
            log.warn("[Workspace] 创建项目目录失败 path={} error={}", target, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建项目目录失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("path", target.toString());
        log.info("[Workspace] 新建项目目录: {}", target);
        return ReturnResult.ok(result);
    }

    /**
     * 从 GitHub 仓库创建项目目录（浅克隆）。
     *
     * <p>请求体：{@code { url: string, name?: string }}。{@code name} 可省略，
     * 省略时取仓库名。地址只放行 {@code https://github.com/owner/repo}，理由见
     * {@link AiWorkspaceGitCloner}；克隆失败会把半成品目录删除，不会留下一个
     * 「已存在但空」的项目。</p>
     *
     * @param body 请求体，允许为 null（按地址为空处理）
     * @return 新项目目录的 {@code name} 与绝对 {@code path}
     */
    @PostMapping("/directory/git-clone")
    public ReturnResult<Map<String, Object>> cloneFromGithub(@RequestBody(required = false) Map<String, Object> body) {
        // 与 createDirectory 同口径：先确认登录身份，避免落到 public 账号的共享目录
        AiUserContext.requireUserId();
        String url = Objects.toString(body == null ? null : body.get("url"), "").trim();
        String name = Objects.toString(body == null ? null : body.get("name"), "").trim();
        if (name.isEmpty()) {
            // 地址不合法时由克隆器抛出 4xx，名称推导不会拿到半截路径
            name = AiWorkspaceGitCloner.repositoryName(url);
        }
        validateName(name);
        Path agentWorkspace = pathResolver.agentWorkspace();
        Path target = agentWorkspace.resolve(name).normalize();
        if (!target.startsWith(agentWorkspace)) {
            log.warn("[Workspace] 克隆目录越界，已拒绝: {}", target);
            throw new ApiException(ApiErrorCode.FORBIDDEN, "项目目录越界");
        }
        if (target.toString().length() > MAX_PATH_LENGTH) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "项目目录路径过长（最多 " + MAX_PATH_LENGTH + " 个字符），请缩短基路径或项目名");
        }
        if (Files.exists(target)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目已存在：" + name);
        }
        gitCloner.clone(url, target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("path", target.toString());
        result.put("gitUrl", url);
        return ReturnResult.ok(result);
    }

    /**
     * 校验项目名。
     *
     * <p>只放行「单级目录名」：先把 Windows 与 Unix 都不接受的字符挡掉，
     * 再挡住文件系统会静默改写或直接报错的写法（纯点名、结尾点、保留设备名），
     * 让这些情况都以 400 + 可读原因返回，而不是落到写盘失败变成 500。</p>
     *
     * @param name 已 trim 的项目名
     */
    private void validateName(String name) {
        if (name.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目名称不能为空");
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目名称最多 " + MAX_NAME_LENGTH + " 个字符");
        }
        if (!ILLEGAL_NAME_CHARS.matcher(name).matches()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目名称不能包含 \\ / : * ? \" < > | 或控制字符");
        }
        if (DOTS_ONLY.matcher(name).matches()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目名称不能只由点组成");
        }
        if (name.endsWith(".")) {
            // Windows 会静默去掉结尾的点，导致返回值与实际目录不一致
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目名称不能以点结尾");
        }
        if (RESERVED_DEVICE_NAME.matcher(name).matches()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "项目名称不能使用系统保留名：" + name);
        }
    }
}
