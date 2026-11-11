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
     * 获取指定码流的播放地址
     *
     * <p>默认忽略码流类型，保持与两参数版本一致；支持主/子码流的平台
     * （海康云眸、大华云睿等）覆盖本方法。</p>
     *
     * @param channel    通道
     * @param protocol   协议
     * @param streamType 码流：main 主码流 / sub 子码流
     * @return 拉流地址
     */
    default String getLiveUrl(int channel, String protocol, String streamType) {
        return getLiveUrl(channel, protocol);
    }

    /**
     * 开启直播（实时预览）会话。
     *
     * <p>默认实现回落到「取播放地址」：云平台按 URL 播放，不需要显式开播/停播，
     * 因此生成一个只带 URL 的句柄即可；SDK 直连会话应覆盖本方法返回真实预览句柄，
     * 并覆盖 {@link #stopLive(String)} 释放厂商侧资源。</p>
     *
     * @param channel    通道
     * @param protocol   协议
     * @param streamType 码流：main 主码流 / sub 子码流
     * @return 直播句柄
     */
    default LiveHandle startLive(int channel, String protocol, String streamType) {
        String url = getLiveUrl(channel, protocol, streamType);
        String streamId = getDeviceId() + ":live:" + channel + ":" + streamType;
        return new LiveHandle(streamId, url, protocol, streamType, null);
    }

    /**
     * 停止直播（实时预览）会话。
     *
     * <p>默认空实现：按 URL 播放的云平台无需释放资源；SDK 直连必须覆盖以停止预览、
     * 释放句柄，否则厂商侧会话会一直占用。</p>
     *
     * @param streamId 开启直播时返回的会话标识
     */
    default void stopLive(String streamId) {
        // 默认无资源可释放，由 SDK 会话覆盖
    }

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
     * 列出该设备下的通道。
     *
     * <p>默认抛 {@link UnsupportedOperationException}：没有通道能力的平台必须显式失败，
     * 不能返回空列表让业务侧误以为「设备没有通道」。前端据能力矩阵隐藏入口，
     * 不应调用到这里。</p>
     *
     * @return 通道列表
     */
    default List<DeviceChannel> listChannels() {
        throw new UnsupportedOperationException("该平台不支持通道列表");
    }

    /**
     * 抓图：返回一张 JPEG 字节流。
     *
     * <p>没有抓图能力的平台默认抛异常，避免返回空流被误当成成功。</p>
     *
     * @param channel 通道
     * @return JPEG 图片字节流
     */
    default InputStream capture(int channel) {
        throw new UnsupportedOperationException("该平台不支持抓图");
    }

    /**
     * 列出该设备下的门点位。
     *
     * @return 门列表
     */
    default List<DeviceDoorInfo> listDoors() {
        throw new UnsupportedOperationException("该平台不支持门列表");
    }

    /**
     * 远程开门
     * @param doorId 门标识
     */
    void remoteUnlock(int doorId);

    /**
     * 给单个人员下发单个门点权限（同步）。
     *
     * <p>默认不支持；平台实现需按自身接口将人员与门点绑定。</p>
     *
     * @param personId 厂商侧人员标识
     * @param doorId   厂商侧门点标识
     */
    default void grantDoorAccess(String personId, String doorId) {
        throw new UnsupportedOperationException("该平台不支持门禁权限下发");
    }

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

    /**
     * 车辆档案列表。
     *
     * <p>默认抛 {@link UnsupportedOperationException}：没有车辆能力的平台必须显式失败，
     * 不返回空列表让业务侧误以为「没有车辆」。</p>
     *
     * @return 车辆列表
     */
    default List<VehicleInfo> listVehicles() {
        throw new UnsupportedOperationException("该平台不支持车辆档案");
    }

    /**
     * 新增或修改车辆档案。
     *
     * @param vehicle 车辆
     * @param exists  平台侧是否已存在
     * @return 厂商侧车辆标识
     */
    default String upsertVehicle(VehicleInfo vehicle, boolean exists) {
        throw new UnsupportedOperationException("该平台不支持车辆写入");
    }

    /**
     * 删除车辆档案。
     *
     * @param vehicleId 车辆标识
     */
    default void deleteVehicle(String vehicleId) {
        throw new UnsupportedOperationException("该平台不支持车辆删除");
    }

    /**
     * 车闸（道闸）点位列表。
     *
     * @return 车闸列表
     */
    default List<BarrierGateInfo> listBarriers() {
        throw new UnsupportedOperationException("该平台不支持车闸列表");
    }

    /**
     * 开闸。
     *
     * @param gateId    车闸标识
     * @param direction 方向：in / out
     */
    default void openBarrier(String gateId, String direction) {
        throw new UnsupportedOperationException("该平台不支持开闸");
    }

    /**
     * 查询过车记录。
     *
     * @param range 时间段
     * @return 过车记录
     */
    default List<VehiclePassRecord> queryVehiclePasses(TimeRange range) {
        throw new UnsupportedOperationException("该平台不支持过车记录");
    }

    /**
     * 从指定游标后查询过车记录（支持游标的平台可避免重复翻页）。
     *
     * <p>默认实现忽略游标，直接走 {@link #queryVehiclePasses(TimeRange)}。</p>
     *
     * @param range   时间段
     * @param afterId 平台侧起始消息 ID（不含）
     * @return 过车记录
     */
    default List<VehiclePassRecord> queryVehiclePasses(TimeRange range, long afterId) {
        return queryVehiclePasses(range);
    }
}
