package com.chua.starter.datasync.support;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.datasync.support.spi.DynamicService;
import com.chua.starter.ssh.service.SshServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * OSGi 与 SSH 集成验证 RESTful 接口。
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

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取关键集成 Bean 信息。
     */
    @GetMapping("/beans")
    public Object beans() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("osgiLauncher", osgiLauncher != null ? osgiLauncher.getClass().getName() : "NOT FOUND");
        resp.put("osgiActive", osgiLauncher != null && osgiLauncher.isActive());
        resp.put("sshServerService", sshServerService != null ? sshServerService.getClass().getName() : "NOT FOUND");
        resp.put("sshRunning", sshServerService != null && sshServerService.isRunning());
        resp.put("totalBeans", applicationContext.getBeanDefinitionCount());
        return resp;
    }

    /**
     * 获取 Spring 容器中所有 Bean 名称列表（可按关键字过滤）。
     *
     * @param keyword 关键字过滤（可选）
     */
    @GetMapping("/beans/all")
    public Object listAllBeans(@RequestParam(required = false) String keyword) {
        String[] names = applicationContext.getBeanDefinitionNames();
        Arrays.sort(names);
        List<String> filtered = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) {
            filtered.addAll(Arrays.asList(names));
        } else {
            String lower = keyword.toLowerCase();
            for (String name : names) {
                if (name.toLowerCase().contains(lower)) {
                    filtered.add(name);
                }
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", names.length);
        resp.put("matched", filtered.size());
        resp.put("beans", filtered);
        return resp;
    }

    /**
     * 获取指定 Bean 的类型与实现类信息。
     */
    @GetMapping("/beans/{name}")
    public Object getBean(@PathVariable String name) {
        if (!applicationContext.containsBean(name)) {
            return error("Bean 未找到: " + name);
        }
        Object bean = applicationContext.getBean(name);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("name", name);
        resp.put("class", bean.getClass().getName());
        resp.put("type", Arrays.stream(applicationContext.getAliases(name)).toList());
        resp.put("isSingleton", applicationContext.isSingleton(name));
        return resp;
    }

    /**
     * 调用通过 OSGi Bundle 注册的 DynamicService。
     *
     * @param input 输入参数
     */
    @GetMapping("/dynamic/invoke")
    public Object invokeDynamic(@RequestParam(defaultValue = "world") String input) {
        if (!ready()) {
            return error("OSGi 框架未启动");
        }
        try {
            List<DynamicService> services = osgiLauncher.getServices(DynamicService.class);
            List<Map<String, Object>> results = new ArrayList<>();
            for (DynamicService svc : services) {
                Map<String, Object> r = new HashMap<>();
                r.put("name", svc.getName());
                r.put("class", svc.getClass().getName());
                r.put("output", svc.execute(input));
                results.add(r);
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("count", results.size());
            resp.put("results", results);
            return resp;
        } catch (Exception e) {
            log.error("[Dynamic] 调用失败", e);
            return error("调用失败: " + e.getMessage());
        }
    }

    /**
     * 列出所有 Bundle。
     */
    @GetMapping("/osgi/bundles")
    public Object listBundles() {
        if (!ready()) {
            return error("OSGi 框架未启动");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (OsgiBundle bundle : osgiLauncher.getBundles()) {
            result.add(toMap(bundle));
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", result.size());
        resp.put("bundles", result);
        return resp;
    }

    /**
     * 获取指定 Bundle 详情。
     */
    @GetMapping("/osgi/bundles/{symbolicName}")
    public Object getBundle(@PathVariable String symbolicName) {
        if (!ready()) {
            return error("OSGi 框架未启动");
        }
        OsgiBundle target = findBundle(symbolicName);
        if (target == null) {
            return error("Bundle 未找到: " + symbolicName);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.putAll(toMap(target));
        resp.put("exportedPackages", scanExportedPackages(target));
        return resp;
    }

    /**
     * 安装 Bundle（可选自动启动）。
     */
    @PostMapping("/osgi/bundles/install")
    public Object installBundle(@RequestParam String url,
                                @RequestParam(defaultValue = "true") boolean autoStart) {
        if (!ready()) {
            return error("OSGi 框架未启动");
        }
        try {
            String normalizedUrl = normalizeUrl(url);
            OsgiBundle bundle = osgiLauncher.installBundle(normalizedUrl);
            if (autoStart) {
                bundle.start();
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.putAll(toMap(bundle));
            resp.put("url", normalizedUrl);
            return resp;
        } catch (Exception e) {
            log.error("[OSGi] Bundle 安装失败", e);
            return error("Bundle 安装失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 卸载 Bundle。
     */
    @DeleteMapping("/osgi/bundles/{symbolicName}")
    public Object uninstallBundle(@PathVariable String symbolicName) {
        if (!ready()) {
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
            return error("Bundle 卸载失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 启动 Bundle。
     */
    @PostMapping("/osgi/bundles/{symbolicName}/start")
    public Object startBundle(@PathVariable String symbolicName) {
        return changeBundleState(symbolicName, true, "启动");
    }

    /**
     * 停止 Bundle。
     */
    @PostMapping("/osgi/bundles/{symbolicName}/stop")
    public Object stopBundle(@PathVariable String symbolicName) {
        return changeBundleState(symbolicName, false, "停止");
    }

    /**
     * 查询已注册的 OSGi 服务。
     */
    @GetMapping("/osgi/services")
    public Object listServices(@RequestParam String type,
                               @RequestParam(defaultValue = "true") boolean fuzzyMatch) {
        if (!ready()) {
            return error("OSGi 框架未启动");
        }
        try {
            Class<?> clazz = null;
            if (fuzzyMatch) {
                clazz = resolveClass(type);
                if (clazz == null) {
                    return error("无法解析类: " + type + "（请提供完整类名或简单类名）");
                }
            } else {
                clazz = Class.forName(type);
            }
            List<?> list = osgiLauncher.getServices(clazz);
            List<Map<String, Object>> details = new ArrayList<>();
            for (Object svc : list) {
                Map<String, Object> d = new HashMap<>();
                d.put("class", svc.getClass().getName());
                details.add(d);
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("type", clazz.getName());
            resp.put("count", list.size());
            resp.put("services", details);
            return resp;
        } catch (ClassNotFoundException e) {
            return error("类未找到: " + type);
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
        resp.put("success", true);
        resp.put("class", sshServerService.getClass().getName());
        resp.put("running", sshServerService.isRunning());
        resp.put("activeSessions", sshServerService.getActiveSessionCount());
        return resp;
    }

    // ============================ 私有辅助 ============================

    private boolean ready() {
        return osgiLauncher != null && osgiLauncher.isActive();
    }

    private OsgiBundle findBundle(String symbolicName) {
        for (OsgiBundle bundle : osgiLauncher.getBundles()) {
            if (symbolicName.equals(bundle.getSymbolicName())) {
                return bundle;
            }
        }
        return null;
    }

    private Object changeBundleState(String symbolicName, boolean start, String action) {
        if (!ready()) {
            return error("OSGi 框架未启动");
        }
        OsgiBundle bundle = findBundle(symbolicName);
        if (bundle == null) {
            return error("Bundle 未找到: " + symbolicName);
        }
        try {
            if (start) {
                bundle.start();
            } else {
                bundle.stop();
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("action", action);
            resp.putAll(toMap(bundle));
            return resp;
        } catch (Exception e) {
            log.error("[OSGi] Bundle {} 失败", action, e);
            return error("Bundle " + action + " 失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(OsgiBundle bundle) {
        Map<String, Object> info = new HashMap<>();
        info.put("symbolicName", bundle.getSymbolicName());
        info.put("version", bundle.getVersion());
        info.put("state", bundle.getState());
        return info;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        if (url.startsWith("file:") || url.startsWith("http:") || url.startsWith("https:")) {
            return url;
        }
        File f = new File(url);
        if (!f.exists()) {
            throw new IllegalArgumentException("文件不存在: " + url);
        }
        return "file:///" + f.getAbsolutePath().replace('\\', '/');
    }

    private Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignore) {
        }
        String[] prefixes = {
                "com.chua.starter.datasync.support.spi.",
                "org.apache.felix.gogo.",
                "org.osgi.",
                "org.apache.felix.",
                "com.chua."
        };
        for (String prefix : prefixes) {
            try {
                return Class.forName(prefix + name);
            } catch (ClassNotFoundException ignore) {
            }
        }
        return null;
    }

    private List<String> scanExportedPackages(OsgiBundle bundle) {
        List<String> packages = new ArrayList<>();
        try {
            File cacheDir = new File("./osgi-cache");
            if (!cacheDir.exists()) {
                return packages;
            }
            for (File bundleDir : cacheDir.listFiles((d, n) -> n.startsWith("bundle"))) {
                for (File versionDir : bundleDir.listFiles((d, n) -> n.startsWith("version"))) {
                    File jarFile = new File(versionDir, "bundle.jar");
                    if (jarFile.exists()) {
                        try (JarFile jar = new JarFile(jarFile)) {
                            Enumeration<JarEntry> entries = jar.entries();
                            while (entries.hasMoreElements()) {
                                String name = entries.nextElement().getName();
                                if (name.endsWith(".class") && !name.contains("/")) {
                                    continue;
                                }
                                String pkg = name.replace('/', '.');
                                int idx = pkg.lastIndexOf('.');
                                if (idx > 0 && !packages.contains(pkg.substring(0, idx))) {
                                    packages.add(pkg.substring(0, idx));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[OSGi] 扫描导出包失败", e);
        }
        return packages;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        return resp;
    }
}
