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