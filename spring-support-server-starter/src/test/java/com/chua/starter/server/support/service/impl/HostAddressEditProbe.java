package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 主机地址编辑回归探针。
 *
 * <p>需求口径：地址字段填什么就存什么，不解析 {@code user@host}。
 * 这里直接调用 {@link ServerHostServiceImpl#update(Long, MsServerHost)}，
 * 断言写到 Mapper 的地址与提交值完全一致——历史上后端会把
 * {@code user@host} 拆成「地址=host、账号=user」，表现为「改了地址保存后又变回去」。</p>
 *
 * <p>只用到 hostMapper：update 的其余分支（VNC 口令、自动建身份）在本用例的
 * 输入下都会提前返回，其它构造依赖传 null 即可。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class HostAddressEditProbe {

    /**
     * 编辑地址为含 @ 的字面量时应原样落库。
     */
    @Test
    void updateKeepsAddressLiteral() {
        MsServerHost exists = new MsServerHost();
        exists.setId(11L);
        exists.setServerHostName("rdp-test-198");
        exists.setServerHostAddress("192.168.50.198");
        exists.setServerHostPort(22);
        exists.setServerHostOsType("WINDOWS");
        exists.setServerHostCollectMode("AGENT");
        exists.setServerHostRemoteMode("GUACD");

        MsServerHost[] captured = new MsServerHost[1];
        MsServerHostMapper hostMapper = (MsServerHostMapper) Proxy.newProxyInstance(
                MsServerHostMapper.class.getClassLoader(),
                new Class<?>[]{MsServerHostMapper.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "selectById":
                            return exists;
                        case "updateById":
                            captured[0] = (MsServerHost) args[0];
                            return 1;
                        case "toString":
                            return "MsServerHostMapperProbe";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });

        ServerHostServiceImpl service = new ServerHostServiceImpl(
                hostMapper, null, null, null, null, null, null, null, null, null, null);

        MsServerHost patch = new MsServerHost();
        patch.setServerHostAddress("ibssh_demo@ssh.instabox.sh");
        service.update(11L, patch);

        assertEquals("ibssh_demo@ssh.instabox.sh", captured[0].getServerHostAddress(),
                "地址应原样保存，不得拆出 user@ 前缀");
    }

    /**
     * 反射代理的默认返回值：对象 null、布尔 false、数字 0。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
