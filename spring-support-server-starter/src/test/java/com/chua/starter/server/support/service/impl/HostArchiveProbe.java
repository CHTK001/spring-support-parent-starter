package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chua.starter.server.support.controller.HostArchiveController;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.model.vo.HostArchiveImportVO;
import com.chua.starter.server.support.model.vo.HostArchiveVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主机资产归档回归探针（导出 → 导入 → 再导入）。
 *
 * <p>这条链路有三个「静默出错」的高危点，用真实数据库测反而不容易看见：</p>
 * <ol>
 *   <li><b>口令被二次加密</b>：导入端若调用 {@code encrypt}，库里会变成「密文的密文」，
 *       连接时才炸；因此断言导入后的值<b>逐字等于</b>归档里的值；</li>
 *   <li><b>重复导入产生重复数据</b>：同一份文件点两次，主机 / 身份 / 绑定都必须只命中不新增；</li>
 *   <li><b>NOT NULL 列被写成 null</b>：归档里缺 {@code sshEnabled} 时按系统类型推断，
 *       Windows 应为 0、Linux 应为 1，否则开关语义会反。</li>
 * </ol>
 *
 * <p>Mapper 用动态代理 + 内存列表模拟：{@code LambdaQueryWrapper} 的等值条件通过
 * {@code getParamNameValuePairs()} 取出实参来匹配，因此不需要连数据库。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
class HostArchiveProbe {

    /**
     * SSH 默认端口
     */
    private static final int SSH_PORT = 22;

    /**
     * RDP 默认端口
     */
    private static final int RDP_PORT = 3389;

    /**
     * 测试用杂项 TCP 端口（导出源主机）
     */
    private static final int TEST_PORT = 2222;

    /**
     * 采集团隔秒数（默认 1 小时）
     */
    private static final int COLLECT_INTERVAL_S = 3600;

    /**
     * 初始化 MyBatis-Plus 的 lambda 列缓存，使条件包装器能在无 Spring 容器下渲染出列名与实参。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("hostArchiveProbe");
        TableInfoHelper.initTableInfo(assistant, MsServerHost.class);
        TableInfoHelper.initTableInfo(assistant, MsServerIdentity.class);
        TableInfoHelper.initTableInfo(assistant, MsServerHostIdentity.class);
    }

    /**
     * 导出再导入：三层数据完整还原，口令保持原样。
     */
    @Test
    void exportThenImportRestoresAllLayers() {
        FakeStore src = new FakeStore();
        MsServerHost host = new MsServerHost();
        host.setId(src.nextId());
        host.setServerHostName("prod-web");
        host.setServerHostAddress("10.0.0.11");
        host.setServerHostPort(TEST_PORT);
        host.setServerHostOsType("LINUX");
        host.setServerHostSshEnabled(1);
        host.setServerHostAccount("root");
        src.hosts.add(host);

        MsServerIdentity identity = new MsServerIdentity();
        identity.setId(src.nextId());
        identity.setMsServerIdentityName("prod-web@root");
        identity.setMsServerIdentityUsername("root");
        identity.setMsServerIdentityAuthType("PASSWORD");
        identity.setMsServerIdentityPassword("AESHEX_CIPHERTEXT");
        src.identities.add(identity);

        MsServerHostIdentity rel = new MsServerHostIdentity();
        rel.setId(src.nextId());
        rel.setMsServerHostIdentityHostId(host.getId());
        rel.setMsServerHostIdentityIdentityId(identity.getId());
        rel.setMsServerHostIdentitySort(1);
        src.relations.add(rel);

        HostArchiveVO archive = new HostArchiveServiceImpl(src.hostMapper(), src.identityMapper(),
                src.hostIdentityMapper()).export();
        assertEquals(HostArchiveVO.FORMAT, archive.format());
        assertEquals(1, archive.hosts().size());
        assertEquals(1, archive.identities().size());
        assertEquals(1, archive.bindings().size());
        assertEquals("AESHEX_CIPHERTEXT", archive.identities().get(0).password());

        FakeStore dst = new FakeStore();
        HostArchiveServiceImpl importer = new HostArchiveServiceImpl(dst.hostMapper(),
                dst.identityMapper(), dst.hostIdentityMapper());
        HostArchiveImportVO first = importer.importArchive(archive);

        assertEquals(1, first.hostsCreated());
        assertEquals(0, first.hostsMatched());
        assertEquals(1, first.identitiesCreated());
        assertEquals(1, first.bindingsCreated());
        assertTrue(first.warnings().isEmpty());
        assertEquals("10.0.0.11", dst.hosts.get(0).getServerHostAddress());
        assertEquals(TEST_PORT, dst.hosts.get(0).getServerHostPort());
        assertEquals("AESHEX_CIPHERTEXT", dst.identities.get(0).getMsServerIdentityPassword(),
                "口令必须原样落库，不能二次加密");
    }

