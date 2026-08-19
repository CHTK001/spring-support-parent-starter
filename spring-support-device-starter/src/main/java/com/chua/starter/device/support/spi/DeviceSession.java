package com.chua.starter.device.spi;

import com.chua.starter.device.spi.model.*;

import java.io.InputStream;
import java.util.List;

/**
 * 设备会话接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceSession {

    /**
     * 获取设备 ID
     */
    String getDeviceId();

    /**
     * 是否在线
     */
    boolean isOnline();

    /**
     * 关闭会话
     */
    void close();

    /**
     * 实时预览（拉字节流，SDK 模式）
     */
    InputStream preview(int channel);

    /**
     * 查询录像列表
     */
    List<RecordFile> queryRecords(int channel, TimeRange range);

    /**
     * 回放字节流（SDK 模式）
     */
    InputStream playback(int channel, TimeRange range);

    /**
     * 获取视频播放地址（HLS / FLV / RTSP 等 URL）
     */
    String getLiveUrl(int channel, String protocol);

    /**
     * 获取录像回放 URL（HLS）
     *
     * @return 回放 URL，平台不支持时返回 null
     */
    String getPlaybackUrl(int channel, TimeRange range);

    /**
     * 查询门禁出入记录
     */
    List<AccessRecord> queryAccessRecords(TimeRange range);

    /**
     * 测试连接
     */
    boolean testConnection();

    /**
     * 云台控制
     */
    void ptzControl(PtzCommand cmd, double speed);

    /**
     * 设置预置位
     */
    void setPreset(int presetId, String name);

    /**
     * 调用预置位
     */
    void callPreset(int presetId);

    /**
     * 删除预置位
     */
    void deletePreset(int presetId);

    /**
     * 列出预置位
     */
    List<PresetInfo> listPresets();

    /**
     * 远程开门
     */
    void remoteUnlock(int doorId);

    /**
     * 订阅事件
     */
    void subscribeEvent(EventType type, EventListener listener);

    /**
     * 取消订阅事件
     */
    void unsubscribeEvent(EventType type);

    /**
     * 拉取事件
     */
    List<DeviceEvent> pollEvents(TimeRange range);
}
