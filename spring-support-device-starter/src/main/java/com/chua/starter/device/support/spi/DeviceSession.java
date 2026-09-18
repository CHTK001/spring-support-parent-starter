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
    * 获取设备 标识
    * @return 获取deviceid的结果
    */
    String getDeviceId();

    /**
    * 是否在线
    * @return 是否online的结果
    */
    boolean isOnline();

    /**
    * 关闭会话
    */
    void close();

    /**
    * 实时预览（拉字节流，SDK 模式）
    * @param channel 通道
    * @return preview的结果
    */
    InputStream preview(int channel);

    /**
    * 查询录像列表
    * @param channel 通道
    * @param range 范围
    * @return 查询records的结果
    */
    List<RecordFile> queryRecords(int channel, TimeRange range);

    /**
    * 回放字节流（SDK 模式）
    * @param channel 通道
    * @param range 范围
    * @return playback的结果
    */
    InputStream playback(int channel, TimeRange range);

    /**
    * 获取视频播放地址（HLS / FLV / RTSP 等 URL）
    * @param channel 通道
    * @param protocol 协议
    * @return 获取liveurl的结果
    */
    String getLiveUrl(int channel, String protocol);

    /**
    * 获取录像回放 URL（HLS）
    *
    * @return 回放 URL，平台不支持时返回 空
    * @param channel 通道
    * @param range 范围
    */
    String getPlaybackUrl(int channel, TimeRange range);

    /**
    * 查询门禁出入记录
    * @param range 范围
    * @return 查询accessrecords的结果
    */
    List<AccessRecord> queryAccessRecords(TimeRange range);

    /**
    * 测试连接
    * @return 测试connection的结果
    */
    boolean testConnection();

    /**
    * 云台控制
    * @param cmd CMD
    * @param speed 速度
    */
    void ptzControl(PtzCommand cmd, double speed);

    /**
    * 设置预置位
    * @param presetId presetid
    * @param name 名称
    */
    void setPreset(int presetId, String name);

    /**
    * 调用预置位
    * @param presetId presetid
    */
    void callPreset(int presetId);

    /**
    * 删除预置位
    * @param presetId presetid
    */
    void deletePreset(int presetId);

    /**
    * 列出预置位
    * @return 列表presets的结果
    */
    List<PresetInfo> listPresets();

    /**
    * 远程开门
    * @param doorId 门标识
    */
    void remoteUnlock(int doorId);

    /**
    * 订阅事件
    * @param type 类型
    * @param listener 监听器
    */
    void subscribeEvent(EventType type, EventListener listener);

    /**
    * 取消订阅事件
    * @param type 类型
    */
    void unsubscribeEvent(EventType type);

    /**
    * 拉取事件
    * @param range 范围
    * @return poll事件的结果
    */
    List<DeviceEvent> pollEvents(TimeRange range);
}