    /**
     * 同一份归档导入两次：第二次全部命中，不新增任何行。
     */
    @Test
    void importIsIdempotent() {
        FakeStore src = new FakeStore();
        MsServerHost host = new MsServerHost();
        host.setId(src.nextId());
        host.setServerHostName("db");
        host.setServerHostAddress("10.0.0.12");
        host.setServerHostPort(SSH_PORT);
        host.setServerHostOsType("LINUX");
        src.hosts.add(host);

        MsServerIdentity identity = new MsServerIdentity();
        identity.setId(src.nextId());
        identity.setMsServerIdentityName("db@dba");
        identity.setMsServerIdentityUsername("dba");
        src.identities.add(identity);

        MsServerHostIdentity rel = new MsServerHostIdentity();
        rel.setId(src.nextId());
        rel.setMsServerHostIdentityHostId(host.getId());
        rel.setMsServerHostIdentityIdentityId(identity.getId());
        src.relations.add(rel);

        HostArchiveVO archive = new HostArchiveServiceImpl(src.hostMapper(), src.identityMapper(),
                src.hostIdentityMapper()).export();

        FakeStore dst = new FakeStore();
        HostArchiveServiceImpl importer = new HostArchiveServiceImpl(dst.hostMapper(),
                dst.identityMapper(), dst.hostIdentityMapper());
        importer.importArchive(archive);
        HostArchiveImportVO second = importer.importArchive(archive);

        assertEquals(0, second.hostsCreated());
        assertEquals(1, second.hostsMatched());
        assertEquals(0, second.identitiesCreated());
        assertEquals(1, second.identitiesMatched());
        assertEquals(0, second.bindingsCreated());
        assertEquals(1, second.bindingsSkipped());
        assertEquals(1, dst.hosts.size(), "重复导入不得新增主机");
        assertEquals(1, dst.identities.size(), "重复导入不得新增身份");
        assertEquals(1, dst.relations.size(), "重复导入不得新增绑定");
    }

    /**
     * 缺字段的容错：sshEnabled / osType / collectEnabled 为空时按平台默认补齐。
     */
    @Test
    void missingFieldsFallBackToPlatformDefaults() {
        FakeStore dst = new FakeStore();
        HostArchiveServiceImpl importer = new HostArchiveServiceImpl(dst.hostMapper(),
                dst.identityMapper(), dst.hostIdentityMapper());

        HostArchiveVO.HostItem linux = new HostArchiveVO.HostItem(null, null, "10.0.0.20", null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        HostArchiveVO.HostItem windows = new HostArchiveVO.HostItem(null, null, "10.0.0.21", RDP_PORT,
                "WINDOWS", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        HostArchiveVO archive = new HostArchiveVO(HostArchiveVO.FORMAT, 1, "2026-11-07T00:00:00",
                null, List.of(linux, windows), List.of(), List.of());

        importer.importArchive(archive);

        assertEquals(2, dst.hosts.size());
        MsServerHost first = dst.hosts.get(0);
        assertEquals("10.0.0.20", first.getServerHostName(), "缺名称时用地址兜底");
        assertEquals("LINUX", first.getServerHostOsType());
        assertEquals(SSH_PORT, first.getServerHostPort());
        assertEquals(1, first.getServerHostSshEnabled());
        assertEquals(0, first.getServerHostCollectEnabled());
        assertEquals("SCHEDULED", first.getServerHostCollectMode());
        assertEquals(COLLECT_INTERVAL_S, first.getServerHostCollectInterval());
        assertEquals(1, first.getServerHostStatus());
        assertEquals(-1, first.getServerHostOnline());
        assertEquals(0, dst.hosts.get(1).getServerHostSshEnabled(), "Windows 默认关 SSH");
    }

    /**
     * 绑定无法解析时只记提示，不影响主机与身份导入。
     */
    @Test
    void unresolvedBindingOnlyWarns() {
        FakeStore dst = new FakeStore();
        HostArchiveServiceImpl importer = new HostArchiveServiceImpl(dst.hostMapper(),
                dst.identityMapper(), dst.hostIdentityMapper());

        HostArchiveVO archive = new HostArchiveVO(HostArchiveVO.FORMAT, 1, "2026-11-07T00:00:00",
                null,
                List.of(new HostArchiveVO.HostItem("h", null, "10.0.0.30", SSH_PORT, "LINUX", null, null,
                        null, null, null, null, null, 1, null, null, null, null, null, null, null,
                        null, null, null)),
                List.of(new HostArchiveVO.IdentityItem("ghost@x", "PASSWORD", "x", "CIPHER", null,
                        null, null, null, null, null)),
                List.of(
                        new HostArchiveVO.BindingItem("10.0.0.30", SSH_PORT, "missing-name", 1),
                        new HostArchiveVO.BindingItem("10.0.0.99", SSH_PORT, "ghost@x", 1),
                        new HostArchiveVO.BindingItem("10.0.0.30", SSH_PORT, "ghost@x", 1)));

        HostArchiveImportVO result = importer.importArchive(archive);

        assertEquals(1, result.hostsCreated());
        assertEquals(1, result.identitiesCreated());
        assertEquals(1, result.bindingsCreated());
        assertEquals(2, result.bindingsSkipped());
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("missing-name")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("10.0.0.99")));
        assertFalse(dst.hosts.isEmpty());
        assertNotNull(dst.identities.get(0).getMsServerIdentityPassword());
    }

