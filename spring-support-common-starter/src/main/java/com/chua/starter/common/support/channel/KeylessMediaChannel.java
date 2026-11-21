package com.chua.starter.common.support.channel;

import java.util.List;
import java.util.Map;

/**
 * 免登录生成类渠道（文生图 / 文生视频）的中立契约。
 *
 * <p>与 {@link KeylessChatChannel} 平行：渠道的完整实现住在账号模块
 * （{@code ImageChannelProvider} / {@code VideoChannelProvider}），但「免费渠道聚合目录」
 * 由 AI 模块组装，而 AI 模块按设计不反向依赖账号模块。</p>
 *
 * <p>因此在 common-starter 放一份最小契约：账号模块的生成类渠道实现同时实现本接口，
 * AI 模块通过 {@code ServiceProvider} 发现它们，把渠道内置模型并入免费渠道的模型目录
 * （如 {@code image-pollinations} 的 {@code flux} / {@code turbo}），
 * 让「从供应商获取模型」在免费渠道下能按来源渠道搜到全部 chat 与 image 模型。</p>
 *
 * <p>只暴露目录所需的三项事实：渠道标识、展示名、任务类型与内置模型清单。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public interface KeylessMediaChannel {

    /**
     * 渠道标识（如 {@code image-pollinations}）。
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 底层 {@code ChatClient} SPI 扩展名（如 {@code duckduckgo}）。
     *
     * <p>纯 URL 拼装的渠道（如 Pollinations 图床）没有 ChatClient 扩展，
     * 此时返回 {@code null}，目录行用 {@link #channel()} 作为来源标识。</p>
     *
     * @return 扩展名，可为 {@code null}
     */
    default String extension() {
        return null;
    }

    /**
     * 渠道展示名称。
     *
     * @return 展示名称
     */
    String displayName();

    /**
     * 渠道产出的任务类型（如 {@code image_generation} / {@code video_generation}）。
     *
     * @return 任务类型列表
     */
    List<String> tasks();

    /**
     * 渠道内置模型清单。
     *
     * @return 模型列表，每项含 {@code id} / {@code name} / 可选 {@code contextWindow}
     */
    List<Map<String, Object>> listModels();

    /**
     * 判断渠道是否声明了任一给定任务类型（大小写不敏感）。
     *
     * <p>任务匹配口径落在契约上，避免各消费方（目录读取、模型物化、能力标注）
     * 各自维护一套别名匹配。</p>
     *
     * @param channel 免登录生成类渠道，可为 {@code null}
     * @param wanted  期望的任务类型，如 {@code image_generation}
     * @return true 表示渠道声明了其中任一任务
     */
    static boolean declaresTask(KeylessMediaChannel channel, String... wanted) {
        if (channel == null || wanted == null || wanted.length == 0) {
            return false;
        }
        List<String> tasks = channel.tasks();
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        for (String task : tasks) {
            if (task == null) {
                continue;
            }
            String name = task.trim();
            for (String expect : wanted) {
                if (expect != null && expect.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
