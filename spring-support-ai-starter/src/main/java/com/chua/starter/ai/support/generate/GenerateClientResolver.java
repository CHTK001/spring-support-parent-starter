package com.chua.starter.ai.support.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageClientSetting;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoClientSetting;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.util.ProviderCredentials;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成客户端解析器：按模型名路由到对应供应商的 {@link ImageClient} / {@link VideoClient} 实现。
 *
 * <p>与对话侧 {@code AiChatFacadeServiceImpl#resolveChatClient} 同构：先按模型名查
 * {@code sys_account_channel_model} → {@code sys_account_channel}，命中则用该供应商的凭证动态构建；
 * 未命中再回退到 {@code generate} 分组全局配置。差别在于对话侧有 {@code ChatClientRegistry}
 * 常驻注册表，而生成侧是按需构建，故本类自带 TTL 缓存。</p>
 *
 * <p>SPI 名由 {@link GenerateSpiRouter} 从 {@code platform} 归一得到，业务标识与实现名解耦。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateClientResolver {

    /**
     * 渠道业务（账号模块）：渠道行与启用口径
     */
    private final AccountChannelService channelService;

    /**
     * 提供者模型映射器
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 构建结果缓存（key = 类型::供应商::模型，value = 构建时间戳）
     */
    private final Map<String, Long> clientCache = new ConcurrentHashMap<>();

    /**
     * 已构建客户端缓存：key 同上，value 为客户端本体。
     *
     * <p>生成客户端持有 HTTP 连接，缓存让其可复用；变更配置后由
     * {@link #invalidate(String)} 清理。</p>
     */
    private final Map<String, Object> clientHolder = new ConcurrentHashMap<>();

    /**
     * 客户端缓存有效期（毫秒）：5 分钟
     */
    private static final long CLIENT_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    /**
     * 解析结果：客户端 + 命中的模型配置
     *
     * @param client   底层客户端，未命中为 null
     * @param provider 命中的供应商，未命中为 null
     * @param modelCfg 命中的模型行，未命中为 null
     */
    public record Resolved<T>(T client, SysAccountChannel provider, SysAccountChannelModel modelCfg) {

        /**
         * 是否解析成功
         *
         * @return true 表示拿到可用客户端
         */
        public boolean ok() {
            return client != null;
        }
    }

    /**
     * 解析图像生成客户端。
     *
     * @param model 模型名，可为空（为空时仅按全局默认配置兜底）
     * @return 解析结果
     */
    public Resolved<ImageClient> resolveImage(String model) {
        return resolveImage(model, 0);
    }

    /**
     * 解析图像生成客户端（指定用第几个 API Key）。
     *
     * @param model   模型名，可为空
     * @param keyIndex API Key 下标，越界时回退到第一个
     * @return 解析结果
     */
    public Resolved<ImageClient> resolveImage(String model, int keyIndex) {
        SysAccountChannel provider = resolveProviderByModel(model);
        if (provider == null) {
            provider = resolveKeylessImageProvider(model);
        }
        if (provider == null) {
            return new Resolved<>(null, null, null);
        }
        SysAccountChannelModel modelCfg = resolveModelRow(model);
        String spi = GenerateSpiRouter.imageSpi(provider.getPlatform());
        if ((spi.isEmpty() || !GenerateSpiRouter.supportsImage(provider.getPlatform()))
                && KeylessGenerateCatalog.ownsImageModel(model)) {
            // 防御回退：免登录出图模型的行可能被渠道页保存路径挂在其它渠道名下
            //（该渠道并没有出图 SPI 实现），只要免费渠道聚合行可用就仍路由回去，
            // 避免生图被这类脏行劫持。
            SysAccountChannel free = resolveKeylessImageProvider(model);
            if (free != null) {
                provider = free;
                spi = GenerateSpiRouter.imageSpi(provider.getPlatform());
            }
        }
        if (spi.isEmpty() || !GenerateSpiRouter.supportsImage(provider.getPlatform())) {
            log.warn("[Generate] 供应商无图像生成实现 platform={} spi={}", provider.getPlatform(), spi);
            return new Resolved<>(null, provider, modelCfg);
        }
        String cacheKey = "image::" + provider.getProviderId() + "::" + spi + "::k" + keyIndex;
        ImageClient cached = (ImageClient) clientHolder.get(cacheKey);
        if (cached != null && !expired(cacheKey)) {
            return new Resolved<>(applyImageModel(cached, model), provider, modelCfg);
        }
        try {
            ImageClientSetting setting = ImageClientSetting.builder()
                    .provider(spi)
                    .appKey(keyAt(provider, keyIndex))
                    .secretKey(ProviderCredentials.secretKey(provider.getCredentials()))
                    .baseUrl(provider.getBaseUrl())
                    .build();
            ImageClient client = ImageClient.create(setting);
            if (client == null) {
                return new Resolved<>(null, provider, modelCfg);
            }
            clientHolder.put(cacheKey, client);
            clientCache.put(cacheKey, System.currentTimeMillis());
            return new Resolved<>(applyImageModel(client, model), provider, modelCfg);
        } catch (Exception e) {
            log.warn("[Generate] 构建图像客户端失败 spi={}: {}", spi, e.getMessage());
            return new Resolved<>(null, provider, modelCfg);
        }
    }

    /**
     * 供应商可用的 API Key 数量。
     *
     * <p>限流时靠换 Key 重试（模型不变），故需要知道有几个可换的。</p>
     *
     * @param provider 供应商
     * @return Key 数量，至少为 1
     */
    public int keyCount(SysAccountChannel provider) {
        return provider == null ? 1
                : Math.max(1, ProviderCredentials.apiKeys(provider.getCredentials()).size());
    }

    /**
     * 取指定下标的 API Key。
     *
     * @param provider 供应商
     * @param index    下标
     * @return Key 文本；下标越界时回退到第一个
     */
    private static String keyAt(SysAccountChannel provider, int index) {
        List<String> keys = ProviderCredentials.apiKeys(provider.getCredentials());
        if (keys.isEmpty()) {
            return null;
        }
        return index >= 0 && index < keys.size() ? keys.get(index) : keys.getFirst();
    }

    /**
     * 解析视频生成客户端。
     *
     * @param model 模型名，可为空
     * @return 解析结果
     */
    public Resolved<VideoClient> resolveVideo(String model) {
        return resolveVideo(model, 0);
    }

    /**
     * 解析视频生成客户端（指定用第几个 API Key）。
     *
     * @param model   模型名，可为空
     * @param keyIndex API Key 下标，越界时回退到第一个
     * @return 解析结果
     */
    public Resolved<VideoClient> resolveVideo(String model, int keyIndex) {
        SysAccountChannel provider = resolveProviderByModel(model);
        if (provider == null) {
            provider = resolveKeylessVideoProvider(model);
        }
        if (provider == null) {
            return new Resolved<>(null, null, null);
        }
        SysAccountChannelModel modelCfg = resolveModelRow(model);
        String spi = GenerateSpiRouter.videoSpi(provider.getPlatform());
        if ((spi.isEmpty() || !GenerateSpiRouter.supportsVideo(provider.getPlatform()))
                && KeylessGenerateCatalog.ownsVideoModel(model)) {
            // 防御回退：免登录视频模型的行可能被渠道页保存路径挂在其它渠道名下
            //（该渠道并没有视频 SPI 实现），只要免费渠道聚合行可用就仍路由回去，
            // 避免视频生成被这类脏行劫持。
            SysAccountChannel free = resolveKeylessVideoProvider(model);
            if (free != null) {
                provider = free;
                spi = GenerateSpiRouter.videoSpi(provider.getPlatform());
            }
        }
        if (spi.isEmpty() || !GenerateSpiRouter.supportsVideo(provider.getPlatform())) {
            log.warn("[Generate] 供应商无视频生成实现 platform={} spi={}", provider.getPlatform(), spi);
            return new Resolved<>(null, provider, modelCfg);
        }
        String cacheKey = "video::" + provider.getProviderId() + "::" + spi + "::k" + keyIndex;
        VideoClient cached = (VideoClient) clientHolder.get(cacheKey);
        if (cached != null && !expired(cacheKey)) {
            return new Resolved<>(applyVideoModel(cached, model), provider, modelCfg);
        }
        try {
            VideoClientSetting setting = VideoClientSetting.builder()
                    .provider(spi)
                    .appKey(keyAt(provider, keyIndex))
                    .baseUrl(provider.getBaseUrl())
                    .build();
            VideoClient client = VideoClient.create(setting);
            if (client == null) {
                return new Resolved<>(null, provider, modelCfg);
            }
            clientHolder.put(cacheKey, client);
            clientCache.put(cacheKey, System.currentTimeMillis());
            return new Resolved<>(applyVideoModel(client, model), provider, modelCfg);
        } catch (Exception e) {
            log.warn("[Generate] 构建视频客户端失败 spi={}: {}", spi, e.getMessage());
            return new Resolved<>(null, provider, modelCfg);
        }
    }

    /**
     * 清理指定供应商的客户端缓存（配置变更后调用）。
     *
     * @param providerId 提供商标识
     */
    public void invalidate(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        String fragment = "::" + providerId + "::";
        clientHolder.keySet().removeIf(k -> k.contains(fragment));
        clientCache.keySet().removeIf(k -> k.contains(fragment));
    }

    /**
     * 把模型名写入客户端的链式配置。
     *
     * @param client 图像客户端
     * @param model  模型名
     * @return 同一客户端（链式接口返回 this）
     */
    private static ImageClient applyImageModel(ImageClient client, String model) {
        return model == null || model.isBlank() ? client : client.model(model);
    }

    /**
     * 把模型名写入客户端的链式配置。
     *
     * @param client 视频客户端
     * @param model  模型名
     * @return 同一客户端（链式接口返回 this）
     */
    private static VideoClient applyVideoModel(VideoClient client, String model) {
        return model == null || model.isBlank() ? client : client.model(model);
    }

    /**
     * 判断缓存是否过期。
     *
     * @param key 缓存键
     * @return true 表示已过期
     */
    private boolean expired(String key) {
        Long builtAt = clientCache.get(key);
        return builtAt == null || System.currentTimeMillis() - builtAt >= CLIENT_CACHE_TTL_MILLIS;
    }

    /**
     * 按模型名查已启用的供应商。
     *
     * @param model 模型名
     * @return 供应商，未命中返回 null
     */
    private SysAccountChannel resolveProviderByModel(String model) {
        SysAccountChannelModel row = resolveModelRow(model);
        if (row == null) {
            return null;
        }
        try {
            SysAccountChannel provider = channelService.find(row.getProviderId());
            return provider != null && Boolean.TRUE.equals(provider.getEnabled()) ? provider : null;
        } catch (Exception e) {
            log.warn("[Generate] 解析供应商失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 免登录出图模型的兜底供应商解析。
     *
     * <p>模型物化进 {@code sys_account_channel_model} 之前（旧库未跑物化器），
     * {@code image-pollinations} 的 {@code flux} / {@code turbo} 按模型名查不到
     * 供应商；但出图本身是通的：账号模块的 {@code FreeChannelImageClient}
     * （SPI {@code free-channel}）会按模型名反查内置渠道再转交。因此当模型确实
     * 由某个免登录出图渠道声明时，把它解析到「免费渠道」聚合行。</p>
     *
     * <p>库里已有该模型的行时以库为权威：全部行都被停用说明管理员已关掉它，
     * 这里必须拒绝，兜底不得再放行；仅当库里完全没有该模型的行时才走本兜底。</p>
     *
     * <p>只在 {@link KeylessGenerateCatalog#ownsImageModel(String)} 成立时兜底，
     * 保证任意未配置的模型名不会被误判成可执行。</p>
     *
     * @param model 模型名
     * @return 免费渠道聚合行；不适用时返回 {@code null}
     */
    private SysAccountChannel resolveKeylessImageProvider(String model) {
        if (!KeylessGenerateCatalog.ownsImageModel(model)) {
            return null;
        }
        if (resolveAnyModelRow(model) != null && resolveModelRow(model) == null) {
            // 模型已入库且没有已启用行：管理员已停用，兜底不放行
            log.debug("[Generate] 模型已停用，免登录兜底不放行 model={}", model);
            return null;
        }
        try {
            SysAccountChannel free = channelService.findByPlatform(ProviderRouteTargets.FREE_CHANNEL_PLATFORM);
            return free != null && Boolean.TRUE.equals(free.getEnabled()) ? free : null;
        } catch (Exception e) {
            log.warn("[Generate] 解析免费渠道失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 免登录文生视频模型的兜底供应商解析。
     *
     * <p>模型物化进 {@code sys_account_channel_model} 之前（旧库未跑物化器），
     * {@code video-doubao} / {@code video-qwen} 的内置模型按模型名查不到
     * 供应商；但生成链路是通的：账号模块的 {@code FreeChannelVideoClient}
     * （SPI {@code free-channel}）会按模型名反查内置渠道再转交。因此当模型确实
     * 由某个免登录文生视频渠道声明时，把它解析到「免费渠道」聚合行。</p>
     *
     * <p>库里已有该模型的行时以库为权威：全部行都被停用说明管理员已关掉它，
     * 这里必须拒绝，兜底不得再放行；仅当库里完全没有该模型的行时才走本兜底。</p>
     *
     * <p>只在 {@link KeylessGenerateCatalog#ownsVideoModel(String)} 成立时兜底，
     * 保证任意未配置的模型名不会被误判成可执行。</p>
     *
     * @param model 模型名
     * @return 免费渠道聚合行；不适用时返回 {@code null}
     */
    private SysAccountChannel resolveKeylessVideoProvider(String model) {
        if (!KeylessGenerateCatalog.ownsVideoModel(model)) {
            return null;
        }
        if (resolveAnyModelRow(model) != null && resolveModelRow(model) == null) {
            // 模型已入库且没有已启用行：管理员已停用，兜底不放行
            log.debug("[Generate] 模型已停用，免登录兜底不放行 model={}", model);
            return null;
        }
        try {
            SysAccountChannel free = channelService.findByPlatform(ProviderRouteTargets.FREE_CHANNEL_PLATFORM);
            return free != null && Boolean.TRUE.equals(free.getEnabled()) ? free : null;
        } catch (Exception e) {
            log.warn("[Generate] 解析免费渠道失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 按模型名查模型行（不限启用状态）。
     *
     * <p>用于判断「库里是否已有这个模型」：有行即视为库为权威，
     * 免登录兜底不得越过库里的启停状态。</p>
     *
     * @param model 模型名
     * @return 模型行（含停用行）；没有匹配行返回 {@code null}
     */
    private SysAccountChannelModel resolveAnyModelRow(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        try {
            return channelModelService.findByModel(model);
        } catch (Exception e) {
            log.warn("[Generate] 查询模型行(不限状态)失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 已启用的模型行（按主键顺序）。
     *
     * <p>业务侧在「没有在设置里点名模型」时需要自己挑一个能用的：
     * 逐个解析会把没有 SPI 实现的供应商也试一遍，故由这里统一给出候选集。</p>
     *
     * @return 已启用的模型行；查询异常时返回空列表
     */
    public List<SysAccountChannelModel> listEnabledModels() {
        try {
            return channelModelService.listAllEnabled();
        } catch (Exception e) {
            log.warn("[Generate] 查询已启用模型失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按模型名查已启用的模型行。
     *
     * @param model 模型名
     * @return 模型行，未命中返回 null
     */
    private SysAccountChannelModel resolveModelRow(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        try {
            return channelModelService.findEnabledByModel(model);
        } catch (Exception e) {
            log.warn("[Generate] 解析模型行失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }
}
