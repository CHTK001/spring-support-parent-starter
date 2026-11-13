// [P3C A20 豁免] A20/A30/A31: 含日志/断言名/参数模板等非业务魔法值场景，豁免。
package com.chua.starter.server.support.probe;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.mapper.MsServerConfigMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.mapper.MsServerReleaseMapper;
import com.chua.starter.server.support.service.impl.ReleaseServiceImpl;
import com.chua.starter.server.support.spi.PackageManagerRouter;
import com.chua.starter.server.support.spi.ProjectPlatformProviderRegistry;
import com.chua.starter.server.support.spi.ProjectTypeProviderRegistry;
import com.chua.starter.server.support.spi.SystemServiceProviderRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 发布级 SkyWalking APM 注入的回归探针。
 *
 * <p>APM 注入是「改配置就改行为」的纯字符串拼接逻辑：注入三条 JVM 参数、项目级配置优先、
 * 发布配置变更后同步到下属项目、关闭时清理。这些分支在编译期全部无感，而一旦拼错
 * （参数名漏个点、路径为空仍拼出 {@code -javaagent:null}、项目自己写的 agent 被发布级覆盖、
 * 重复同步叠加出两组 {@code -javaagent:}）只会在真机启动或链路采集不到时才发现，
 * 且现场取证成本极高。此前的脚本生成探针 {@code ReleaseScriptProbe} 没有覆盖这一段。</p>
 *
 * <p><b>探针口径</b>：全部走真实生产代码（{@link ReleaseServiceImpl} 的公开方法与私有方法），
 * 只把四个 Mapper 换成 {@link Proxy} 桩，不连数据库、不起 Spring 容器。这样断言的
 * 是「按下发布开关之后，项目最终落库的 jvmArgs 是什么样」，而不是探针自己复刻的规则。</p>
 *
 * <p><b>覆盖的五个触发点</b>：</p>
 * <ol>
 *   <li>A：注入条件矩阵（开关 / jar / OAP 地址 / 项目级优先 / 幂等 / 裸参数兜底）</li>
 *   <li>B：新建项目时注入（{@code createProject}）</li>
 *   <li>C：发布配置变更后同步到下属项目（{@code updateRelease} → {@code syncReleaseApm}）</li>
 *   <li>D：关闭开关或配置失效后清理 APM 行（项目级 APM 永不清）</li>
 *   <li>E：复制项目到其它发布时按目标发布注入（{@code copyProjects}）</li>
 * </ol>
 *
 * <p>F 段把「APM 参数判定」当作契约单独锁定：注入的三条参数必须全部能被
 * {@code isApmArg} 识别，否则第二次同步会重复注入第二组 {@code -javaagent:}，
 * 直接导致 JVM 启动失败（同端口两个 agent 争用）。</p>
 *
 * <p>命名用 {@code Probe} 而非 {@code Test}：本仓约定非 example 模块不新增 {@code *Test} 类；
 * surefire 已在 pom 里补上 {@code **}{@code /*Probe.java} 匹配，故 {@code mvn test} 会直接执行本探针。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class ReleaseApmProbe {

    /**
     * 发布级 SkyWalking agent jar 路径样例（目标主机绝对路径）。
     */
    private static final String JAR = "/opt/skywalking/agent/skywalking-agent.jar";

    /**
     * 另一个发布使用的 agent jar 路径（验证「按目标发布注入」而不是沿用源发布）。
     */
    private static final String JAR2 = "/data/apm/skywalking-agent.jar";

    /**
     * OAP 集群地址样例（collector.backend_service）。
     */
    private static final String BACKEND = "oap.cluster:11800";

    /**
     * 另一个发布的 OAP 地址。
     */
    private static final String BACKEND2 = "oap.prod:11800";

    /**
     * 发布级 service_name 前缀样例。
     */
    private static final String PREFIX = "ms";

    /**
     * 参数序列化器：与生产同一套 Jackson（生产用同一 ObjectMapper 写出 jvmArgs）。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 通过数。
     */
    private int passed;

    /**
     * 失败明细（断言名 + 现场文本）。
     */
    private final List<String> failures = new ArrayList<>();

    private ReleaseApmProbe() {
    }

    // ==================== 独立运行入口 ====================

    /**
     * 探针独立入口：逐段跑完全部断言，失败则以退出码 1 结束。
     *
     * <p>不调用 {@code System.exit(0)}：成功时让进程自然结束，避免被 surefire 加载时误退出 JVM。</p>
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        ReleaseApmProbe probe = new ReleaseApmProbe();
        probe.sectionAInjectionMatrix();
        probe.sectionBCreateProject();
        probe.sectionCSyncOnReleaseUpdate();
        probe.sectionDClearOnDisable();
        probe.sectionECopyProjects();
        probe.sectionFArgContract();
        System.out.println("[apm-probe] ---- 汇总：passed=" + probe.passed + " failed=" + probe.failures.size() + " ----"); // [P3C E1 豁免]
        probe.failures.forEach(f -> System.out.println("[apm-probe] FAIL " + f)); // [P3C E1 豁免]
        if (!probe.failures.isEmpty()) {
            throw new IllegalStateException("程序执行失败，请检查执行结果日志。");
        }
    }

    // ==================== A. 注入条件矩阵 ====================

    /**
     * A 段：发布级 APM 注入的准入条件与产物形态。
     */
    @Test
    void aInjectionMatrix() {
        sectionAInjectionMatrix();
        assertSectionDone("A");
    }

    private void sectionAInjectionMatrix() {
        // A1 基线：开关开启且 jar / OAP 地址就绪 → 三条参数按固定顺序落在最前
        Harness base = harness(release(1L, true, JAR, PREFIX, BACKEND));
        MsServerProject injected = project(11L, 1L, "demo", null);
        applyApm(base.impl, injected);
        check("A1 开关开启且 jar/OAP 就绪时注入三条参数（顺序：agent → service_name → backend）",
                argsOf("A1", injected).equals(List.of(
                        "-javaagent:" + JAR,
                        "-Dskywalking.agent.service_name=" + PREFIX + "-demo",
                        "-Dskywalking.collector.backend_service=" + BACKEND)),
                injected.getJvmArgs());

        // A2~A7 五个「不注入」条件：任一缺失都必须整体不注入，绝不能拼出半截参数
        check("A2 开关为 false 时不注入（jvmArgs 原样不动）",
                untouched(false, JAR, PREFIX, BACKEND), "开关 false 仍被改写");
        check("A3 开关为 null 时不注入（未设置不等于开启）",
                untouched(null, JAR, PREFIX, BACKEND), "开关 null 被当成开启");
        check("A4 jar 路径为空串时不注入",
                untouched(true, "", PREFIX, BACKEND), "jar 为空串仍注入");
        check("A5 jar 路径为空白串时不注入（空白不等于已配置）",
                untouched(true, "   ", PREFIX, BACKEND), "jar 全空白仍注入");
        check("A6 jar 路径为 null 时不注入",
                untouched(true, null, PREFIX, BACKEND), "jar 为 null 仍注入");
        check("A7 OAP 地址为空时不注入（没有 backend 采集不到，注入只会白占启动参数）",
                untouched(true, JAR, PREFIX, ""), "backend 为空仍注入");
        check("A7b OAP 地址为 null 时不注入",
                untouched(true, JAR, PREFIX, null), "backend 为 null 仍注入");

        // A8 没有归属发布的项目不该去查发布表（提前返回，不做无谓查询）
        Harness orphan = harness(release(8L, true, JAR, PREFIX, BACKEND));
        MsServerProject noRelease = project(18L, null, "demo", null);
        applyApm(orphan.impl, noRelease);
        check("A8 releaseId 为 null 时直接跳过，且不查发布表",
                orphan.releases.selectByIdCalls == 0 && noRelease.getJvmArgs() == null,
                "selectByIdCalls=" + orphan.releases.selectByIdCalls + " jvmArgs=" + noRelease.getJvmArgs());

        // A9 发布已被删除（查不到）时不能 NPE，也不能注入
        Harness missing = harness();
        MsServerProject dangling = project(19L, 404L, "demo", null);
        applyApm(missing.impl, dangling);
        check("A9 发布不存在时不注入且不抛异常", dangling.getJvmArgs() == null, dangling.getJvmArgs());

        // A10 service_name 拼装规则：前缀-项目名，缺一则退化为另一侧
        Harness noPrefix = harness(release(10L, true, JAR, null, BACKEND));
        MsServerProject onlyName = project(110L, 10L, "demo", null);
        applyApm(noPrefix.impl, onlyName);
        check("A10a 前缀为空时 service_name 只取项目名",
                argsOf("A10a", onlyName).contains("-Dskywalking.agent.service_name=demo"),
                onlyName.getJvmArgs());

        Harness noName = harness(release(11L, true, JAR, PREFIX, BACKEND));
        MsServerProject blankName = project(111L, 11L, null, null);
        applyApm(noName.impl, blankName);
        check("A10b 项目名为空时 service_name 只取前缀",
                argsOf("A10b", blankName).contains("-Dskywalking.agent.service_name=" + PREFIX),
                blankName.getJvmArgs());

        Harness blankPrefix = harness(release(12L, true, JAR, "  ", BACKEND));
        MsServerProject normalName = project(112L, 12L, "demo", null);
        applyApm(blankPrefix.impl, normalName);
        check("A10c 空白前缀视同未配置，不拼出「  -demo」这类半截服务名",
                argsOf("A10c", normalName).contains("-Dskywalking.agent.service_name=demo"),
                normalName.getJvmArgs());

        // A11 项目已有的非 APM 参数必须保留，且排在注入的三条之后（agent 必须在 -jar 之前、
        // 也不能打乱用户显式指定的 -Xmx / profiles 顺序）
        Harness keep = harness(release(13L, true, JAR, PREFIX, BACKEND));
        MsServerProject withArgs = project(113L, 13L, "demo",
                json("-Xmx512m", "--spring.profiles.active=prod"));
        applyApm(keep.impl, withArgs);
        check("A11 已有 JVM 参数保留，注入项在前、用户参数在后",
                argsOf("A11", withArgs).equals(List.of(
                        "-javaagent:" + JAR,
                        "-Dskywalking.agent.service_name=" + PREFIX + "-demo",
                        "-Dskywalking.collector.backend_service=" + BACKEND,
                        "-Xmx512m",
                        "--spring.profiles.active=prod")),
                withArgs.getJvmArgs());

        // A12/A13 项目级 APM 优先：已写 -javaagent 或 -Dskywalking.* 的项目一个字都不改
        Harness projectLevel = harness(release(14L, true, JAR, PREFIX, BACKEND));
        String ownAgent = json("-javaagent:/own/apm/agent.jar", "-Xmx256m");
        MsServerProject declared = project(114L, 14L, "demo", ownAgent);
        applyApm(projectLevel.impl, declared);
        check("A12 项目已自带 -javaagent 时原样保留（项目级配置优先）",
                ownAgent.equals(declared.getJvmArgs()), declared.getJvmArgs());

        Harness propOnly = harness(release(15L, true, JAR, PREFIX, BACKEND));
        String ownProp = json("-Dskywalking.collector.backend_service=own:11800");
        MsServerProject declaredByProp = project(115L, 15L, "demo", ownProp);
        applyApm(propOnly.impl, declaredByProp);
        check("A13 项目只写了 -Dskywalking.* 也算已声明 APM，不重复注入 agent",
                ownProp.equals(declaredByProp.getJvmArgs()), declaredByProp.getJvmArgs());

        // A14 幂等：同一项目连续两次注入不能叠加出第二组 -javaagent:
        Harness twice = harness(release(16L, true, JAR, PREFIX, BACKEND));
        MsServerProject repeat = project(116L, 16L, "demo", null);
        applyApm(twice.impl, repeat);
        String first = repeat.getJvmArgs();
        applyApm(twice.impl, repeat);
        check("A14 连续注入两次结果不变（重复同步不叠加第二组 agent）",
                first.equals(repeat.getJvmArgs()) && countOf(argsOf("A14", repeat), "-javaagent:") == 1,
                first + " -> " + repeat.getJvmArgs());

        // A15 裸参数串兜底：历史数据/手工填写不是 JSON 数组，也必须按空白切分后保留
        Harness bare = harness(release(17L, true, JAR, PREFIX, BACKEND));
        MsServerProject bareArgs = project(117L, 17L, "demo",
                "--server.port=8112 --spring.profiles.active=prod");
        applyApm(bare.impl, bareArgs);
        check("A15 裸参数串按空白切分后保留（不静默丢参）",
                argsOf("A15", bareArgs).equals(List.of(
                        "-javaagent:" + JAR,
                        "-Dskywalking.agent.service_name=" + PREFIX + "-demo",
                        "-Dskywalking.collector.backend_service=" + BACKEND,
                        "--server.port=8112",
                        "--spring.profiles.active=prod")),
                bareArgs.getJvmArgs());

        // A16 落库形态：必须是合法 JSON 数组且无空项，前端 jvmArgs 编辑器才能解析回列表
        List<String> shape = argsOf("A16", injected);
        check("A16 注入结果是合法 JSON 数组、无空项、无「null」字面量",
                injected.getJvmArgs().startsWith("[") && injected.getJvmArgs().endsWith("]")
                        && shape.stream().noneMatch(String::isBlank)
                        && !injected.getJvmArgs().contains("=null"),
                injected.getJvmArgs());

        // A17 敏感性对照：缺空值判断的实现会直接拼出 -javaagent:null，A16 与 A4~A7 才能判负
        String naive = "-javaagent:" + (String) null;
        check("A17 敏感性对照：「不判空就拼」会产出 -javaagent:null，证明 A4~A7、A16 非恒真",
                naive.contains("=null") || naive.equals("-javaagent:null"), naive);
    }

    // ==================== B. 新建项目触发点 ====================

    /**
     * B 段：{@code createProject} 落库前必须已把发布级 APM 合入 jvmArgs。
     */
    @Test
    void bCreateProject() {
        sectionBCreateProject();
        assertSectionDone("B");
    }

    private void sectionBCreateProject() {
        // B1 新建项目：接口创建（不走前端拼脚本）也必须带上发布级 APM
        Harness h = harness(release(1L, true, JAR, PREFIX, BACKEND));
        MsServerProject created = request(1L, "demo", null);
        ReturnResult<MsServerProject> result = h.impl.createProject(created);
        check("B0 createProject 成功", result.isOk(), String.valueOf(result.getMsg()));
        check("B1 createProject 落库前已注入三条 APM 参数",
                argsOf("B1", created).equals(List.of(
                        "-javaagent:" + JAR,
                        "-Dskywalking.agent.service_name=" + PREFIX + "-demo",
                        "-Dskywalking.collector.backend_service=" + BACKEND)),
                created.getJvmArgs());
        check("B1b 落库的实例就是被改写过的那一份（不是另存一份原始 jvmArgs）",
                h.projects.inserted.size() == 1
                        && created.getJvmArgs().equals(((MsServerProject) h.projects.inserted.get(0)).getJvmArgs()),
                String.valueOf(h.projects.inserted));

        // B2 项目自带 APM：新建时不得被发布级覆盖
        Harness h2 = harness(release(2L, true, JAR, PREFIX, BACKEND));
        String own = json("-javaagent:/own/agent.jar");
        MsServerProject withOwn = request(2L, "demo", own);
        h2.impl.createProject(withOwn);
        check("B2 新建项目已自带 APM 时不被发布级覆盖", own.equals(withOwn.getJvmArgs()), withOwn.getJvmArgs());

        // B3 发布未开启：新建项目不带 APM
        Harness h3 = harness(release(3L, false, JAR, PREFIX, BACKEND));
        MsServerProject plain = request(3L, "demo", null);
        h3.impl.createProject(plain);
        check("B3 发布未开启时新建项目不注入", plain.getJvmArgs() == null, plain.getJvmArgs());

        // B4 APM 注入不能挤掉内置脚本补全（两个后置处理互不干扰）
        check("B4 注入 APM 的同时四个脚本模板仍然补全",
                plain.getStartScript() != null && plain.getStopScript() != null
                        && plain.getRestartScript() != null && plain.getStatusScript() != null
                        && "stopped".equals(plain.getStatus()),
                "start=" + abbreviate(plain.getStartScript()));
        check("B4b 已注入 APM 的项目同样完成脚本补全", created.getStartScript() != null,
                "start=" + abbreviate(created.getStartScript()));
    }

    // ==================== C. 发布配置变更 → 同步下属项目 ====================

    /**
     * C 段：发布级 APM 配置变更后，同步到该发布下「未声明 APM」的项目。
     */
    @Test
    void cSyncOnReleaseUpdate() {
        sectionCSyncOnReleaseUpdate();
        assertSectionDone("C");
    }

    private void sectionCSyncOnReleaseUpdate() {
        // 基线：发布从「全关」改为「全开」，该发布下三个项目（含一个有自研 APM 的）
        Harness h = harness(release(1L, false, null, null, null));
        MsServerProject alpha = project(21L, 1L, "alpha", json("-Xmx1g"));
        MsServerProject beta = project(22L, 1L, "beta", null);
        String own = json("-javaagent:/own/agent.jar");
        MsServerProject gamma = project(23L, 1L, "gamma", own);
        h.projectsOf(alpha, beta, gamma);

        ReturnResult<MsServerRelease> updated = h.impl.updateRelease(1L, payload(true, JAR, PREFIX, BACKEND));
        check("C0 updateRelease 成功", updated.isOk(), String.valueOf(updated.getMsg()));

        check("C1 开关由关转开：项目原有参数保留，APM 三条补在最前",
                argsOf("C1", alpha).equals(List.of(
                        "-javaagent:" + JAR,
                        "-Dskywalking.agent.service_name=" + PREFIX + "-alpha",
                        "-Dskywalking.collector.backend_service=" + BACKEND,
                        "-Xmx1g")),
                alpha.getJvmArgs());
        check("C2 jvmArgs 为 null 的项目同样能被补上（null 不是注入障碍）",
                argsOf("C2", beta).equals(List.of(
                        "-javaagent:" + JAR,
                        "-Dskywalking.agent.service_name=" + PREFIX + "-beta",
                        "-Dskywalking.collector.backend_service=" + BACKEND)),
                beta.getJvmArgs());
        check("C3 项目级 APM 的项目被跳过：一个字不改，也不触发一次多余的 updateById",
                own.equals(gamma.getJvmArgs()) && h.projects.updated.size() == 2,
                "gamma=" + gamma.getJvmArgs() + " updated=" + h.projects.updated.size());
        check("C3b 敏感性对照：若同步不判「已声明 APM」，gamma 会变成两条 -javaagent（此处断言只有一条）",
                countOf(argsOf("C3b", gamma), "-javaagent:") == 1,
                gamma.getJvmArgs());

        // C4 重复同步（同一配置再保存一次）必须收敛：不出现第二组 agent
        int before = h.projects.updated.size();
        h.impl.updateRelease(1L, payload(true, JAR, PREFIX, BACKEND));
        check("C4 重复同步幂等：已有 APM 的项目不再被改写（无第二组 -javaagent）",
                h.projects.updated.size() == before
                        && countOf(argsOf("C4", alpha), "-javaagent:") == 1,
                "updated=" + h.projects.updated.size() + " alpha=" + alpha.getJvmArgs());

        // C5 变更 service_name 前缀：只对「已由发布注入过」的项目生效的前提是它们仍能被识别；
        // 这里锁死「同步时用的是当前发布前缀」，避免改前缀后项目里留着旧服务名。
        Harness h5 = harness(release(2L, false, null, null, null));
        MsServerProject delta = project(24L, 2L, "delta", null);
        h5.projectsOf(delta);
        h5.impl.updateRelease(2L, payload(true, JAR, "svc", BACKEND));
        check("C5 同步使用当前发布配置的前缀拼 service_name",
                argsOf("C5", delta).contains("-Dskywalking.agent.service_name=svc-delta"),
                delta.getJvmArgs());
    }

    // ==================== D. 关闭/失效 → 清理 APM 行 ====================

    /**
     * D 段：开关关闭、jar 或 OAP 地址失效时必须清理「本发布注入的」APM 行；
     * 项目自己声明的 APM（自研 agent、自有 OAP 地址）一律不动。
     *
     * <p>这一段是「关闭开关到底有没有用」的唯一证据：注入是把三条参数复制进项目 jvmArgs，
     * 落库后发布侧再无记录可查，若清理只按「项目里有没有 skywalking 参数」判断，
     * 关闭开关就永远清不掉（清空分支成为死代码），而按「有就删」又会删掉项目自研 agent。</p>
     */
    @Test
    void dClearOnDisable() {
        sectionDClearOnDisable();
        assertSectionDone("D");
    }

    private void sectionDClearOnDisable() {
        // D1 关闭开关 → 本发布注入的三行被清掉，项目自有参数保留
        Harness h = harness(release(1L, true, JAR, PREFIX, BACKEND));
        MsServerProject alpha = project(21L, 1L, "alpha", json(
                "-javaagent:" + JAR,
                "-Dskywalking.agent.service_name=" + PREFIX + "-alpha",
                "-Dskywalking.collector.backend_service=" + BACKEND,
                "-Xmx1g"));
        h.projectsOf(alpha);
        h.impl.updateRelease(1L, payload(false, null, null, null));
        check("D1 关闭开关后本发布注入的 APM 三行被清理、项目自有参数保留",
                json("-Xmx1g").equals(alpha.getJvmArgs()), alpha.getJvmArgs());

        // D2 项目级 APM 永远不受发布级开关影响（发布侧不接管别人的 agent）
        Harness h2 = harness(release(2L, true, JAR, PREFIX, BACKEND));
        String own = json("-javaagent:/own/agent.jar", "-Dskywalking.collector.backend_service=own:11800");
        MsServerProject gamma = project(22L, 2L, "gamma", own);
        h2.projectsOf(gamma);
        h2.impl.updateRelease(2L, payload(false, null, null, null));
        check("D2 项目级 APM 不会被发布级开关清掉", own.equals(gamma.getJvmArgs()), gamma.getJvmArgs());

        // D3 人工改造过 agent（换成自研 agent）→ 视为项目自有，不清
        Harness h3 = harness(release(3L, true, JAR, PREFIX, BACKEND));
        String custom = json("-javaagent:/custom/apm/agent.jar",
                "-Dskywalking.collector.backend_service=" + BACKEND);
        MsServerProject custom_ = project(23L, 3L, "beta", custom);
        h3.projectsOf(custom_);
        h3.impl.updateRelease(3L, payload(false, null, null, null));
        check("D3 项目已自行改造成自研 agent 时不被清理（清理只认本发布注入的那套）",
                custom.equals(custom_.getJvmArgs()), custom_.getJvmArgs());

        // D4 jar 路径被清空但开关仍开：等价于「配置失效」，仍要能认出并清理
        Harness h4 = harness(release(4L, true, JAR, PREFIX, BACKEND));
        MsServerProject beta = project(24L, 4L, "beta", json(
                "-javaagent:" + JAR,
                "-Dskywalking.agent.service_name=" + PREFIX + "-beta",
                "-Dskywalking.collector.backend_service=" + BACKEND,
                "-Xmx3g"));
        h4.projectsOf(beta);
        h4.impl.updateRelease(4L, payload(null, "", null, null));
        check("D4 jar 路径被清空（开关仍开）时按「前缀+OAP 地址」认出并清理",
                json("-Xmx3g").equals(beta.getJvmArgs()), beta.getJvmArgs());

        // D5 OAP 地址被清空：注入条件不成立，已注入的也要清（此时靠 agent 路径认出）
        Harness h5 = harness(release(5L, true, JAR, PREFIX, BACKEND));
        MsServerProject omega = project(25L, 5L, "omega", json(
                "-javaagent:" + JAR,
                "-Dskywalking.agent.service_name=" + PREFIX + "-omega",
                "-Dskywalking.collector.backend_service=" + BACKEND));
        h5.projectsOf(omega);
        h5.impl.updateRelease(5L, payload(null, null, null, ""));
        check("D5 OAP 地址被清空时本发布注入的行被清理",
                "[]".equals(omega.getJvmArgs()), omega.getJvmArgs());
        check("D5b 清理后是合法空 JSON 数组而不是 null/裸串",
                argsOf("D5b", omega).isEmpty(), omega.getJvmArgs());

        // D6 清理只清 APM 行，用户参数一个不动（顺序也保持）
        Harness h6 = harness(release(6L, true, JAR, PREFIX, BACKEND));
        MsServerProject keep = project(26L, 6L, "keep", json(
                "-Xmx512m",
                "-javaagent:" + JAR,
                "-Dskywalking.agent.service_name=" + PREFIX + "-keep",
                "-Dskywalking.collector.backend_service=" + BACKEND,
                "--spring.profiles.active=prod"));
        h6.projectsOf(keep);
        h6.impl.updateRelease(6L, payload(false, null, null, null));
        check("D6 清理后用户参数顺序与内容完全保留",
                json("-Xmx512m", "--spring.profiles.active=prod").equals(keep.getJvmArgs()),
                keep.getJvmArgs());

        // D7 从未被注入过的项目：关闭开关时不该产生任何写入（不写空数组、不动 updateTime）
        Harness h7 = harness(release(7L, true, JAR, PREFIX, BACKEND));
        MsServerProject never = project(27L, 7L, "never", json("-Xmx1g"));
        h7.projectsOf(never);
        h7.impl.updateRelease(7L, payload(false, null, null, null));
        check("D7 关闭开关时对「从未注入过」的项目不做任何写入",
                h7.projects.updated.isEmpty() && json("-Xmx1g").equals(never.getJvmArgs()),
                "updated=" + h7.projects.updated.size() + " jvmArgs=" + never.getJvmArgs());

        // D8 前缀为空时不做归属判定：宁可留着让用户手动清，也不能误删项目自研 agent
        Harness h8 = harness(release(8L, true, JAR, null, BACKEND));
        String ambiguous = json("-javaagent:/own/agent.jar", "-Dskywalking.agent.service_name=omega",
                "-Dskywalking.collector.backend_service=" + BACKEND);
        MsServerProject ambiguousProject = project(28L, 8L, "omega", ambiguous);
        h8.projectsOf(ambiguousProject);
        h8.impl.updateRelease(8L, payload(false, null, null, null));
        check("D8 无前缀时 service_name 与项目名同形，不做归属判定（保留原样，不误删）",
                ambiguous.equals(ambiguousProject.getJvmArgs()), ambiguousProject.getJvmArgs());
    }

    // ==================== E. 复制项目 → 按目标发布注入 ====================

    /**
     * E 段：{@code copyProjects} 把项目复制到别的发布时，必须按「目标发布」的 APM 配置注入。
     */
    @Test
    void eCopyProjects() {
        sectionECopyProjects();
        assertSectionDone("E");
    }

    private void sectionECopyProjects() {
        // E1 源发布没有 APM，目标发布有 → 副本带目标发布的 agent/OAP 与「目标前缀-源项目名」
        Harness h = harness(release(1L, false, null, null, null));
        h.release(release(2L, true, JAR2, "prod", BACKEND2));
        MsServerProject source = project(31L, 1L, "alpha", json("-Xmx1g"));
        source.setPid("4321");
        source.setStatus("running");
        source.setServiceName("ms-alpha");
        h.projects.byId.put(31L, source);

        ReturnResult<Map<String, Object>> copied = h.impl.copyProjects(List.of(31L), List.of(2L));
        check("E0 copyProjects 成功且计数为 1",
                copied.isOk() && Integer.valueOf(1).equals(copied.getData().get("count")),
                String.valueOf(copied.getData()));
        MsServerProject copy = asProject(h, 0);
        check("E1 副本按目标发布注入 agent/OAP 与「目标前缀-源项目名」，源参数保留在后",
                argsOf("E1", copy).equals(List.of(
                        "-javaagent:" + JAR2,
                        "-Dskywalking.agent.service_name=prod-alpha",
                        "-Dskywalking.collector.backend_service=" + BACKEND2,
                        "-Xmx1g")),
                copy.getJvmArgs());
        check("E2 副本的复制语义未被 APM 注入破坏（id/pid/状态/服务名清空，指向目标发布）",
                copy.getId() == null && Long.valueOf(2L).equals(copy.getReleaseId())
                        && "stopped".equals(copy.getStatus())
                        && copy.getPid() == null && copy.getServiceName() == null,
                "id=" + copy.getId() + " releaseId=" + copy.getReleaseId()
                        + " status=" + copy.getStatus() + " pid=" + copy.getPid()
                        + " serviceName=" + copy.getServiceName());

        // E3 目标发布未开启 → 副本不带 APM，源参数原样
        Harness h3 = harness(release(3L, false, null, null, null));
        h3.release(release(4L, false, JAR, PREFIX, BACKEND));
        MsServerProject src3 = project(32L, 3L, "beta", json("-Xmx2g"));
        h3.projects.byId.put(32L, src3);
        h3.impl.copyProjects(List.of(32L), List.of(4L));
        check("E3 目标发布未开启时副本不带 APM",
                json("-Xmx2g").equals(asProject(h3, 0).getJvmArgs()), asProject(h3, 0).getJvmArgs());

        // E4 源项目自带 APM → 副本保留源 APM（项目级优先，跨发布也不被目标发布覆盖）
        Harness h4 = harness(release(5L, true, JAR, PREFIX, BACKEND));
        h4.release(release(6L, true, JAR2, "prod", BACKEND2));
        String own = json("-javaagent:/own/agent.jar");
        MsServerProject src4 = project(33L, 5L, "gamma", own);
        h4.projects.byId.put(33L, src4);
        h4.impl.copyProjects(List.of(33L), List.of(6L));
        check("E4 源项目自带 APM 时副本保留源 APM（不被目标发布覆盖）",
                own.equals(asProject(h4, 0).getJvmArgs()), asProject(h4, 0).getJvmArgs());

        // E5 一次复制到两个发布：每个副本各按自己的目标发布注入
        Harness h5 = harness(release(7L, false, null, null, null));
        h5.release(release(8L, true, JAR, "a", BACKEND));
        h5.release(release(9L, true, JAR2, "b", BACKEND2));
        MsServerProject src5 = project(34L, 7L, "delta", null);
        h5.projects.byId.put(34L, src5);
        h5.impl.copyProjects(List.of(34L), List.of(8L, 9L));
        List<String> first = h5.projects.inserted.isEmpty() ? List.of()
                : argsOf("E5a", asProject(h5, 0));
        List<String> second = h5.projects.inserted.size() < 2 ? List.of()
                : argsOf("E5b", asProject(h5, 1));
        check("E5 同一源项目复制到两个发布，副本各自使用目标发布的 agent/OAP/前缀",
                h5.projects.inserted.size() == 2
                        && first.contains("-javaagent:" + JAR)
                        && first.contains("-Dskywalking.agent.service_name=a-delta")
                        && second.contains("-javaagent:" + JAR2)
                        && second.contains("-Dskywalking.agent.service_name=b-delta"),
                "first=" + first + " second=" + second);

        // E6 目标发布不存在时如实失败，且不落库半个项目
        Harness h6 = harness(release(10L, false, null, null, null));
        MsServerProject src6 = project(35L, 10L, "epsilon", null);
        h6.projects.byId.put(35L, src6);
        ReturnResult<Map<String, Object>> failed = h6.impl.copyProjects(List.of(35L), List.of(404L));
        check("E6 目标发布不存在时记入 failed 且不产生副本",
                failed.isOk() && h6.projects.inserted.isEmpty()
                        && ((List<?>) failed.getData().get("failed")).size() == 1,
                String.valueOf(failed.getData()));
    }

    // ==================== F. APM 参数判定契约 ====================

    /**
     * F 段：{@code isApmArg} 的识别范围必须与注入的三条参数互为闭环。
     *
     * <p>这是「幂等」与「项目级优先」两条规则能够成立的前提：只要有一条注入参数识别不了，
     * 下一次同步就会再注入一组 {@code -javaagent:}，JVM 直接起不来。</p>
     */
    @Test
    void fArgContract() {
        sectionFArgContract();
        assertSectionDone("F");
    }

    private void sectionFArgContract() {
        Harness h = harness(release(1L, true, JAR, PREFIX, BACKEND));
        MsServerProject project = project(11L, 1L, "demo", null);
        applyApm(h.impl, project);
        List<String> args = argsOf("F1", project);

        check("F1 注入的三条参数全部能被 isApmArg 识别（否则二次同步会重复注入）",
                args.size() == 3 && args.stream().allMatch(a -> apm(h.impl, a)), String.valueOf(args));
        check("F2 普通 JVM 参数不被误判为 APM（-Xmx / -Dserver.port / 空串）",
                !apm(h.impl, "-Xmx512m") && !apm(h.impl, "-Dserver.port=8112")
                        && !apm(h.impl, "") && !apm(h.impl, "--spring.profiles.active=prod"),
                "-Xmx512m / -Dserver.port=8112 / '' / --spring...");
        check("F3 敏感性对照：近似写法必须判负（-Dskywalking 无点、-Dskywalking_agent 下划线、javaagent: 无短横线）",
                !apm(h.impl, "-Dskywalking") && !apm(h.impl, "-Dskywalking_agent=x")
                        && !apm(h.impl, "javaagent:/x.jar"),
                "近似写法被判为 APM 会让 F1/F2 失去意义");
        check("F4 两条 -Dskywalking.* 参数的键名与采集端约定一致",
                args.contains("-Dskywalking.agent.service_name=" + PREFIX + "-demo")
                        && args.contains("-Dskywalking.collector.backend_service=" + BACKEND),
                String.valueOf(args));
    }

    // ==================== G. 业务端口解析 ====================

    /**
     * G 段：概览探测与健康检查使用的端口必须能读懂项目 appConfig 的 JSON 形态。
     */
    @Test
    void gResolveHealthPort() {
        Harness h = harness();
        MsServerProject project = project(41L, 1L, "port-demo", null);

        project.setAppConfig("[{\"key\":\"server.port\",\"value\":\"18090\"}]");
        check("G1 appConfig JSON 中的 server.port 可被解析", portOf(h.impl, project) == 18090,
                "port=" + portOf(h.impl, project));

        project.setCmdArgs(json("--server.port=18091"));
        check("G2 显式命令参数优先于 appConfig", portOf(h.impl, project) == 18091,
                "port=" + portOf(h.impl, project));

        project.setCmdArgs("[]");
        project.setJvmArgs(json("-Dserver.port=18092"));
        check("G3 JVM 参数优先于 appConfig", portOf(h.impl, project) == 18092,
                "port=" + portOf(h.impl, project));

        project.setJvmArgs("[]");
        project.setAppConfig("server.port=18093");
        check("G4 历史裸串 server.port= 仍兼容", portOf(h.impl, project) == 18093,
                "port=" + portOf(h.impl, project));

        project.setAppConfig("[{\"key\":\"server.port\",\"value\":\"99999\"}]");
        check("G5 非法端口不参与探测", portOf(h.impl, project) == 0,
                "port=" + portOf(h.impl, project));

        assertSectionDone("G");
    }

    /**
     * 反射解析项目端口。
     *
     * @param impl    服务实例
     * @param project 项目
     * @return 端口；无法解析为 0
     */
    private static int portOf(ReleaseServiceImpl impl, MsServerProject project) {
        return (Integer) invoke(impl, "resolveHealthPort", new Class<?>[] {MsServerProject.class}, project);
    }

    // ==================== 桩与断言基础设施 ====================

    /**
     * 判断某个参数是否会被生产代码认定为「项目已声明 APM」。
     *
     * @param impl ReleaseServiceImpl 实例
     * @param arg  参数
     * @return 是 APM 参数时为 {@code true}
     */
    private static boolean apm(ReleaseServiceImpl impl, String arg) {
        Object out = invoke(impl, "isApmArg", new Class<?>[] {String.class}, arg);
        return Boolean.TRUE.equals(out);
    }

    /**
     * 「开关 / jar / OAP 缺失时不注入」的矩阵断言：注入前 jvmArgs 必须保持原样。
     *
     * @param enabled 开关
     * @param jar     jar 路径
     * @param prefix  前缀
     * @param backend OAP 地址
     * @return 未被改写时为 {@code true}
     */
    private static boolean untouched(Boolean enabled, String jar, String prefix, String backend) {
        Harness h = harness(release(1L, enabled, jar, prefix, backend));
        String before = json("-Xmx512m");
        MsServerProject p = project(11L, 1L, "demo", before);
        applyApm(h.impl, p);
        return before.equals(p.getJvmArgs());
    }

    /**
     * 通过反射调用私有的 {@code applyReleaseApm}。
     *
     * @param impl    服务实例
     * @param project 项目
     */
    private static void applyApm(ReleaseServiceImpl impl, MsServerProject project) {
        invoke(impl, "applyReleaseApm", new Class<?>[] {MsServerProject.class}, project);
    }

    /**
     * 反射调用（私有方法一律经此入口，便于统一解包异常原因）。
     *
     * @param target 目标对象
     * @param name   方法名
     * @param types  参数类型
     * @param args   实参
     * @return 方法返回值
     */
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("反射调用 " + name + " 抛出异常: " + cause, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射调用 " + name + " 失败: " + e, e);
        }
    }

    /**
     * 断言某项目的 jvmArgs 解析为参数列表（解析失败直接算失败，而不是静默返回空）。
     *
     * @param tag 断言标签（用于失败信息）
     * @param p   项目
     * @return 参数列表
     */
    private static List<String> argsOf(String tag, MsServerProject p) {
        String json = p.getJvmArgs();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw failFast(tag + " jvmArgs 不是合法 JSON 数组，无法解析", json + " / " + e.getMessage());
        }
    }

    /**
     * 无法继续解析时的失败出口（现场文本一次性带出，便于定位是哪个 jvmArgs 被写坏）。
     *
     * @param name   断言名
     * @param detail 现场文本
     * @return 不会返回，仅为便于在 {@code throw} 表达式中使用
     */
    private static IllegalStateException failFast(String name, String detail) {
        return new IllegalStateException(name + " <<< " + abbreviate(detail));
    }

    /**
     * 列表里以某前缀开头的元素个数。
     *
     * @param args   参数列表
     * @param prefix 前缀
     * @return 个数
     */
    private static int countOf(List<String> args, String prefix) {
        int n = 0;
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 取桩记录里第 i 个被 insert 的项目。
     *
     * @param h Harness
     * @param i 下标
     * @return 项目
     */
    private static MsServerProject asProject(Harness h, int i) {
        if (h.projects.inserted.size() <= i) {
            throw new IllegalStateException("没有第 " + i + " 个被插入的项目，实际 "
                    + h.projects.inserted.size() + " 个");
        }
        return (MsServerProject) h.projects.inserted.get(i);
    }

    /**
     * 构造一条完整的发布（含主键，模拟库里已有的记录）。
     *
     * @param id      主键
     * @param enabled 开关
     * @param jar     agent jar 路径
     * @param prefix  service_name 前缀
     * @param backend OAP 地址
     * @return 发布
     */
    private static MsServerRelease release(Long id, Boolean enabled, String jar, String prefix, String backend) {
        MsServerRelease release = new MsServerRelease();
        release.setId(id);
        release.setReleaseName("发布-" + id);
        release.setSwEnabled(enabled);
        release.setSwJarPath(jar);
        release.setSwServiceNamePrefix(prefix);
        release.setSwBackendService(backend);
        return release;
    }

    /**
     * 构造一次「发布更新」请求体（只带 APM 四项，模拟前端只提交变更的字段）。
     *
     * @param enabled 开关
     * @param jar     agent jar 路径
     * @param prefix  前缀
     * @param backend OAP 地址
     * @return 请求体
     */
    private static MsServerRelease payload(Boolean enabled, String jar, String prefix, String backend) {
        MsServerRelease release = new MsServerRelease();
        release.setSwEnabled(enabled);
        release.setSwJarPath(jar);
        release.setSwServiceNamePrefix(prefix);
        release.setSwBackendService(backend);
        return release;
    }

    /**
     * 构造一条已存在的项目记录。
     *
     * @param id        主键
     * @param releaseId 发布主键
     * @param name      项目名
     * @param jvmArgs   jvmArgs 原始串
     * @return 项目
     */
    private static MsServerProject project(Long id, Long releaseId, String name, String jvmArgs) {
        MsServerProject project = new MsServerProject();
        project.setId(id);
        project.setReleaseId(releaseId);
        project.setProjectName(name);
        project.setProjectType("springboot");
        project.setMainEntry("demo.jar");
        project.setJvmArgs(jvmArgs);
        return project;
    }

    /**
     * 构造一次「新建项目」请求体（无主键）。
     *
     * @param releaseId 发布主键
     * @param name      项目名
     * @param jvmArgs   jvmArgs 原始串
     * @return 项目
     */
    private static MsServerProject request(Long releaseId, String name, String jvmArgs) {
        MsServerProject project = project(null, releaseId, name, jvmArgs);
        project.setProjectPath("/opt/app/" + name);
        project.setLogPath("/opt/app/" + name + "/logs/app.log");
        return project;
    }

    /**
     * 按 JSON 数组语义拼一个 jvmArgs 串（与前端 JSON.stringify(string[]) 等价）。
     *
     * @param items 参数项
     * @return JSON 数组串
     */
    private static String json(String... items) {
        try {
            return JSON.writeValueAsString(List.of(items));
        } catch (Exception e) {
            throw new IllegalStateException("构造 jvmArgs 失败: " + e, e);
        }
    }

    /**
     * 构造只带桩 Mapper 的服务实例。
     *
     * @param releases 预置发布
     * @return Harness
     */
    private static Harness harness(MsServerRelease... releases) {
        Harness h = new Harness();
        for (MsServerRelease release : releases) {
            h.release(release);
        }
        return h;
    }

    /**
     * 段结束时统一判定：本段内不允许有失败断言。
     *
     * @param section 段标识
     */
    private void assertSectionDone(String section) {
        if (failures.isEmpty()) {
            return;
        }
        Assertions.fail("段 " + section + " 有 " + failures.size() + " 条断言失败："
                + String.join(" | ", failures));
    }

    /**
     * 记录一条断言结果。
     *
     * @param name   断言名
     * @param ok     是否通过
     * @param detail 失败时的现场文本
     */
    private void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            return;
        }
        failures.add(name + "  <<< " + abbreviate(detail));
    }

    /**
     * 截断现场文本，避免刷屏。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String one = text.replace('\n', ' ');
        return one.length() <= 220 ? one : one.substring(0, 220) + "...";
    }

    /**
     * 测试夹具：四个 Mapper 桩 + 一个真实的 ReleaseServiceImpl。
     *
     * <p>刻意 new 真实实现并只替换 Mapper：断言的是「生产代码把 jvmArgs 改成什么样」，
     * 不是探针自己复刻的一套注入规则（探针复刻规则只会证明探针自己是对的）。</p>
     */
    private static final class Harness {

        /**
         * 发布表桩。
         */
        private final Stub releases = new Stub();

        /**
         * 项目表桩。
         */
        private final Stub projects = new Stub();

        /**
         * 主机表桩（APM 链路不读主机，保持空实现即可）。
         */
        private final Stub hosts = new Stub();

        /**
         * 系统配置表桩（APM 链路不读配置，保持空实现即可）。
         */
        private final Stub configs = new Stub();

        /**
         * 被测服务。
         */
        private final ReleaseServiceImpl impl;

        private Harness() {
            impl = new ReleaseServiceImpl(
                    proxy(MsServerReleaseMapper.class, releases),
                    proxy(MsServerProjectMapper.class, projects),
                    proxy(MsServerHostMapper.class, hosts),
                    null,
                    null,
                    null,
                    null,
                    new PackageManagerRouter(),
                    new SystemServiceProviderRegistry(),
                    new ProjectPlatformProviderRegistry(),
                    new ProjectTypeProviderRegistry(),
                    proxy(MsServerConfigMapper.class, configs),
                    null);
        }

        /**
         * 预置一条发布。
         *
         * @param release 发布
         */
        private void release(MsServerRelease release) {
            releases.byId.put(release.getId(), release);
        }

        /**
         * 预置某个查询返回的项目列表。
         *
         * @param ps 项目
         */
        private void projectsOf(MsServerProject... ps) {
            projects.rows = new ArrayList<>(List.of(ps));
        }
    }

    /**
     * MyBatis-Plus BaseMapper 的调用桩。
     *
     * <p>只实现 APM 链路真正会用到的方法：{@code selectById} / {@code selectList} /
     * {@code insert} / {@code updateById}。其余方法（本链路不会走到）按返回类型给空值，
     * 避免桩自己抛异常把断言带偏。</p>
     */
    private static final class Stub implements InvocationHandler {

        /**
         * 主键 → 记录（selectById 的返回值）。
         */
        private final Map<Serializable, Object> byId = new LinkedHashMap<>();

        /**
         * selectList 的返回值。
         */
        private List<?> rows = Collections.emptyList();

        /**
         * 被 insert 的实体（按调用顺序）。
         */
        private final List<Object> inserted = new ArrayList<>();

        /**
         * 被 updateById 的实体（按调用顺序）。
         */
        private final List<Object> updated = new ArrayList<>();

        /**
         * selectById 被调用的次数（用于断言「提前返回，不做无谓查询」）。
         */
        private int selectByIdCalls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "selectById":
                    selectByIdCalls++;
                    return byId.get(args[0]);
                case "selectList":
                    return rows;
                case "insert":
                    inserted.add(args[0]);
                    return 1;
                case "updateById":
                    updated.add(args[0]);
                    return 1;
                case "toString":
                    return "MapperStub";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return fallback(method.getReturnType());
            }
        }

        /**
         * 未覆盖方法的兜底返回值。
         *
         * @param type 返回类型
         * @return 该类型的空值
         */
        private static Object fallback(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == char.class) {
                return (char) 0;
            }
            if (type == float.class) {
                return 0F;
            }
            if (type == double.class) {
                return 0D;
            }
            if (type == Optional.class) {
                return Optional.empty();
            }
            if (type == List.class) {
                return new ArrayList<>();
            }
            if (type == Map.class) {
                return new LinkedHashMap<>();
            }
            return null;
        }
    }

    /**
     * 生成 Mapper 接口的动态代理。
     *
     * @param type    Mapper 接口
     * @param handler 调用桩
     * @param <T>     Mapper 类型
     * @return 代理实例
     */
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(ReleaseApmProbe.class.getClassLoader(),
                new Class<?>[] {type}, handler));
    }
}
