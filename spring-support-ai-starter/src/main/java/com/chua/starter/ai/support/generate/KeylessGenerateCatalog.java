package com.chua.starter.ai.support.generate;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.channel.KeylessMediaChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 免登录生成类渠道（文生图 / 文生视频）的模型目录读取器。
 *
 * <p><b>为什么需要它</b>：{@code image-pollinations} 这类渠道没有账号密钥，渠道管理
 * 不会为它们写 {@code sys_account_channel_model} 行，但出图链路是通的——账号模块的
 * {@code FreeChannelImageClient}（SPI {@code free-channel}）按模型名反查内置渠道后转交。
 * 因此「可选生成模型」必须能从渠道 SPI 直接补出这些模型，且提交时也要能把同一个
 * 模型名解析回「免费渠道」这一聚合供应商。</p>
 *
 * <p>本类只读渠道 SPI（{@link KeylessMediaChannel}，契约在 common-starter），
 * 不写任何渠道表，也不参与渠道归属判定——渠道业务仍整体在账号模块。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public final class KeylessGenerateCatalog {

    /**
     * 文生图任务类型
     */
    private static final String TASK_IMAGE = "image_generation";

    /**
     * 文生图的兼容别名
     */
    private static final String TASK_TEXT_TO_IMAGE = "text-to-image";

    /**
     * 工具类不允许实例化
     */
    private KeylessGenerateCatalog() {
    }

    /**
     * 目录行：渠道内置的一个模型。
     *
     * @param channel     渠道标识（如 {@code image-pollinations}）
     * @param displayName 渠道展示名（如 {@code Pollinations 文生图}）
     * @param model       模型标识（如 {@code flux}）
     * @param name        渠道给出的模型展示名，可为空
     */
    public record ChannelModel(String channel, String displayName, String model, String name) {
    }

    /**
     * 读取全部免登录出图渠道的内置模型。
     *
     * @return 模型行；渠道 SPI 未接入或单个渠道读取失败时返回已取到的部分
     */
    public static List<ChannelModel> imageModels() {
        Map<String, KeylessMediaChannel> channels;
        try {
            channels = ServiceProvider.of(KeylessMediaChannel.class).list();
        } catch (Exception e) {
            return List.of();
        }
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        List<ChannelModel> rows = new ArrayList<>();
        for (KeylessMediaChannel channel : channels.values()) {
            if (channel == null || blank(channel.channel()) || !declaresImageTask(channel)) {
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
                Object rawId = model == null ? null : model.get("id");
                String id = rawId == null ? null : String.valueOf(rawId).trim();
                if (blank(id)) {
                    continue;
                }
                Object rawName = model.get("name");
                String name = rawName == null ? null : String.valueOf(rawName).trim();
                rows.add(new ChannelModel(channel.channel(), channel.displayName(), id, blank(name) ? null : name));
            }
        }
        return rows;
    }

    /**
     * 判断模型名是否由某个免登录出图渠道声明。
     *
     * <p>只在这个判定成立时，才允许把模型解析到「免费渠道」聚合供应商；
     * 否则任意未配置的模型名都会被错认成可执行。</p>
     *
     * @param model 模型名，可为空
     * @return true 表示该模型属于免登录出图渠道
     */
    public static boolean ownsImageModel(String model) {
        if (blank(model)) {
            return false;
        }
        String target = model.trim().toLowerCase(Locale.ROOT);
        for (ChannelModel row : imageModels()) {
            if (row.model().toLowerCase(Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 渠道是否声明了文生图任务。
     *
     * @param channel 免登录生成类渠道
     * @return true 表示能出图
     */
    private static boolean declaresImageTask(KeylessMediaChannel channel) {
        List<String> tasks = channel.tasks();
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        for (String task : tasks) {
            if (task == null) {
                continue;
            }
            String name = task.trim();
            if (TASK_IMAGE.equalsIgnoreCase(name) || TASK_TEXT_TO_IMAGE.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 空白判断。
     *
     * @param value 待判断值
     * @return true 表示为 null / 空白
     */
    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
