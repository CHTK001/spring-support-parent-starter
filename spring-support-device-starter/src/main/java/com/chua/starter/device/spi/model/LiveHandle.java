package com.chua.starter.device.spi.model;

import java.time.Instant;

/**
 * 一次直播（实时预览）会话句柄。
 *
 * <p>统一 SDK 直连与云平台两种语义：SDK 侧 streamId 是厂商返回的预览句柄
 * （停止预览时必须回传）；云平台侧通常只有可播放 URL，streamId 由实现方自行
 * 生成一个稳定标识，stopLive 按该标识失效本地会话即可。</p>
 *
 * @param streamId    直播会话标识，停止时回传；不可为空
 * @param url         可播放地址（HLS / FLV / RTSP 等），可为空（SDK 字节流模式）
 * @param protocol    播放协议，例如 hls / flv / rtsp
 * @param streamType  码流：main 主码流 / sub 子码流
 * @param expiresAt   句柄过期时间，为空表示不过期
 * @since 4.0.0.51
 * @author CH
 */
public record LiveHandle(
        String streamId,
        String url,
        String protocol,
        String streamType,
        Instant expiresAt
) {
}
