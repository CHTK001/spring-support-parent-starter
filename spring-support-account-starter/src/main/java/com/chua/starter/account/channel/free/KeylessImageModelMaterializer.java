// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.free;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.channel.mapper.SysAccountChannelMapper;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.common.support.channel.KeylessMediaChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免登录出图模型的物化器：把渠道 SPI 声明的内置模型落进模型管理。
 *
 * <p>背景：{@code image-pollinations} 这类免登录出图渠道没有密钥，历史上
 * {@code sys_account_channel_model} 里没有它们的模型行，生图目录靠 ai 侧从渠道
 * SPI 临时补行 —— 模型管理页因此看不到、也停不掉这些模型。本类在启动时把
 * 「声明了文生图任务」的免登录渠道的内置模型统一物化到 {@code free-channel}
 * 聚合渠道行下（capabilities 标 {@code image_generation}），此后生图目录以库行为
 * 唯一来源，管理员在模型管理里启停即可生效。</p>
 *
 * <p>物化幂等：已存在的模型行不覆盖（管理员改过的别名 / 开关 / 参数保持原样），
 * 只补新出现的模型；只写本模块自己的表，不涉及 ai 模块。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeylessImageModelMaterializer implements ApplicationRunner {

    /**
     * 免费渠道聚合行的平台标识（与 ai 侧路由目标一致）
     */
    private static final String FREE_CHANNEL_PLATFORM = "free-channel";

    /**
     * 文生图任务类型
     */
    private static final String TASK_IMAGE = "image_generation";

    /**
     * 文生图任务别名
     */
    private static final String TASK_TEXT_TO_IMAGE = "text-to-image";

    /**
     * 渠道业务：渠道行定位
     */
    private final AccountChannelService channelService;

    /**
     * 渠道业务：模型行批量补录
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 渠道行写入（惰性创建聚合行用）
     */
    private final SysAccountChannelMapper channelMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            materialize();
        } catch (Exception e) {
            // 物化失败不阻断启动：下次启动会重新补录
            log.warn("[KeylessMaterialize] 免登录出图模型物化失败: {}", e.getMessage());
        }
    }

    /**
     * 执行一次物化：确保聚合行存在 → 读渠道 SPI → 幂等补录模型行。
     */
    private synchronized void materialize() {
        SysAccountChannel free = channelService.findByPlatform(FREE_CHANNEL_PLATFORM);
        if (free == null) {
            free = ensureFreeChannelRow();
        }
        List<SysAccountChannelModel> candidates = collectImageCandidates();
        if (candidates.isEmpty()) {
            log.info("[KeylessMaterialize] 免登录出图渠道未声明模型，跳过物化");
            return;
        }
        int imported = channelModelService.createMissing(free.getProviderId(), candidates);
        log.info("[KeylessMaterialize] 免登录出图模型物化完成: channel={} 候选 {} 条 新增 {} 条",
                free.getProviderId(), candidates.size(), imported);
    }

    /**
     * 惰性创建免费渠道聚合行。
     *
     * <p>聚合行没有上游地址与密钥（免登录渠道的出图实现按模型名反查内置渠道），
     * 认证方式落 {@code none}；platform 固定为 {@code free-channel}，
     * ai 侧按该值路由到 {@code FreeChannelImageClient}。</p>
     *
     * @return 新建的聚合行
     */
    private SysAccountChannel ensureFreeChannelRow() {
        long now = System.currentTimeMillis();
        SysAccountChannel row = new SysAccountChannel();
        row.setProviderId(FREE_CHANNEL_PLATFORM);
        row.setPlatform(FREE_CHANNEL_PLATFORM);
        row.setName("免费渠道");
        row.setBaseUrl("");
        row.setAuthScheme("none");
        row.setEnabled(true);
        row.setSortOrder(channelMapper.selectCount(null).intValue());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        channelMapper.insert(row);
        log.info("[KeylessMaterialize] 惰性创建免费渠道聚合行: {}", FREE_CHANNEL_PLATFORM);
        return row;
    }

    /**
     * 从渠道 SPI 收集「声明了文生图任务」的内置模型候选行。
     *
     * @return 候选行（不含 providerId，由 {@code createMissing} 统一落渠道）
     */
    private List<SysAccountChannelModel> collectImageCandidates() {
        Map<String, KeylessMediaChannel> channels;
        try {
            channels = ServiceProvider.of(KeylessMediaChannel.class).list();
        } catch (Exception e) {
            log.warn("[KeylessMaterialize] 读取免登录渠道 SPI 失败: {}", e.getMessage());
            return List.of();
        }
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        List<SysAccountChannelModel> candidates = new ArrayList<>();
        for (KeylessMediaChannel channel : channels.values()) {
            if (channel == null
                    || !KeylessMediaChannel.declaresTask(channel, TASK_IMAGE, TASK_TEXT_TO_IMAGE)) {
                continue;
            }
            List<Map<String, Object>> models;
            try {
                models = channel.listModels();
            } catch (Exception e) {
                // 单个渠道取清单失败不影响其它渠道
                continue;
            }
            if (models == null) {
                continue;
            }
            for (Map<String, Object> model : models) {
                SysAccountChannelModel row = toCandidate(channel, model);
                if (row != null) {
                    candidates.add(row);
                }
            }
        }
        return candidates;
    }

    /**
     * 把渠道的一个内置模型转成候选行。
     *
     * @param channel 来源渠道
     * @param model   渠道内置模型（含 id / name）
     * @return 候选行；模型缺 id 时返回 {@code null}
     */
    private static SysAccountChannelModel toCandidate(KeylessMediaChannel channel, Map<String, Object> model) {
        String id = model == null ? null : text(model.get("id"));
        if (id == null) {
            return null;
        }
        SysAccountChannelModel row = new SysAccountChannelModel();
        row.setModel(id);
        String name = text(model.get("name"));
        // 渠道给出的展示名放别名位（如「Flux（默认）」），与历史目录行为一致
        row.setAlias(name == null ? id : name);
        row.setEnabled(Boolean.TRUE);
        row.setDescription("免登录渠道「" + channel.displayName() + "」内置模型（启动物化）");
        List<Map<String, Object>> caps = new ArrayList<>(1);
        Map<String, Object> cap = new LinkedHashMap<>(2);
        cap.put("task", TASK_IMAGE);
        caps.add(cap);
        row.setCapabilities(caps);
        return row;
    }

    /**
     * 对象转字符串（去空白）。
     *
     * @param value 原始值
     * @return 字符串；空白返回 {@code null}
     */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
