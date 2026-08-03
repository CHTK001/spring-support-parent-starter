package com.chua.starter.datasync.support;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.ssh.service.SshServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OSGi 与 SSH 集成验证 RESTful 接口。
 *
 * <p>提供 Bundle 动态安装/卸载、服务查询、SSH 服务状态查询等接口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/test/integration")
public class IntegrationTestController {

    @Autowired(required = false)
    private FelixOsgiLauncher osgiLauncher;

    @Autowired(required = false)
    private SshServerService sshServerService;

    /**
     * 获取当前所有 OSGi Bundle 列表。
     */
    @GetMapping("/osgi/bundles")
    public Object listBundles() {
        if (osgiLauncher == null || !osgiLauncher.isActive()) {
            return error("OSGi 框架未启动");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (OsgiBundle bundle : osgiLauncher.getBundles()) {
            Map<String, Object> info = new HashMap<>();
            info.put("symbolicName", bundle.getSymbolicName());
            info.put("version", bundle.getVersion());
            info.put("state", bundle.getState());
            result.add(info);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", result.size());
        resp.put("bundles", result);
        return resp;
    }

    /**
     * 动态安装 Bundle。
     *
     * @param url bundle 的 jar 路径
     */
    @PostMapping("/osgi/bundles/install")
    public Object installBundle(@RequestParam String url) {
        if (osgiLauncher == null || !osgiLauncher.isActive()) {
            return error("OSGi 框架未启动");
        }
        try {
            OsgiBundle bundle = osgiLauncher.installBundle(url);
            bundle.start();
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("symbolicName", bundle.getSymbolicName());
            resp.put("version", bundle.getVersion());
            resp.put("state", bundle.getState());
            return resp;
        } catch (Exception e) {
            log.error("[OSGi] Bundle 安装失败", e);
            return error("Bundle 安装失败: " + e.getMessage());
        }
    }

    /**
     * 动态卸载 Bundle。
     *
     * @param symbolicName bundle 的符号名称
     */
    @DeleteMapping("/osgi/bundles/{symbolicName}")
    public Object uninstallBundle(@PathVariable String symbolicName) {
        if (osgiLauncher == null || !osgiLauncher.isActive()) {
            return error("OSGi 框架未启动");
        }
        try {
            osgiLauncher.uninstallBundle(symbolicName);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("uninstalled", symbolicName);
            return resp;
        } catch (Exception e) {
            log.error("[OSGi] Bundle 卸载失败", e);
            return error("Bundle 卸载失败: " + e.getMessage());
        }
    }

    /**
     * 查询已注册的 OSGi 服务（按类型）。
     */
    @GetMapping("/osgi/services")
    public Object listServices(@RequestParam(required = false) String type) {
        if (osgiLauncher == null || !osgiLauncher.isActive()) {
            return error("OSGi 框架未启动");
        }
        try {
            List<Object> services = new ArrayList<>();
            if (type != null && !type.isEmpty()) {
                Class<?> clazz = Class.forName(type);
                List<?> list = osgiLauncher.getServices(clazz);
                services.addAll(list);
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("count", services.size());
            resp.put("services", services);
            return resp;
        } catch (Exception e) {
            log.error("[OSGi] 服务查询失败", e);
            return error("服务查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取 SSH 服务状态。
     */
    @GetMapping("/ssh/info")
    public Object sshInfo() {
        if (sshServerService == null) {
            return error("SSH 服务未启动");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("class", sshServerService.getClass().getName());
        resp.put("running", sshServerService.isRunning());
        resp.put("activeSessions", sshServerService.getActiveSessionCount());
        return resp;
    }

    /**
     * 获取所有集成的 Bean 信息。
     */
    @GetMapping("/beans")
    public Object beans() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("osgiLauncher", osgiLauncher != null ? osgiLauncher.getClass().getName() : "NOT FOUND");
        resp.put("osgiActive", osgiLauncher != null && osgiLauncher.isActive());
        resp.put("sshServerService", sshServerService != null ? sshServerService.getClass().getName() : "NOT FOUND");
        resp.put("sshRunning", sshServerService != null && sshServerService.isRunning());
        return resp;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        return resp;
    }
}
