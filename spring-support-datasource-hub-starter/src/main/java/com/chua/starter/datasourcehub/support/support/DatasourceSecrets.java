package com.chua.starter.datasourcehub.support.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 数据源口令解码。
 *
 * <p>台账里的口令有两种存法：本模块新建的存明文，历史从采集平台
 * （{@code collect_datasource}，用 {@code b64:} 前缀的 Base64）迁过来的仍是编码值。
 * 带前缀才解码，无前缀原样返回，因此两种来源可以共存，
 * 迁移时口令列可以直接整列搬过去，不必逐行转换。</p>
 *
 * <p>接口出参不回传口令明文，页面只显示是否已设置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DatasourceSecrets {

    /**
     * 编码存储值前缀。
     */
    private static final String PREFIX = "b64:";

    /**
     * 工具类禁止实例化。
     */
    private DatasourceSecrets() {
    }

    /**
     * 存储值还原为明文。
     *
     * @param stored 存储值，可为 {@code b64:} 前缀的 Base64 或明文
     * @return 明文口令，空白输入返回空串
     */
    public static String decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        String text = stored.trim();
        if (!text.startsWith(PREFIX)) {
            return text;
        }
        try {
            return new String(Base64.getDecoder().decode(text.substring(PREFIX.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // 不是合法 Base64 时按原值处理，避免把用户口令直接改坏
            return text;
        }
    }
}