    /**
     * 归档 JSON 的对外契约：口令密文原样穿过序列化，未知字段不导致导入失败。
     *
     * <p>跨版本迁移时，新版本导出的归档可能有老版本没有的字段；老版本若用默认的
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} 会直接 400，用户看到的是「导入失败」而不是
     * 「少了一个字段」。因此这里断言多出来的字段被忽略、有效字段照常还原。</p>
     *
     * @throws Exception JSON 处理异常
     */
    @Test
    void jsonContractKeepsCiphertextAndIgnoresUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HostArchiveVO archive = new HostArchiveVO(HostArchiveVO.FORMAT, 1, "2026-11-07T00:00:00",
                "note",
                List.of(new HostArchiveVO.HostItem("h", "c", "10.0.0.40", SSH_PORT, "LINUX", null, null,
                        null, null, null, null, null, 1, null, null, null, null, null, null, null,
                        null, null, null)),
                List.of(new HostArchiveVO.IdentityItem("h@root", "PASSWORD", "root", "CIPHERTEXT",
                        null, null, null, null, null, null)),
                List.of(new HostArchiveVO.BindingItem("10.0.0.40", SSH_PORT, "h@root", 1)));
        String json = mapper.writeValueAsString(archive);
        assertTrue(json.contains("\"password\":\"CIPHERTEXT\""), json);

