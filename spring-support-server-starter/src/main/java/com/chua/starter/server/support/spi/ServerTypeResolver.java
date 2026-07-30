package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 服务器类型解析器。
 *
 * <p>统一收口 serverType 标准化逻辑，避免各管理器重复处理空值和大小写。
 *
 * @author CH
 */
public final class ServerTypeResolver {

    /**
     * 协议管理器，用于统一类型标准化。
     */
    private static final ServerHostProtocolManager PROTOCOL_MANAGER = new ServerHostProtocolManager();

    private ServerTypeResolver() {
    }

    /**
     * 解析主机协议类型，缺省回落 local。
     *
     * @param host 服务器主机
     * @return 规范化后的小写类型
     */
    public static String resolve(ServerHost host) {
        if (host == null) {
            return "local";
        }
        return PROTOCOL_MANAGER.normalizeType(host.getServerType()).toLowerCase(Locale.ROOT);
    }

    /**
     * 解析原始字符串类型，缺省回落 local。
     *
     * @param rawType 原始类型文本
     * @return 规范化后的小写类型
     */
    public static String resolve(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "local";
        }
        return PROTOCOL_MANAGER.normalizeType(rawType).toLowerCase(Locale.ROOT);
    }
}
