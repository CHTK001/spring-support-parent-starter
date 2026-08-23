package com.chua.starter.ai.support.nomifun.service;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nomifun 契约业务服务（厚逻辑层）。
 *
 * <p>nomifun 前端（pages/ai，React SPA）以「桌面壳」模式运行（注入
 * {@code window.__backendPort}），所有业务命令走本地网关
 * {@code http://127.0.0.1:8787/api/*} + {@code ws://127.0.0.1:8787/ws}。
 * 本服务聚合现有 AI 中台 service，实现 nomifun 契约所需的最小数据面。
 * 控制器层仅做协议翻译（轻量壳），业务逻辑全部集中在此。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NomifunContractService {

    /**
     * 全局设置服务：读取客户端配置等
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 客户端设置分组
     */
    private static final String CLIENT_GROUP = "nomifun.client";

    /**
     * 系统信息响应字段（nomifun 前端 {@code /api/system/info} 契约）。
     *
     * <p>前端 {@code application.systemInfo.invoke()} 期望：
     * {@code cache_dir/work_dir/log_dir/storage_generation/platform/arch}，
     * 缺失会导致启动时 {@code BackendRequestError} 崩溃。</p>
     *
     * @return 系统信息 Map
     */
    public Map<String, Object> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        String userDir = System.getProperty("user.dir");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String platform;
        if (osName == null || osName.toLowerCase().contains("win")) {
            platform = "windows";
        } else if (osName.toLowerCase().contains("mac")) {
            platform = "macos";
        } else {
            platform = "linux";
        }
        String dir = userDir == null ? "." : userDir;
        info.put("cache_dir", dir + "/.cache");
        info.put("work_dir", dir);
        info.put("log_dir", dir + "/logs");
        // 前端契约要求 canonical lowercase UUIDv7，前端以此为 localStorage 代际隔离
        info.put("storage_generation", uuidV7());
        info.put("platform", platform);
        info.put("arch", osArch == null ? "x86_64" : osArch);
        return info;
    }

    /**
     * 生成 canonical lowercase UUIDv7 字符串（RFC 9562）。
     *
     * <p>nomifun 前端对 storage_generation 做严格正则校验：
     * {@code ^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$}。</p>
     *
     * @return UUIDv7 字符串
     */
    private String uuidV7() {
        long millis = System.currentTimeMillis();
        byte[] bytes = new byte[16];

        // 先生成随机数填充
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(bytes);

        // 前6字节：时间戳（大端序）
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;

        // 设置版本号为7（第6字节高4位）
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);

        // 设置变体为10xx（第8字节高2位）
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        return formatUuid(bytes);
    }

    /**
     * 将 16 字节按 RFC 9562 布局格式化为小写 UUID 字符串。
     *
     * @param bytes 16 字节
     * @return 小写 UUID 字符串
     */
    private String formatUuid(byte[] bytes) {
        StringBuilder sb = new StringBuilder(36);
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                sb.append('-');
            }
            sb.append(hex[(bytes[i] >> 4) & 0xF]).append(hex[bytes[i] & 0xF]);
        }
        return sb.toString();
    }

    /**
     * 读取客户端设置（{@code /api/settings/client}）。
     * 当前聚合自全局设置表的 {@code nomifun.client} 分组，未配置则返回空对象。</p>
     *
     * @return 客户端设置 Map
     */
    public Map<String, Object> clientSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = settingService.list(CLIENT_GROUP);
        for (Map<String, Object> item : items) {
            Object field = item.get("field");
            Object value = item.get("value");
            if (field != null && value != null) {
                result.put(String.valueOf(field), value);
            }
        }
        return result;
    }

    /**
     * 更新客户端设置（{@code PUT /api/settings/client}）。
     *
     * @param body 前端提交的完整设置对象
     * @return 无返回
     */
    public void updateClientSettings(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            settingService.upsertValue(CLIENT_GROUP, entry.getKey(), String.valueOf(entry.getValue()), null);
        }
    }
}