        String withExtra = json.replaceFirst("\\{", "{\"futureField\":{\"a\":1},");
        HostArchiveVO back = mapper.readValue(withExtra, HostArchiveVO.class);
        assertEquals(HostArchiveVO.FORMAT, back.format());
        assertEquals("CIPHERTEXT", back.identities().get(0).password());
        assertEquals(1, back.bindings().size());
    }

    /**
     * 控制器挂在 {@code /ms/server/host/archive}：前端走的就是这个前缀，改错会 404。
     */
    @Test
    void controllerExposesArchiveEndpoint() {
        RequestMapping mapping = HostArchiveController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping, "控制器缺少 @RequestMapping");
        assertTrue(java.util.Arrays.asList(mapping.value()).contains("/ms/server/host/archive"),
                java.util.Arrays.toString(mapping.value()));
    }

    /**
     * 内存台账 + 三个 Mapper 代理。
     */
    private static final class FakeStore {

        /**
         * 自增序列（与真实库一样从 100 开始，避免 id=0 干扰断言）
         */
        // [P3C A20 豁免]
        private final AtomicLong seq = new AtomicLong(100);

        private final List<MsServerHost> hosts = new ArrayList<>();

        private final List<MsServerIdentity> identities = new ArrayList<>();

        private final List<MsServerHostIdentity> relations = new ArrayList<>();

        /**
         * 分配主键。
         *
         * @return 新主键
         */
        long nextId() {
            return seq.incrementAndGet();
        }

        /**
         * 主机 Mapper。
         *
         * @return 代理
         */
        MsServerHostMapper hostMapper() {
            return (MsServerHostMapper) proxy(MsServerHostMapper.class, (name, args) -> {
                switch (name) {
                    case "selectList":
                        return new ArrayList<>(hosts);
                    case "selectOne":
                        String address = stringParam(args[0]);
                        Long port = numberParam(args[0]);
                        return hosts.stream()
                                .filter(h -> eq(h.getServerHostAddress(), address)
                                        && (port == null || eq(h.getServerHostPort(), port)))
                                .findFirst().orElse(null);
                    case "insert":
                        MsServerHost h = (MsServerHost) args[0];
                        if (h.getId() == null) {
                            h.setId(nextId());
                        }
                        hosts.add(h);
                        return 1;
                    default:
                        return null;
                }
            });
        }

        /**
         * 身份 Mapper。
         *
         * @return 代理
         */
        MsServerIdentityMapper identityMapper() {
            return (MsServerIdentityMapper) proxy(MsServerIdentityMapper.class, (name, args) -> {
                switch (name) {
                    case "selectList":
                        return new ArrayList<>(identities);
                    case "selectOne":
                        String identityName = stringParam(args[0]);
                        return identities.stream()
                                .filter(i -> eq(i.getMsServerIdentityName(), identityName))
                                .findFirst().orElse(null);
                    case "insert":
                        MsServerIdentity i = (MsServerIdentity) args[0];
                        if (i.getId() == null) {
                            i.setId(nextId());
                        }
                        identities.add(i);
                        return 1;
                    default:
                        return null;
                }
            });
        }

        /**
         * 主机-身份关联 Mapper。
         *
         * @return 代理
         */
        MsServerHostIdentityMapper hostIdentityMapper() {
            return (MsServerHostIdentityMapper) proxy(MsServerHostIdentityMapper.class, (name, args) -> {
                switch (name) {
                    case "selectList":
                        return new ArrayList<>(relations);
                    case "selectCount":
                        List<Long> ids = numberParams(args[0]);
                        long count = relations.stream()
                                .filter(r -> ids.contains(r.getMsServerHostIdentityHostId())
                                        && ids.contains(r.getMsServerHostIdentityIdentityId()))
                                .count();
                        return count;
                    case "insert":
                        MsServerHostIdentity r = (MsServerHostIdentity) args[0];
                        if (r.getId() == null) {
                            r.setId(nextId());
                        }
                        relations.add(r);
                        return 1;
                    default:
                        return null;
                }
            });
        }

        /**
         * 取出 wrapper 的等值实参。
         *
         * <p>MP 的 {@code eq} 把实参写进 {@code paramNameValuePairs} 的动作是<b>懒</b>的：
         * 要等 SQL 片段渲染才执行，所以这里先 {@code getSqlSegment()} 触发一次，
         * 否则读到的永远是空 map。键名是内部生成的、顺序也不保证，因此按值类型取。</p>
         *
         * @param wrapper 条件
         * @return 实参数组
         */
        private static Object[] values(Object wrapper) {
            AbstractWrapper<?, ?, ?> w = (AbstractWrapper<?, ?, ?>) wrapper;
            w.getSqlSegment();
            return w.getParamNameValuePairs().values().toArray();
        }

        /**
         * 取第一个字符串实参（主机地址 / 身份名称）。
         *
         * @param wrapper 条件
         * @return 字符串实参，没有则 null
         */
        private static String stringParam(Object wrapper) {
            for (Object v : values(wrapper)) {
                if (v instanceof String) {
                    return (String) v;
                }
            }
            return null;
        }

        /**
         * 取第一个数字实参（端口）。
         *
         * @param wrapper 条件
         * @return 数字实参，没有则 null
         */
        private static Long numberParam(Object wrapper) {
            for (Object v : values(wrapper)) {
                if (v instanceof Number) {
                    return ((Number) v).longValue();
                }
            }
            return null;
        }

        /**
         * 取全部数字实参（主机 ID + 身份 ID）。
         *
         * @param wrapper 条件
         * @return 数字实参列表
         */
        private static List<Long> numberParams(Object wrapper) {
            List<Long> out = new ArrayList<>();
            for (Object v : values(wrapper)) {
                if (v instanceof Number) {
                    out.add(((Number) v).longValue());
                }
            }
            return out;
        }

        /**
         * 宽松相等：Integer/Long 之间也按数值比较。
         *
         * @param left  库中值
         * @param right 条件值
         * @return true 表示相等
         */
        private static boolean eq(Object left, Object right) {
            if (left == null || right == null) {
                return java.util.Objects.equals(left, right);
            }
            if (left instanceof Number && right instanceof Number) {
                return ((Number) left).longValue() == ((Number) right).longValue();
            }
            return String.valueOf(left).equals(String.valueOf(right));
        }

        /**
         * 生成只实现所需方法的动态代理。
         *
         * @param type    Mapper 接口
         * @param handler 方法名 + 参数 → 返回值
         * @return 代理实例
         */
        private static Object proxy(Class<?> type, Handler handler) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "toString":
                                return type.getSimpleName() + "Probe";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return handler.invoke(method.getName(), args == null ? new Object[0] : args);
                        }
                    });
        }

        /**
         * 方法处理钩子。
         */
        @FunctionalInterface
        private interface Handler {
            /**
             * 处理方法调用。
             *
             * @param methodName 方法名
             * @param args       实参
             * @return 返回值
             */
            Object invoke(String methodName, Object[] args);
        }
    }
}
