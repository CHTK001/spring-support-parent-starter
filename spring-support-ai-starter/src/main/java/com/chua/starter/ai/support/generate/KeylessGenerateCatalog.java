package com.chua.starter.ai.support.generate;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.channel.KeylessMediaChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * 文生视频任务类型
     */
    private static final String TASK_VIDEO = "video_generation";

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
        return collect(TASK_IMAGE, TASK_TEXT_TO_IMAGE);
    }

    /**
     * 读取全部免登录文生视频渠道的内置模型。
     *
     * @return 模型行；渠道 SPI 未接入或单个渠道读取失败时返回已取到的部分
     */
    public static List<ChannelModel> videoModels() {
        return collect(TASK_VIDEO);
    }

    /**
     * 读取声明了任一给定任务类型的免登录渠道的内置模型。
     *
     * @param wantedTasks 期望的任务类型（如 {@code image_generation} / {@code video_generation}）
     * @return 模型行；渠道 SPI 未接入或单个渠道读取失败时返回已取到的部分
     */
    private static List<ChannelModel> collect(String... wantedTasks) {
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
            if (channel == null || blank(channel.channel())
                    || !KeylessMediaChannel.declaresTask(channel, wantedTasks)) {
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
     * 读取全部免登录出图渠道的内置模型，按模型名（小写）建索引。
     *
     * <p>同名模型先到先得；调用方一次建索引后在整个请求内复用，
     * 避免逐行重复做 SPI 发现。</p>
     *
     * @return 模型名（小写）→ 目录行；渠道 SPI 未接入时返回空表
     */
    public static Map<String, ChannelModel> imageModelMap() {
        return index(imageModels());
    }

    /**
     * 读取全部免登录文生视频渠道的内置模型，按模型名（小写）建索引。
     *
     * @return 模型名（小写）→ 目录行；渠道 SPI 未接入时返回空表
     */
    public static Map<String, ChannelModel> videoModelMap() {
        return index(videoModels());
    }

    /**
     * 按模型名（小写）建目录索引。
     *
     * <p>同名模型先到先得；调用方一次建索引后在整个请求内复用，
     * 避免逐行重复做 SPI 发现。</p>
     *
     * @param rows 目录行
     * @return 模型名（小写）→ 目录行
     */
    private static Map<String, ChannelModel> index(List<ChannelModel> rows) {
        Map<String, ChannelModel> index = new LinkedHashMap<>(16);
        for (ChannelModel row : rows) {
            if (row.model() == null) {
                continue;
            }
            index.putIfAbsent(row.model().trim().toLowerCase(Locale.ROOT), row);
        }
        return index;
    }

    /**
     * 按模型名查免登录出图渠道的目录行。
     *
     * @param model 模型名，可为空
     * @return 目录行；不属于任何免登录出图渠道时返回 {@code null}
     */
    public static ChannelModel imageModelOf(String model) {
        if (blank(model)) {
            return null;
        }
        return imageModelMap().get(model.trim().toLowerCase(Locale.ROOT));
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
        return imageModelOf(model) != null;
    }

    /**
     * 按模型名查免登录文生视频渠道的目录行。
     *
     * @param model 模型名，可为空
     * @return 目录行；不属于任何免登录文生视频渠道时返回 {@code null}
     */
    public static ChannelModel videoModelOf(String model) {
        if (blank(model)) {
            return null;
        }
        return videoModelMap().get(model.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 判断模型名是否由某个免登录文生视频渠道声明。
     *
     * <p>只在这个判定成立时，才允许把模型解析到「免费渠道」聚合供应商；
     * 否则任意未配置的模型名都会被错认成可执行。</p>
     *
     * @param model 模型名，可为空
     * @return true 表示该模型属于免登录文生视频渠道
     */
    public static boolean ownsVideoModel(String model) {
        return videoModelOf(model) != null;
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
