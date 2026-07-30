package com.chua.starter.project.service.runtime.impl;

import com.chua.common.support.annotation.Spi;
import com.chua.starter.project.model.remote.ProjectRemoteProject;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandSupport;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端项目运行时命令策略。
 * <p>
 * Nginx 优先 + Node.js 自动降级：
 * <ul>
 *   <li>Nginx 模式：自动检测 Nginx → 写入站点配置 → ln -sf → reload</li>
 *   <li>Node 降级模式：检测 http-server/serve → nohup 后台启动</li>
 * </ul>
 * 所有环境检测逻辑放在生成的 Shell 脚本内部（运行时检测）。
 * <p>
 * 端口配置：从 {@code healthCheckUrl} 正则提取，默认 8080。
 * 子目录配置：{@code mainArtifact} 如为纯目录名（如 {@code dist}），
 * 则 Nginx root 指向 {@code rootDirectory/dist}。
 */
@Spi("frontend")
public class FrontendProjectRuntimeCommandStrategy implements ProjectRuntimeCommandStrategy {

    private static final String NGINX_SITES_DIR = ".panel/nginx-sites";
    private static final Pattern PORT_PATTERN = Pattern.compile(":(\\d{2,5})(/|$)");
    private static final int DEFAULT_PORT = 8080;

    @Override
    public String buildStart(ProjectRuntimeCommandContext context) {
        ProjectRemoteProject project = context.project();
        String rootDir = context.rootDirectory();
        String logDir = context.logDirectory();
        String startupLog = context.startupLogFile();
        String pidFile = context.pidFile();
        String projectId = project.getProjectId();
        int port = resolvePort(project);
        String subDir = resolveSubDirectory(project);
        String webRoot = StringUtils.hasText(subDir) ? rootDir + "/" + subDir : rootDir;
        // 使用 $HOME 环境变量，避免单引号包裹导致 ~ 不展开
        String nginxSitesDir = "\"$HOME\"/" + NGINX_SITES_DIR;

        // Nginx 配置（base64 编码，避免 Shell 变量展开）
        String nginxConf = buildNginxConfig(webRoot, port);
        String encodedConf = Base64.getEncoder().encodeToString(nginxConf.getBytes(StandardCharsets.UTF_8));
        String configName = projectId + ".conf";

        // Shell 脚本生成
        return "mkdir -p " + sq(logDir) + " " + nginxSitesDir + " " + sq(rootDir)
                // Nginx 模式
                + " && if which nginx >/dev/null 2>&1; then"
                + "   echo '" + encodedConf + "' | base64 -d > " + nginxSitesDir + "/" + configName
                + "   && (sudo ln -sf " + nginxSitesDir + "/" + configName + " /etc/nginx/sites-enabled/" + configName + " 2>/dev/null"
                + "     || sudo cp " + nginxSitesDir + "/" + configName + " /etc/nginx/conf.d/" + configName + " 2>/dev/null"
                + "     || ln -sf " + nginxSitesDir + "/" + configName + " /etc/nginx/sites-enabled/" + configName + " 2>/dev/null"
                + "     || cp " + nginxSitesDir + "/" + configName + " /etc/nginx/conf.d/" + configName + " 2>/dev/null)"
                + "   && ( (sudo nginx -t 2>&1 && sudo nginx -s reload 2>&1) || (nginx -t 2>&1 && nginx -s reload 2>&1) )"
                + "   && echo " + sq("nginx:" + port) + " > " + sq(pidFile) + ";"
                // Node 降级模式
                + "   else"
                + "   _cmd=''"
                + "   && if command -v http-server >/dev/null 2>&1; then"
                + "     _cmd='http-server';"
                + "   elif command -v serve >/dev/null 2>&1; then"
                + "     _cmd='serve -s';"
                + "   else _cmd='npx -y http-server'; fi"
                + "   && cd " + sq(rootDir)
                + "   && (nohup $_cmd -p " + port + " --cors"
                + "     > " + sq(startupLog) + " 2>&1 < /dev/null &"
                + "     echo $! > " + sq(pidFile) + ")"
                + "; fi"
                + " && echo 'ok'";
    }

