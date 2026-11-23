// [P3C A20 豁免] A20/A30/A31: 含日志/界面文案等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.platform.PlatformAccessGlobalSettingProvider;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 平台访问密钥（ONNX / Web）校验服务。
 *
 * <p>配置中心 {@code platform} 分组（{@link PlatformAccessGlobalSettingProvider}，
 * 仅管理员可配置与查看）保存两个平台的访问密钥；本服务对「使用平台」的请求做
 * 逐请求校验：</p>
 * <ul>
 *   <li>模型目录：{@code GET /api/providers}、{@code GET /api/provider-models}
 *       对无有效密钥的非管理员隐藏对应平台的模型；</li>
 *   <li>对话使用：{@code /v2/chat/stream}、{@code /v2/chat/resume}、{@code /v2/chat/send}
 *       对 web:/onnx: 前缀模型校验请求头密钥；</li>
 *   <li>Web 浏览器推理：前端开聊前必须先调 {@code POST /v2/platform-keys/verify}
 *       由后端校验（安全性校验不放在前端）。</li>
 * </ul>
 *
 * <p>口径：密钥未配置（留空）= 该平台未启用访问控制，放行；已配置时管理员豁免，
 * 非管理员必须携带匹配密钥。密钥比对用 {@link MessageDigest#isEqual} 恒定时间比较。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccessKeyService {

    /**
     * ONNX 平台标识
     */
    public static final String PLATFORM_ONNX = "onnx";

    /**
     * Web 浏览器推理平台标识
     */
    public static final String PLATFORM_WEB = "web";

    /**
     * ONNX 平台密钥请求头
     */
    public static final String HEADER_ONNX_APP_KEY = "X-Onnx-App-Key";

    /**
     * Web 平台密钥请求头
     */
    public static final String HEADER_WEB_APP_KEY = "X-Web-App-Key";

    /**
     * 全局设置读取器（直读全局层：管理员专属字段不存在用户覆盖层）
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 规范化平台标识。
     *
     * @param platform 平台标识（忽略大小写与首尾空白）
     * @return onnx / web；未知平台返回 null
     */
    public String normalizePlatform(String platform) {
        String p = platform == null ? "" : platform.trim().toLowerCase();
        if (PLATFORM_ONNX.equals(p) || PLATFORM_WEB.equals(p)) {
            return p;
        }
        return null;
    }

    /**
     * 平台对应的配置字段名。
     *
     * @param platform 已规范化的平台标识
     * @return 配置字段名
     */
    public String fieldFor(String platform) {
        return PLATFORM_ONNX.equals(platform)
                ? PlatformAccessGlobalSettingProvider.FIELD_ONNX_APP_KEY
                : PlatformAccessGlobalSettingProvider.FIELD_WEB_APP_KEY;
    }

    /**
     * 平台对应的密钥请求头名。
     *
     * @param platform 已规范化的平台标识
     * @return 请求头名
     */
    public String headerNameFor(String platform) {
        return PLATFORM_ONNX.equals(platform) ? HEADER_ONNX_APP_KEY : HEADER_WEB_APP_KEY;
    }

    /**
     * 读取平台当前配置的密钥（全局层直读）。
     *
     * @param platform 平台标识（onnx / web，忽略大小写）
     * @return 配置的密钥；未配置或平台未知返回 null
     */
    public String configuredKey(String platform) {
        String p = normalizePlatform(platform);
        if (p == null) {
            return null;
        }
        try {
            return globalSettingReader.getStrDirect(PlatformAccessGlobalSettingProvider.GROUP, fieldFor(p));
        } catch (Exception e) {
            log.warn("[PlatformKey] 读取平台密钥失败(platform={}): {}", p, e.getMessage());
            return null;
        }
    }

    /**
     * 平台是否已启用访问控制（配置了非空密钥）。
     *
     * @param platform 平台标识
     * @return true=已启用（请求需携带匹配密钥）
     */
    public boolean isPlatformEnabled(String platform) {
        String configured = configuredKey(platform);
        return configured != null && !configured.isBlank();
    }

    /**
     * 校验平台密钥。
     *
     * @param platform 平台标识（onnx / web，忽略大小写）
     * @param key      请求携带的密钥，可为空
     * @return true=允许访问（未启用校验、管理员、或密钥匹配）
     */
    public boolean verify(String platform, String key) {
        String p = normalizePlatform(platform);
        if (p == null) {
            return false;
        }
        String configured = configuredKey(p);
        if (configured == null || configured.isBlank()) {
            // 未配置密钥 = 未启用访问控制
            return true;
        }
        if (AiUserContext.isAdmin()) {
            return true;
        }
        if (key == null || key.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.trim().getBytes(StandardCharsets.UTF_8),
                key.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从模型复合 id 解析受限平台。
     *
     * <p>前端模型下拉的 id 形态是 {@code <providerId>:<model>}；web / onnx 两个
     * 平台的 providerId 即平台标识，因此按前缀判定。其它平台返回 null（不受限）。</p>
     *
     * @param rawModelId 模型复合 id 或裸模型名，可为空
     * @return onnx / web；非受限模型返回 null
     */
    public String platformOfModel(String rawModelId) {
        if (rawModelId == null) {
            return null;
        }
        String s = rawModelId.trim().toLowerCase();
        int idx = s.indexOf(':');
        if (idx <= 0) {
            return null;
        }
        String prefix = s.substring(0, idx);
        if (PLATFORM_ONNX.equals(prefix) || PLATFORM_WEB.equals(prefix)) {
            return prefix;
        }
        return null;
    }

    /**
     * 断言本次对话允许使用该模型（web / onnx 平台逐请求密钥门禁）。
     *
     * @param rawModelId 模型复合 id（如 web:demo / onnx:xxx）
     * @param onnxKey    请求头携带的 ONNX 密钥，可为空
     * @param webKey     请求头携带的 Web 密钥，可为空
     * @throws ApiException 非管理员且密钥缺失/不匹配时抛 403
     */
    public void assertChatModelAllowed(String rawModelId, String onnxKey, String webKey) {
        String platform = platformOfModel(rawModelId);
        if (platform == null) {
            return;
        }
        String key = PLATFORM_ONNX.equals(platform) ? onnxKey : webKey;
        if (!verify(platform, key)) {
            log.warn("[PlatformKey] 拒绝使用平台模型: platform={}, model={}", platform, rawModelId);
            throw new ApiException(ApiErrorCode.FORBIDDEN,
                    "使用 " + platform.toUpperCase() + " 平台模型需要有效的访问密钥（请求头 "
                            + headerNameFor(platform) + "），请联系管理员获取或在弹窗中输入");
        }
    }
}
