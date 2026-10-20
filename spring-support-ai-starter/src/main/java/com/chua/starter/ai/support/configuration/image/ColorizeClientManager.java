package com.chua.starter.ai.support.configuration.image;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.client.ModelIds;
import com.chua.starter.ai.support.configuration.client.ModelParamBridge;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图像上色客户端管理器。
 *
 * <p>{@code image} 分组允许每个用户覆盖自己的默认模型（见设置中心），
 * 因此缓存键是账号而不是单一共享单例：每个账号各自持有一份
 * {@link ImageEnhancer} 包装对象，互不串用。模型引擎仍由 deeplearning 侧
 * 复用（引擎内按 modelId 持有 translator），包装对象本身很轻。</p>
 *
 * <p><b>热更新</b>：与 {@link ImageRecognitionClientManager} 同一口径 ——
 * 每次调用先按当前配置算指纹（模型 + 设备），指纹变化即重建；同时监听
 * {@link FaceSettingsChangedEvent}（配置写入后由设置服务统一发布），
 * 命中 {@code image} 分组时立即失效缓存，管理员改完模型保存后无需重启即生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColorizeClientManager {

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "image";

    /**
     * 无登录身份（启动期 / 后台线程）时使用的缓存键
     */
    private static final String ANONYMOUS_KEY = "";

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * 按账号缓存的图像上色实例：账号 → 配置指纹 + 增强器
     */
    private final Map<String, CachedEnhancer> enhancerCache = new ConcurrentHashMap<>();

    /**
     * 图像上色（未配置模型时返回 空）。
     *
     * @param imageData 输入图像字节
     * @return 上色后的图像字节；模型未配置时返回 空
     */
    public byte[] colorize(byte[] imageData) {
        ImageEnhancer enhancer = currentEnhancer();
        if (enhancer == null) {
            return null;
        }
        return enhancer.enhance(imageData);
    }

    /**
     * 是否已配置图像上色模型。
     *
     * @return true 表示可用
     */
    public boolean available() {
        return currentEnhancer() != null;
    }

    /**
     * 当前生效的上色模型名。
     *
     * @return 模型名，未配置返回 空
     */
    public String model() {
        return StringUtils.defaultString(
                settingReader.getStrAs(currentUserIdOrNull(), GROUP, "colorize_model"), "");
    }

    /**
     * 图像上色配置变更后失效缓存，实现「保存即生效」。
     *
     * <p>用户级覆盖只影响触发者自己的条目，全局写入则整表失效；实际是否重建
     * 仍由下一次调用按指纹判定，因此即使事件在事务提交前到达、读到旧值，
     * 也不会把旧配置长期固化在缓存里。</p>
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onImageSettingsChanged(FaceSettingsChangedEvent event) {
        if (event == null || !GROUP.equals(event.group())) {
            return;
        }
        if (event.userScoped() && StringUtils.isNotBlank(event.userId())) {
            enhancerCache.remove(keyOf(event.userId()));
        } else {
            // 用户级事件未携带账号（SysAiUserSettingService 的统一发布口径）时按全量失效处理：
            // 多清一次只会让下一次调用按最新配置重建，不会串账号。
            enhancerCache.clear();
        }
        log.info("[Colorize] 图像上色配置变更，缓存已失效(userScoped={}, userId={})",
                event.userScoped(), event.userId());
    }

    /**
     * 清空全部缓存（运维/测试手动触发配置重载时使用）。
     */
    public void invalidate() {
        enhancerCache.clear();
    }

    /**
     * 取当前请求用户的上色器（配置变更自动重建，未配置返回 空）。
     *
     * @return 上色器或 空
     */
    private ImageEnhancer currentEnhancer() {
        String userId = currentUserIdOrNull();
        String model = modelValue(userId);
        String key = keyOf(userId);
        if (StringUtils.isBlank(model)) {
            // 模型被清空 = 关闭上色能力：顺手清掉该账号的旧实例，避免留着不可用配置
            enhancerCache.remove(key);
            return null;
        }
        String device = deviceValue(userId);
        String fingerprint = model + "|" + StringUtils.defaultString(device, "");
        CachedEnhancer cached = enhancerCache.get(key);
        if (cached != null && fingerprint.equals(cached.fingerprint())) {
            return cached.enhancer();
        }
        synchronized (this) {
            cached = enhancerCache.get(key);
            if (cached != null && fingerprint.equals(cached.fingerprint())) {
                return cached.enhancer();
            }
            // 设置页模型选择器保存的是 providerId:model（如 onnx:image-colorize），
            // 而 ModelRegistry / 推理引擎只认裸模型名，带前缀会「配置了却不生效」。
            String bareModel = bareModelId(model);
            ModelParamBridge.bridgeDevice(bareModel, device);
            ImageEnhancer enhancer = ImageEnhancer.create(bareModel);
            enhancerCache.put(key, new CachedEnhancer(fingerprint, enhancer));
            log.info("[Colorize] 图像上色实例已构建 model={} device={}", bareModel,
                    StringUtils.defaultString(device, ""));
            return enhancer;
        }
    }

    /**
     * 把设置页的模型引用收敛成引擎可识别的模型标识。
     *
     * <p>设置页选择模型时保存的是 {@code providerId:model}（用于同名模型消歧与回显），
     * 引擎按裸模型名精确匹配，因此这里剥掉前缀；绝对路径（外部目录绑定后的模型标识
     * 就是目录本身）不按冒号拆分，原样返回。</p>
     *
     * @param model 配置中的模型引用
     * @return 裸模型标识
     */
    private static String bareModelId(String model) {
        if (StringUtils.isBlank(model)) {
            return model;
        }
        String v = model.trim();
        if (v.startsWith("/") || v.startsWith("\\")
                || (v.length() > 2 && v.charAt(1) == ':'
                && (v.charAt(2) == '/' || v.charAt(2) == '\\'))) {
            return v;
        }
        return ModelIds.bare(v);
    }

    /**
     * 读取模型配置值。
     *
     * @param userId 用户标识；为空表示只读全局层
     * @return 模型标识，未配置返回 空
     */
    private String modelValue(String userId) {
        return settingReader.getStrAs(userId, GROUP, "colorize_model");
    }

    /**
     * 读取推理设备配置值。
     *
     * @param userId 用户标识；为空表示只读全局层
     * @return 设备标识，未配置返回 空
     */
    private String deviceValue(String userId) {
        return settingReader.getStrAs(userId, GROUP, "colorize_device");
    }

    /**
     * 缓存键归一化：没有登录身份时统一落到匿名键。
     *
     * @param userId 用户标识
     * @return 缓存键
     */
    private static String keyOf(String userId) {
        return userId == null ? ANONYMOUS_KEY : userId;
    }

    /**
     * 取当前登录用户标识；无请求上下文（后台线程、未登录）时返回 空。
     *
     * @return 用户标识或 空
     */
    private static String currentUserIdOrNull() {
        try {
            return StringUtils.trimToNull(AiUserContext.currentUserId());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 缓存条目：配置指纹 + 增强器实例。
     *
     * @param fingerprint 模型 + 设备的配置指纹
     * @param enhancer    图像增强器
     */
    private record CachedEnhancer(String fingerprint, ImageEnhancer enhancer) {
    }
}
