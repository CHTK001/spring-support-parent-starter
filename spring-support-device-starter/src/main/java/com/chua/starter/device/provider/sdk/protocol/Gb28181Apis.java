package com.chua.starter.device.provider.sdk.protocol;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * GB/T 28181 与 ONVIF 标准协议能力目录。
 *
 * <p><b>性质</b>：这两个是<b>行业标准协议</b>，不是厂商云 OpenAPI：</p>
 * <ul>
 *   <li><b>GB/T 28181</b>：国标信令（SIP）+ 媒体（RTP/PS over UDP/TCP）。设备向 SIP 服务端
 *       注册（默认 5060），平台通过 SIP MESSAGE/INVITE 下发目录查询、实时点播、云台、回放。
 *       实际落地需要一个 SIP 服务端（如 WVP/ZLMediaKit、SRS、或自研 SIP 栈）。</li>
 *   <li><b>ONVIF</b>：设备发现（WS-Discovery）+ SOAP 控制（Device/Media/PTZ/Events 服务），
 *       直连设备 IP，无云端。用于局域网接入非国标设备。</li>
 * </ul>
 *
 * <p>因此本目录登记的是「协议动作」，{@code path} 记录动作名而非 HTTP 端点。
 * {@link Gb28181Sdk} / {@link OnvifSdk} 通过 SIP/ONVIF 客户端实现，
 * 未部署对应服务端或设备不可达时明确报错，不伪造数据。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class Gb28181Apis {

    /** GB28181 协议动作前缀 */
    private static final String GB = "gb28181://";

    /** ONVIF 协议动作前缀 */
    private static final String ON = "onvif://";

    /**
     * 工具类不可实例化。
     */
    private Gb28181Apis() {
    }

    /** GB28181 能力点 */
    private static final List<PlatformApiSpec> GB_ALL = List.of(
            PlatformApiSpec.global("gb.register", "设备 SIP 注册", "EVENT", GB + "register", "2016", "device"),
            PlatformApiSpec.global("gb.catalog", "查询设备目录", "QUERY", GB + "catalog", "2016", "device"),
            PlatformApiSpec.global("gb.device.info", "设备信息查询", "QUERY", GB + "deviceInfo", "2016", "device"),
            PlatformApiSpec.global("gb.device.status", "设备状态查询", "QUERY", GB + "deviceStatus", "2016", "device"),
            PlatformApiSpec.global("gb.live", "实时点播（INVITE）", "STREAM", GB + "invite", "2016", "video"),
            PlatformApiSpec.global("gb.playback", "历史回放", "STREAM", GB + "playback", "2016", "video"),
            PlatformApiSpec.global("gb.ptz", "云台控制", "COMMAND", GB + "ptz", "2016", "video"),
            PlatformApiSpec.global("gb.record.query", "录像查询", "QUERY", GB + "recordInfo", "2016", "video"),
            PlatformApiSpec.global("gb.alarm", "报警通知", "EVENT", GB + "alarm", "2016", "alarm"),
            PlatformApiSpec.global("gb.alarm.subscribe", "订阅报警", "COMMAND", GB + "alarmSubscribe", "2016", "alarm")
    );

    /** ONVIF 能力点 */
    private static final List<PlatformApiSpec> ON_ALL = List.of(
            PlatformApiSpec.global("onvif.discover", "设备发现（WS-Discovery）", "QUERY", ON + "discover", "2.0", "device"),
            PlatformApiSpec.global("onvif.device.info", "设备信息（GetDeviceInformation）", "QUERY", ON + "GetDeviceInformation", "2.0", "device"),
            PlatformApiSpec.global("onvif.device.capabilities", "设备能力（GetCapabilities）", "QUERY", ON + "GetCapabilities", "2.0", "device"),
            PlatformApiSpec.global("onvif.media.profiles", "媒体配置（GetProfiles）", "QUERY", ON + "GetProfiles", "2.0", "video"),
            PlatformApiSpec.global("onvif.media.streamUri", "取流地址（GetStreamUri）", "QUERY", ON + "GetStreamUri", "2.0", "video"),
            PlatformApiSpec.global("onvif.media.snapshot", "抓图（GetSnapshotUri）", "QUERY", ON + "GetSnapshotUri", "2.0", "video"),
            PlatformApiSpec.global("onvif.ptz.nodes", "云台节点（GetNodes）", "QUERY", ON + "GetNodes", "2.0", "video"),
            PlatformApiSpec.global("onvif.ptz.move", "云台移动（ContinuousMove）", "COMMAND", ON + "ContinuousMove", "2.0", "video"),
            PlatformApiSpec.global("onvif.events.subscribe", "事件订阅（CreatePullPointSubscription）", "COMMAND", ON + "CreatePullPointSubscription", "2.0", "alarm"),
            PlatformApiSpec.global("onvif.events.pull", "事件拉取（PullMessages）", "QUERY", ON + "PullMessages", "2.0", "alarm")
    );

    /**
     * GB28181 能力点。
     *
     * @return 能力声明
     */
    public static List<PlatformApiSpec> gb() {
        return GB_ALL;
    }

    /**
     * ONVIF 能力点。
     *
     * @return 能力声明
     */
    public static List<PlatformApiSpec> onvif() {
        return ON_ALL;
    }
}
