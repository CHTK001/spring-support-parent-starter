// [P3C A20 豁免] 回归探针断言码与超时参数。
package com.chua.starter.server.support.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Actuator 观测回填的回归探针（纯字符串逻辑，不需要真实主机）。
 *
 * <p>为什么值得单独立一段：这一层最容易「编译过、跑起来错」——
 * 端点白名单写松了会把任意路径拼进远端命令，状态码标记写错了解析恒为 0
 * （页面就会一直显示「端点未暴露」），Windows 分支把 JSON 直接打印出来则会被
 * 换行切乱行号。这些都只在真机上才暴露，所以先把可静态验证的部分锁住。</p>
 *
 * <p>对私有方法走反射而不是把它们放宽成 public：这些是实现细节，
 * 为了可测而扩大 API 面，后面就会有人从外面直接调。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class ActuatorProbe {

    /**
     * 取私有方法。
     *
     * @param name   方法名
     * @param params 形参类型
     * @return 可访问的方法
     */
    private Method method(String name, Class<?>... params) {
        try {
            Method m = com.chua.starter.server.support.service.impl.ReleaseServiceImpl.class
                    .getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("方法不存在: " + name, e);
        }
    }

    /**
     * 用一个真实实例调用私有方法（这些方法不依赖任何字段，因此可以传 null 实例）。
     *
     * @param name   方法名
     * @param params 形参类型
     * @param args   实参
     * @return 调用结果
     */
    private Object invoke(String name, Class<?>[] params, Object[] args) {
        try {
            return method(name, params).invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("调用失败: " + name, e);
        }
    }

    /**
     * 端点白名单：只读端点放行，穿越与未知端点拒绝。
     */
    @Test
    public void whitelistAllowsKnownEndpointsOnly() {
        Class<?>[] p = {String.class};
        String[] allowed = {
            "health", "metrics", "metrics/jvm.memory.used", "loggers",
            "loggers/com.chua.demo", "env", "beans", "mappings", "threaddump",
            "configprops", "scheduledtasks", "conditions", "caches", "startup", "info",
        };
        for (String path : allowed) {
            Assertions.assertNotNull(invoke("normalizeActuatorPath", p, new Object[]{path}),
                    "应当放行: " + path);
        }
        String[] denied = {
            "", " ", "../../etc/passwd", "metrics/../../env", "shutdown", "heapdump",
            "metrics/jvm;rm -rf /", "metrics/$(whoami)", "metrics/`id`",
            "jolokia/exec", "h2-console", "metrics/jvm.memory.used/extra/deep",
        };
        for (String path : denied) {
            Assertions.assertNull(invoke("normalizeActuatorPath", p, new Object[]{path}),
                    "应当拒绝: " + path);
        }
    }

    /**
     * POSIX 读取命令：状态码必须在第一行（后面的 head 会截断尾部），且带超时。
     */
    @Test
    public void posixReadPutsCodeFirst() {
        String cmd = (String) invoke("posixActuatorGetCommand",
                new Class<?>[]{String.class}, new Object[]{"http://127.0.0.1:8080/actuator/metrics"});
        Assertions.assertTrue(cmd.contains("__ACT_CODE__"), "缺少状态码标记: " + cmd);
        Assertions.assertTrue(cmd.startsWith("curl -s -m 8 -o /dev/null -w '__ACT_CODE__"),
                "状态码应当是第一条命令的唯一输出: " + cmd);
        Assertions.assertTrue(cmd.contains("/actuator/metrics"), "地址未拼进命令: " + cmd);
        Assertions.assertTrue(cmd.contains("head -c"), "响应体应当被限长: " + cmd);
    }

    /**
     * Windows 读取命令：响应体必须 base64，否则 JSON 里的换行会把行号切乱。
     */
    @Test
    public void windowsReadEncodesBody() {
        String cmd = (String) invoke("windowsActuatorGetCommand",
                new Class<?>[]{String.class}, new Object[]{"http://127.0.0.1:8080/actuator/loggers"});
        Assertions.assertTrue(cmd.contains("ToBase64String"), "响应体未编码: " + cmd);
        Assertions.assertTrue(cmd.contains("ACTBODY="), "缺少响应体行前缀: " + cmd);
        Assertions.assertTrue(cmd.contains("__ACT_CODE__"), "缺少状态码标记: " + cmd);
        Assertions.assertTrue(cmd.contains("TimeoutSec"), "缺少超时: " + cmd);
    }

    /**
     * 改级别命令：POST + JSON 体，清空级别用 configuredLevel=null。
     */
    @Test
    public void postCarriesJsonPayload() {
        String posix = (String) invoke("posixActuatorPostCommand",
                new Class<?>[]{String.class, String.class},
                new Object[]{"http://127.0.0.1:8080/actuator/loggers/ROOT",
                    "{\"configuredLevel\":\"DEBUG\"}"});
        Assertions.assertTrue(posix.contains("-X POST"), "未用 POST: " + posix);
        Assertions.assertTrue(posix.contains("application/json"), "缺少 Content-Type: " + posix);
        Assertions.assertTrue(posix.contains("configuredLevel"), "缺少请求体: " + posix);

        String win = (String) invoke("windowsActuatorPostCommand",
                new Class<?>[]{String.class, String.class},
                new Object[]{"http://127.0.0.1:8080/actuator/loggers/ROOT",
                    "{\"configuredLevel\":null}"});
        Assertions.assertTrue(win.contains("Method Post"), "未用 POST: " + win);
        Assertions.assertTrue(win.contains("configuredLevel"), "缺少请求体: " + win);
    }

    /**
     * 状态码解析：只取标记后的数字，脏输出回落 0。
     */
    @Test
    public void parsesStatusFromMixedOutput() {
        Class<?>[] p = {String.class, boolean.class};
        Assertions.assertEquals(200, invoke("parseActuatorCode", p, new Object[]{"__ACT_CODE__200", false}));
        Assertions.assertEquals(204, invoke("parseActuatorCode", p, new Object[]{"__ACT_CODE__204\n", true}));
        Assertions.assertEquals(404, invoke("parseActuatorCode", p, new Object[]{"noise\n__ACT_CODE__404xyz", false}));
        Assertions.assertEquals(0, invoke("parseActuatorCode", p, new Object[]{"", false}));
        Assertions.assertEquals(0, invoke("parseActuatorCode", p, new Object[]{"connection refused", true}));
    }

    /**
     * 日志级别白名单必须与 Actuator 支持的集合一致，否则页面上选了会被后端拒绝。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void logLevelsMatchActuator() {
        try {
            Field f = com.chua.starter.server.support.service.impl.ReleaseServiceImpl.class
                    .getDeclaredField("LOG_LEVELS");
            f.setAccessible(true);
            Set<String> levels = (Set<String>) f.get(null);
            Assertions.assertEquals(Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF"), levels);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("LOG_LEVELS 读取失败", e);
        }
    }

    /**
     * 真机命令往返：起一个本机 HTTP 服务，把生成的 PowerShell 命令跑一遍。
     *
     * <p>为什么值得跑真的而不只断言字符串：命令是拼出来的 Shell 文本，
     * 字符串看着对、到 PowerShell 里被引号或换行拆坏的情况很常见（本仓已踩过多次）。
     * 这一段验证的是「生成 → 执行 → 解析」整条链路，跑不了 PowerShell 时跳过。</p>
     */
    @Test
    public void windowsCommandRoundTrip() throws Exception {
        String ps = findPowerShell();
        Assumptions.assumeTrue(ps != null, "本机没有 PowerShell，跳过命令往返");

        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        String payload = "{\"names\":[\"jvm.memory.used\",\"http.server.requests\"]}";
        server.createContext("/actuator/metrics", exchange -> {
            byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        try {
            String command = (String) invoke("windowsActuatorGetCommand",
                    new Class<?>[]{String.class},
                    new Object[]{"http://127.0.0.1:" + port + "/actuator/metrics"});
            java.nio.file.Path script = java.nio.file.Files.createTempFile("actuator-probe-", ".ps1");
            java.nio.file.Files.write(script, command.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ProcessBuilder pb = new ProcessBuilder(ps, "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            Assertions.assertTrue(p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "命令超时");
            java.nio.file.Files.deleteIfExists(script);

            int status = (int) (Integer) invoke("parseActuatorCode",
                    new Class<?>[]{String.class, boolean.class}, new Object[]{out, true});
            Assertions.assertEquals(200, status, "状态码解析失败，原始输出:\n" + out);
            String body = (String) invoke("actuatorBody",
                    new Class<?>[]{String.class, boolean.class}, new Object[]{out, true});
            Assertions.assertTrue(body.contains("jvm.memory.used"), "响应体解析失败:\n" + body);
        } finally {
            server.stop(0);
        }
    }

    /**
     * 响应体解析：Windows 走 base64 行，POSIX 取状态码行之后。
     *
     * <p>真机上踩到的坑：Windows 的状态码行没有 `=`，与 body 混在同一套
     * KEY=VALUE 解析里，body 会恒为空串 —— 页面于是永远显示「读取成功但没有内容」。</p>
     */
    @Test
    public void parsesBodyFromBothPlatforms() {
        String json = "{\"names\":[\"jvm.memory.used\"]}";
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String windows = "__ACT_CODE__200\nACTBODY=" + encoded + "\n";
        Assertions.assertEquals(json, invoke("actuatorBody",
                new Class<?>[]{String.class, boolean.class}, new Object[]{windows, true}));

        String posix = "__ACT_CODE__200\n" + json + "\n";
        Assertions.assertEquals(json, ((String) invoke("actuatorBody",
                new Class<?>[]{String.class, boolean.class}, new Object[]{posix, false})).trim());

        Assertions.assertEquals("", invoke("actuatorBody",
                new Class<?>[]{String.class, boolean.class}, new Object[]{"", true}));
        Assertions.assertEquals("", invoke("actuatorBody",
                new Class<?>[]{String.class, boolean.class}, new Object[]{"__ACT_CODE__0", false}));
    }

    /**
     * 日志列表压缩：完整 JSON 与「被 64KB 截断」的 JSON 都要给出可用结果。
     *
     * <p>真实应用 loggers 响应几百 KB，远端执行通道会在 64KB 处截断，
     * 原样 JSON.parse 必然失败；这段保证页面至少能拿到前缀里的条目并知道被截断了。</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void compactsLoggersPayload() {
        String full = "{\"levels\":[\"INFO\",\"DEBUG\"],\"loggers\":{"
                + "\"ROOT\":{\"configuredLevel\":\"INFO\",\"effectiveLevel\":\"INFO\"},"
                + "\"com.chua\":{\"effectiveLevel\":\"DEBUG\"}}}";
        java.util.Map<String, Object> packed =
                com.chua.starter.server.support.util.ActuatorLoggers.compact(full);
        java.util.List<java.util.Map<String, String>> rows =
                (java.util.List<java.util.Map<String, String>>) packed.get("loggers");
        Assertions.assertEquals(2, rows.size());
        Assertions.assertEquals(Boolean.FALSE, packed.get("truncated"));
        Assertions.assertEquals("INFO", rows.get(0).get("configuredLevel"));

        String cut = full.substring(0, full.indexOf("\"com.chua\"") + 12);
        java.util.Map<String, Object> packedCut =
                com.chua.starter.server.support.util.ActuatorLoggers.compact(cut);
        java.util.List<java.util.Map<String, String>> cutRows =
                (java.util.List<java.util.Map<String, String>>) packedCut.get("loggers");
        Assertions.assertEquals(Boolean.TRUE, packedCut.get("truncated"));
        Assertions.assertFalse(cutRows.isEmpty(), "截断后至少要抠出 ROOT");
        Assertions.assertEquals("ROOT", cutRows.get(0).get("name"));
    }

    /**
     * 找一个可用的 PowerShell 宿主。
     *
     * @return 可执行文件路径；没有时返回 null
     */
    private String findPowerShell() {
        for (String candidate : new String[]{"powershell", "pwsh"}) {
            try {
                Process p = new ProcessBuilder(candidate, "-NoProfile", "-Command", "exit 0")
                        .redirectErrorStream(true).start();
                if (p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // 继续试下一个
            }
        }
        return null;
    }
}