    @Override
    public String buildStop(ProjectRuntimeCommandContext context) {
        ProjectRemoteProject project = context.project();
        String pidFile = context.pidFile();
        String rootDir = context.rootDirectory();
        String projectId = project.getProjectId();
        String configName = projectId + ".conf";

        return "pid_file=" + sq(pidFile)
                // 读取引擎标记
                + "; if [ -f \"$pid_file\" ]; then"
                + "   _engine=$(cat \"$pid_file\" | tr -d '\\r\\n ')"
                // Nginx 模式：删除配置 → reload
                + "   && if echo \"$_engine\" | grep -q '^nginx:'; then"
                + "     sudo rm -f /etc/nginx/sites-enabled/" + configName + " 2>/dev/null"
                + "     ; sudo rm -f /etc/nginx/conf.d/" + configName + " 2>/dev/null"
                + "     ; rm -f " + "\"$HOME\"/" + NGINX_SITES_DIR + "/" + configName + " 2>/dev/null"
                + "     ; sudo nginx -s reload 2>/dev/null || nginx -s reload 2>/dev/null || true"
                + "     ; rm -f \"$pid_file\""
                // Node 模式：kill PID
                + "   else"
                + "     _pid=$(cat \"$pid_file\" | tr -d '\\r\\n ')"
                + "     ; rm -f \"$pid_file\""
                + "     ; if [ -n \"$_pid\" ] && kill -0 \"$_pid\" 2>/dev/null; then"
                + "       kill \"$_pid\" 2>/dev/null || true"
                + "       ; sleep 2"
                + "       ; kill -0 \"$_pid\" 2>/dev/null && kill -9 \"$_pid\" 2>/dev/null || true"
                + "     ; fi"
                + "   ; fi"
                + "; fi"
                // 兜底：清理 http-server / serve 进程（cwd 匹配）
                + "; for _kw in 'http-server' 'serve'; do"
                + "   pgrep -f -- \"$_kw\" 2>/dev/null | while read _p; do"
                + "     if [ -L /proc/\"$_p\"/cwd ]"
                + "       && [ \"$(readlink /proc/\"$_p\"/cwd)\" = " + sq(rootDir) + " ]; then"
                + "       kill \"$_p\" 2>/dev/null || true"
                + "       ; sleep 1; kill -9 \"$_p\" 2>/dev/null || true"
                + "     ; fi"
                + "   ; done"
                + " ; done 2>/dev/null; true";
    }

    /**
     * Nginx 站点配置。
     * <p>注意：此方法生成的字符串会被 base64 编码后嵌入 Shell 脚本，
     * 因此这里可以直接写 nginx 语法中的 $uri 变量。</p>
     */
    private String buildNginxConfig(String webRoot, int port) {
        return "server {\n"
                + "    listen " + port + ";\n"
                + "    server_name localhost;\n"
                + "    root " + webRoot + ";\n"
                + "    index index.html index.htm;\n"
                + "    charset utf-8;\n"
                + "\n"
                + "    gzip on;\n"
                + "    gzip_min_length 1024;\n"
                + "    gzip_types text/css application/javascript application/json image/svg+xml font/woff2;\n"
                + "    gzip_vary on;\n"
                + "\n"
                + "    location / {\n"
                + "        try_files $uri $uri/ /index.html;\n"
                + "    }\n"
                + "\n"
                + "    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {\n"
                + "        expires 30d;\n"
                + "        add_header Cache-Control \"public, immutable\";\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 从 healthCheckUrl 提取端口，未设置则返回默认值。
     */
    private int resolvePort(ProjectRemoteProject project) {
        if (StringUtils.hasText(project.getHealthCheckUrl())) {
            Matcher m = PORT_PATTERN.matcher(project.getHealthCheckUrl());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return DEFAULT_PORT;
    }

    /**
     * mainArtifact 如为纯目录名（如 dist），返回该目录名，否则返回空。
     */
    private String resolveSubDirectory(ProjectRemoteProject project) {
        String artifact = project.getMainArtifact();
        if (StringUtils.hasText(artifact)
                && !artifact.contains(".")
                && !artifact.contains("/")
                && !artifact.contains("\\")) {
            return artifact.trim();
        }
        return "";
    }

    private static String sq(String value) {
        return ProjectRuntimeCommandSupport.shellQuote(value);
    }
}
