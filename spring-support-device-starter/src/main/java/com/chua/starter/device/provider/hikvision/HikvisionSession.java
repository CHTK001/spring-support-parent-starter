package com.chua.starter.device.provider.hikvision;

import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.EventListener;
import com.chua.starter.device.spi.model.AccessRecord;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DeviceEvent;
import com.chua.starter.device.spi.model.EventType;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.chua.starter.device.spi.model.RecordFile;
import com.chua.starter.device.spi.model.TimeRange;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海康设备会话实现（模拟）
 * <p>实际应调用海康 HCNetSDK / ISUP / 萤石云 SDK。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class HikvisionSession implements DeviceSession {

    /**
     * 设备连接信息
     */
    private final DeviceConnectInfo info;

    /**
     * 在线状态
     */
    private boolean online = true;

    /**
     * 事件订阅映射表
     */
    private final Map<EventType, List<EventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 创建 hikvision会话 实例
     * @param info 信息
     */
    public HikvisionSession(DeviceConnectInfo info) {
        this.info = info;
        log.info("海康设备连接成功: {}", info.ip());
    }

    @Override
    /**
     * 获取deviceid
     * @return 返回值
    */
    public String getDeviceId() {
        return info.ip();
    }

    @Override
    /**
     * 是否Online
     * @return 返回值
    */
    public boolean isOnline() {
        return online;
    }

    @Override
    /**
     * 关闭
    */
    public void close() {
        online = false;
        log.info("海康设备断开: {}", info.ip());
    }

    @Override
    /**
     * Preview
     * @param channel 整数
     * @return 返回值
    */
    public InputStream preview(int channel) {
        // 实际调用 SDK 获取预览流
        log.debug("海康预览: channel={}", channel);
        return null;
    }

    @Override
    /**
     * 查询Records
     * @param channel 整数
     * @param range 参数
     * @return 返回值
    */
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
        // 实际查询录像
        return new ArrayList<>();
    }

    @Override
    /**
     * Playback
     * @param channel 整数
     * @param range 参数
     * @return 返回值
    */
    public InputStream playback(int channel, TimeRange range) {
        log.debug("海康回放: channel={}, range={}", channel, range);
        return null;
    }

    @Override
    /**
     * ptzcontrol
     * @param cmd 参数
     * @param speed 双精度数
     * @return 返回值
    */
    public void ptzControl(PtzCommand cmd, double speed) {
        log.info("海康云台控制: cmd={}, speed={}", cmd, speed);
    }

    @Override
    /**
     * 设置Preset
     * @param name 字符串
     * @param presetId 整数
     * @return 返回值
    */
    public void setPreset(int presetId, String name) {
        log.info("海康设置预置位: presetId={}, name={}", presetId, name);
    }

    @Override
    /**
     * 调用Preset
     * @param presetId 整数
     * @return 返回值
    */
    public void callPreset(int presetId) {
        log.info("海康调用预置位: presetId={}", presetId);
    }

    @Override
    /**
     * 删除Preset
     * @param presetId 整数
     * @return 返回值
    */
    public void deletePreset(int presetId) {
        log.info("海康删除预置位: presetId={}", presetId);
    }

    @Override
    /**
     * 列表presets
     * @return 返回值
    */
    public List<PresetInfo> listPresets() {
        return new ArrayList<>();
    }

    @Override
    /**
     * 远程解锁
     * @param doorId 整数
     * @return 返回值
    */
    public void remoteUnlock(int doorId) {
        log.info("海康远程开门: doorId={}", doorId);
    }

    @Override
    /**
     * 获取liveurl
     * @param channel 整数
     * @param protocol 字符串
     * @return 返回值
    */
    public String getLiveUrl(int channel, String protocol) {
        // 仅 fallback-mock 模式使用的模拟会话：返回公开 HLS 测试流，
        // 便于无硬件环境下验证前端 hls.js 拉流、主/子码流切换与视频墙。
        return "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
    }

    @Override
    /**
     * 获取playbackurl
     * @param channel 整数
     * @param range 参数
     * @return 返回值
    */
    public String getPlaybackUrl(int channel, TimeRange range) {
        return null;
    }

    @Override
    /**
     * 查询accessrecords
     * @param range 参数
     * @return 返回值
    */
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
        return new ArrayList<>();
    }

    @Override
    /**
     * 测试connection
     * @return 返回值
    */
    public boolean testConnection() {
        return online;
    }

    @Override
    /**
     * 订阅事件
     * @param listener 参数
     * @param type 参数
     * @return 返回值
    */
    public void subscribeEvent(EventType type, EventListener listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    @Override
    /**
     * 取消订阅事件
     * @param type 参数
     * @return 返回值
    */
    public void unsubscribeEvent(EventType type) {
        listeners.remove(type);
    }

    @Override
    /**
     * 取出事件
     * @param range 参数
     * @return 返回值
    */
    public List<DeviceEvent> pollEvents(TimeRange range) {
        return new ArrayList<>();
    }
}
