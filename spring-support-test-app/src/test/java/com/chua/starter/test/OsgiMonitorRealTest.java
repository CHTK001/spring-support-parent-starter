package com.chua.starter.test;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.osgi.support.FelixOsgiLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实测试：OSGi 只读管理面（HTTP）全链路。
 *
 * <p>验证 {@code OsgiMonitorController} 暴露的监控端点确实是「框架真实状态」的镜像，
 * 覆盖 HTTP → 控制器 → {@link FelixOsgiLauncher} → Felix 框架 这一层：</p>
 * <pre>
 * 基线：stats / context / services / bundles / snapshot 全部可用且与框架状态一致
 * 安装真实 bundle → /osgi/bundles 立即可见、详情可取、桥接版本号推进
 * 卸载 → /osgi/bundles 不再可见
 * </pre>
 *
 * <p>响应经全局 {@code ApiUniformResponseBodyAdvice} 统一包装，
 * 故断言路径位于 {@code $.result} 之下。</p>
 *
 * <p>测试关闭 SSH/Telnet shell，避免端口占用；安装用的 bundle 取自 classpath 上的
 * {@code sshd-osgi}（该 bundle 不会被自动配置装入框架，且在 sshd 2.13.2 下必然
 * 解析失败——因此只验证「已安装可见」，不验证启动成功）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-monitor",
        "plugin.osgi.storage-clean=onInit",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.endpoint.enabled=true"
})
@AutoConfigureMockMvc
class OsgiMonitorRealTest {

    /**
     * Felix 系统 bundle 符号名。
     * <p>
     * 本用例关闭了 shell（避免占用端口），故 Gogo 相关 bundle 不会被安装；
     * 系统 bundle 是框架运行时必然存在的对象，用它验证「管理面 → 框架」这一层。
     * </p>
     */
    private static final String SYSTEM_BUNDLE = "org.apache.felix.framework";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    /**
     * 真实测试：管理端点随 bundle 生命周期保持同步。
     *
     * @throws Exception MockMvc 请求失败时抛出
     */
    @Test
    void monitorEndpointsReflectBundleLifecycle() throws Exception {
        // 1) 基线：只读端点全部可用，且与框架真实状态一致。
        // 该控制器未被统一响应包装（实测返回控制器原始 Map），故断言取根路径。
        mockMvc.perform(get("/osgi/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.frameworkState").value("ACTIVE"))
                .andExpect(jsonPath("$.totalBundles").isNumber());
        mockMvc.perform(get("/osgi/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.bundleContextAvailable").value(true));
        mockMvc.perform(get("/osgi/services")).andExpect(status().isOk());
        mockMvc.perform(get("/osgi/bundles")).andExpect(status().isOk());

        // 2) 快照端点：桥接可用
        mockMvc.perform(get("/osgi/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frameworkActive").value(true))
                .andExpect(jsonPath("$.bridgeAvailable").value(true));
        long versionBefore = snapshotVersion(bodyOf("/osgi/snapshot"));

        // 3) 框架运行时的系统 bundle 应能在管理面看到（HTTP → launcher → 框架）
        assertTrue(bundleListBody().contains(SYSTEM_BUNDLE),
                "/osgi/bundles 应包含系统 bundle " + SYSTEM_BUNDLE);
        mockMvc.perform(get("/osgi/bundles/" + SYSTEM_BUNDLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbolicName").value(SYSTEM_BUNDLE))
                .andExpect(jsonPath("$.headers").exists())
                .andExpect(jsonPath("$.registeredServiceTypes").exists());

        // 4) 安装真实 bundle → 管理面立即可见，且桥接版本号已推进
        File jar = locateSshdOsgiJar();
        OsgiBundle installed = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        String symbolicName = installed.getSymbolicName();
        try {
            assertTrue(bundleListBody().contains(symbolicName),
                    "安装后 /osgi/bundles 应包含 " + symbolicName);
            mockMvc.perform(get("/osgi/bundles/" + symbolicName))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbolicName").value(symbolicName));
            assertTrue(snapshotVersion(bodyOf("/osgi/snapshot")) > versionBefore,
                    "安装事件应传导到容器侧（桥接框架版本号推进）");
        } finally {
            osgiLauncher.uninstallBundle(symbolicName);
        }

        // 5) 卸载后管理面不再可见
        assertFalse(bundleListBody().contains(symbolicName),
                "卸载后 /osgi/bundles 不应包含 " + symbolicName);
    }

    /**
     * 请求指定端点并返回响应体。
     *
     * @param path 请求路径
     * @return 响应体文本
     * @throws Exception MockMvc 请求失败时抛出
     */
    private String bodyOf(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * 读取 /osgi/bundles 的响应体。
     *
     * @return 响应体文本
     * @throws Exception MockMvc 请求失败时抛出
     */
    private String bundleListBody() throws Exception {
        return bodyOf("/osgi/bundles");
    }

    /**
     * 从快照响应中解析桥接侧框架版本号。
     *
     * @param body 快照响应体
     * @return 当前框架版本号
     */
    private long snapshotVersion(String body) {
        Matcher matcher = Pattern.compile("\"frameworkVersion\"\\s*:\\s*(\\d+)").matcher(body);
        assertTrue(matcher.find(), "快照响应应包含 frameworkVersion，实际: " + body);
        return Long.parseLong(matcher.group(1));
    }

    /**
     * 从 classpath 中定位 sshd-osgi jar。
     *
     * @return jar 文件
     */
    private static File locateSshdOsgiJar() {
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(File.pathSeparator)) {
            File candidate = new File(entry);
            if (candidate.isFile()
                    && candidate.getName().startsWith("sshd-osgi-")
                    && candidate.getName().endsWith(".jar")) {
                return candidate;
            }
        }
        throw new IllegalStateException("classpath 中未找到 sshd-osgi jar，无法进行安装侧验证");
    }
}
