package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.*;

import java.io.InputStream;
import java.util.List;

public interface DeviceSession {
    String getDeviceId();
    boolean isOnline();
    void close();

    // 视频
    InputStream preview(int channel);
    List<RecordFile> queryRecords(int channel, TimeRange range);
    InputStream playback(int channel, TimeRange range);

    /**
     * 获取视频播放地址（HLS / FLV / RTSP 等 URL）
     *
     * @param channel  通道号
     * @param protocol 协议类型，如 hls、flv、rtsp
     * @return 拉流 URL
     */
    String getLiveUrl(int channel, String protocol);

    /**
     * 测试连接：探测当前会话是否仍可达
     *
     * @return 是否可达
     */
    boolean testConnection();

    // 云台
    void ptzControl(PtzCommand cmd, double speed);
    void setPreset(int presetId, String name);
    void callPreset(int presetId);
    void deletePreset(int presetId);
    List<PresetInfo> listPresets();

    // 门禁
    void remoteUnlock(int doorId);
    List<AccessRecord> queryAccessRecords(TimeRange range);

    // 事件订阅
    void subscribeEvent(EventType type, EventListener listener);
    void unsubscribeEvent(EventType type);
    List<DeviceEvent> pollEvents(TimeRange range);
}