package com.chua.starter.server.support.terminal;

import com.chua.starter.server.support.service.ServerConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * RDP 显示参数（分辨率 / 色深 / 画质取向）的解析与校验。
 *
 * <p><b>为什么单独一个类</b>：这几个值同时被两条链路消费 —— guacd 那条在
 * {@link GuacdConnectionFactory} 里组装成 guacd 参数，自研直连那条由前端
 * {@code ScRdpDirectViewer} 通过 `width/height` 传给 WS 桥。两边必须读到同一套
 * 「取值 + 回落」规则，否则改一个分辨率会出现「guacd 生效、直连不生效」的假象。</p>
 *
 * <p><b>为什么要校验而不是直接用</b>：这些值会原样进 guacd/Winsock 参数，
 * 写错（例如色深 20、分辨率 99999）不是「画质差一点」，而是<b>直接连不上</b>。
 * 所以越界/不在枚举里的值一律回落到默认值，并打一条 warn 说明回落原因，
 * 否则用户会以为自己的配置生效了。</p>
 *
 * <p>配置来源是 {@code ms_server_config}（系统配置页可改），键全部以 {@code rdp.} 开头。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RdpDisplaySettings {

    /**
     * 系统配置键：分辨率宽
     */
    public static final String KEY_WIDTH = "rdp.width";

    /**
     * 系统配置键：分辨率高
     */
    public static final String KEY_HEIGHT = "rdp.height";

    /**
     * 系统配置键：色深
     */
    public static final String KEY_COLOR_DEPTH = "rdp.color-depth";

    /**
     * 系统配置键：画质取向（true = 画质优先 / 关掉有损压缩）
     */
    public static final String KEY_FORCE_LOSSLESS = "rdp.force-lossless";

    /**
     * 默认宽（与 guacd 工厂历史的默认一致，改这里等于改出厂值）
     */
    public static final int DEFAULT_WIDTH = 1280;

    /**
     * 默认高
     */
    public static final int DEFAULT_HEIGHT = 768;

    /**
     * 默认色深
     */
    public static final int DEFAULT_COLOR_DEPTH = 24;

    /**
     * 宽的下限：低于此值连登录界面都显示不全
     */
    private static final int MIN_WIDTH = 640;

    /**
     * 宽的上限：超过常见单屏分辨率上限基本是误填
     */
    private static final int MAX_WIDTH = 3840;

    /**
     * 高的下限
     */
    private static final int MIN_HEIGHT = 480;

    /**
     * 高的上限
     */
    private static final int MAX_HEIGHT = 2160;

    /**
     * guacd 认识的色深档位（其它值会导致 RDP 连接失败或黑屏）
     */
    private static final Set<Integer> ALLOWED_COLOR_DEPTH = Set.of(8, 16, 24, 32);

    /**
     * 系统配置服务
     */
    private final ServerConfigService configService;

    /**
     * 生效的分辨率宽。
     *
     * @return 640..3840 之间的值，越界回落默认
     */
    public int width() {
        return intInRange(KEY_WIDTH, DEFAULT_WIDTH, MIN_WIDTH, MAX_WIDTH);
    }

    /**
     * 生效的分辨率高。
     *
     * @return 480..2160 之间的值，越界回落默认
     */
    public int height() {
        return intInRange(KEY_HEIGHT, DEFAULT_HEIGHT, MIN_HEIGHT, MAX_HEIGHT);
    }

    /**
     * 生效的色深。
     *
     * @return 8 / 16 / 24 / 32 之一，其它回落默认
     */
    public int colorDepth() {
        int raw = configService.getInt(KEY_COLOR_DEPTH, DEFAULT_COLOR_DEPTH);
        if (!ALLOWED_COLOR_DEPTH.contains(raw)) {
            log.warn("[rdp] 配置 {} = {} 不是合法色深（只支持 8/16/24/32），已回落到 {}",
                    KEY_COLOR_DEPTH, raw, DEFAULT_COLOR_DEPTH);
            return DEFAULT_COLOR_DEPTH;
        }
        return raw;
    }

    /**
     * 画质取向：true 表示画质优先（guacd 的 force-lossless）。
     *
     * @return 画质优先返回 true
     */
    public boolean forceLossless() {
        return configService.getBoolean(KEY_FORCE_LOSSLESS, false);
    }

    /**
     * 一次性打包当前生效值，供前端直连 RDP 使用。
     *
     * <p>前端不重复实现范围校验，只用这里算好的结果 —— 两份校验迟早会不一致。</p>
     *
     * @return 生效值快照
     */
    public View view() {
        return new View(width(), height(), colorDepth(), rdpDpi(), forceLossless());
    }

    /**
     * 读一个整数配置并校验范围，越界回落默认值。
     *
     * @param key      配置键
     * @param defValue 默认值（同时作为越界时的回落值）
     * @param min      允许的最小值
     * @param max      允许的最大值
     * @return 生效值
     */
    private int intInRange(String key, int defValue, int min, int max) {
        int raw = configService.getInt(key, defValue);
        if (raw < min || raw > max) {
            log.warn("[rdp] 配置 {} = {} 超出 {}..{}，已回落到 {}", key, raw, min, max, defValue);
            return defValue;
        }
        return raw;
    }

    /**
     * 生效的 RDP 显示参数快照。
     *
     * @param width         分辨率宽
     * @param height        分辨率高
     * @param colorDepth    色深
     * @param dpi           界面缩放 DPI
     * @param forceLossless 画质优先
     */
    public record View(int width, int height, int colorDepth, int dpi, boolean forceLossless) {
    }
}
