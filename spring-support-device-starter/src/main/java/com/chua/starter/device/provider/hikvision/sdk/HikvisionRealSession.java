package com.chua.starter.device.provider.hikvision.sdk;

import com.chua.starter.device.provider.hikvision.sdk.HCNetSDK.NET_DVR_ALARMER;
import com.chua.starter.device.provider.hikvision.sdk.HCNetSDK.NET_DVR_DEVICEINFO_V40;
import com.chua.starter.device.provider.hikvision.sdk.HCNetSDK.NET_DVR_PLAYBACK_COND;
import com.chua.starter.device.provider.hikvision.sdk.HCNetSDK.NET_DVR_PREVIEWINFO;
import com.chua.starter.device.provider.hikvision.sdk.HCNetSDK.NET_DVR_TIME;
import com.chua.starter.device.provider.hikvision.sdk.HCNetSDK.NET_DVR_USER_LOGIN_INFO;
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
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
* 海康设备真实会话（基于 hcnetsdk JNA）
* <p>通过 JNA 加载本机 HCNetSDK.dll 与真实设备通信。</p>
*
* <p>若未安装 SDK 动态库或设备不可达，构造函数会抛出异常，</p>
* <p>由 {@code HikvisionProvider} 捕获并回退到模拟会话。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
public class HikvisionRealSession implements DeviceSession {

    /**
    * 海康 SDK 库名（不含扩展名）
    */
    private static final String SDK_LIBRARY_NAME = "HCNetSDK";

    /**
    * 云台指令映射：业务枚举 -> 海康命令码
    */
    private static final Map<PtzCommand, Integer> PTZ_COMMANDS = new ConcurrentHashMap<>();

    static {
        PTZ_COMMANDS.put(PtzCommand.UP, 11);
        PTZ_COMMANDS.put(PtzCommand.DOWN, 12);
        PTZ_COMMANDS.put(PtzCommand.LEFT, 21);
        PTZ_COMMANDS.put(PtzCommand.RIGHT, 22);
        PTZ_COMMANDS.put(PtzCommand.ZOOM_IN, 33);
        PTZ_COMMANDS.put(PtzCommand.ZOOM_OUT, 34);
        PTZ_COMMANDS.put(PtzCommand.FOCUS_IN, 35);
        PTZ_COMMANDS.put(PtzCommand.FOCUS_OUT, 36);
        PTZ_COMMANDS.put(PtzCommand.IRIS_IN, 37);
        PTZ_COMMANDS.put(PtzCommand.IRIS_OUT, 38);
    }

    /**
    * 门禁远程开门命令码
    */
    private static final int NET_DVR_CMD_OPEN_DOOR = 3000;

    /**
    * 码流数据缓冲上限（8MB）
    */
    private static final int MAX_STREAM_BUFFER = 8 * 1024 * 1024;

    /**
    * SDK 接口实例
    */
    private final HCNetSDK sdk;

    /**
    * 设备连接信息
    */
    private final DeviceConnectInfo info;

    /**
    * 在线状态
    */
    private volatile boolean online = true;

    /**
    * 登录用户 标识
    */
    private int userId = -1;

    /**
    * 设备序列号
    */
    private String serial;

    /**
    * 通道数量
    */
    private int channelCount;

    /**
    * 预览句柄 -> 码流缓冲
    */
    private final Map<Integer, ByteArrayOutputStream> previewBuffers = new ConcurrentHashMap<>();

    /**
    * 事件订阅监听器
    */
    private final Map<EventType, List<EventListener>> listeners = new ConcurrentHashMap<>();

    /**
    * 事件缓冲队列
    */
    private final List<DeviceEvent> eventQueue = new CopyOnWriteArrayList<>();

    /**
    * 构造并登录设备
    *
    * @param info 设备连接信息
    * @throws IllegalStateException SDK 加载或设备登录失败
    */
    public HikvisionRealSession(DeviceConnectInfo info) {
        this.info = info;
        try {
            this.sdk = Native.load(SDK_LIBRARY_NAME, HCNetSDK.class);
        } catch (UnsatisfiedLinkError e) {
            /**
            * illegal状态异常。
            * @param e.getMessage( e.获取消息(
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("HCNetSDK 动态库加载失败，请将 SDK 的 dll 放入系统库路径: " + e.getMessage(), e);
        }
        /**
        * login。
        */
        login();
    }

    /**
    * 登录设备
    */
    private void login() {
        if (!sdk.NET_DVR_Init()) {
            /**
            * illegal状态异常。
            * @param sdk.NET_DVR_GetLastError( sdk.netdvr获取最后一个错误(
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("HCNetSDK 初始化失败，错误码: " + sdk.NET_DVR_GetLastError());
        }
        NET_DVR_USER_LOGIN_INFO loginInfo = new NET_DVR_USER_LOGIN_INFO();
        loginInfo.setLoginInfo(info.getIp(), info.getPort(), info.getUsername(), info.getPassword());
        NET_DVR_DEVICEINFO_V40 deviceInfo = new NET_DVR_DEVICEINFO_V40();
        userId = sdk.NET_DVR_Login_V40(loginInfo, deviceInfo);
        if (userId < 0) {
            sdk.NET_DVR_Cleanup();
            /**
            * illegal状态异常。
            * @param info.getIp( 信息.获取ip(
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("海康设备登录失败: " + info.getIp()
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        this.serial = new String(deviceInfo.sSerialNumber, StandardCharsets.US_ASCII).trim();
        this.channelCount = deviceInfo.byChanNum;
        log.info("海康设备登录成功: ip={}, serial={}, channels={}",
                info.getIp(), serial, channelCount);
        /**
        * setup事件callback。
        */
        setupEventCallback();
    }

    /**
    * 注册报警消息回调，接收设备事件
    */
    private void setupEventCallback() {
        HCNetSDK.MessageCallback callback = this::onAlarmMessage;
        sdk.NET_DVR_SetDVRMessageCallBack(callback, Pointer.NULL);
        log.info("海康事件订阅已注册: {}", info.getIp());
    }

    /**
    * 报警消息处理
    *
    * @param command    命令类型
    * @param alarmer    设备信息
    * @param alarmInfo  报警信息
    * @param bufLen     缓冲长度
    * @param user       用户数据
    * @return 是否成功
    */
    private boolean onAlarmMessage(int command, NET_DVR_ALARMER alarmer, Pointer alarmInfo, int bufLen, Pointer user) {
        DeviceEvent event = new DeviceEvent();
        event.setDeviceId(info.getIp());
        event.setType(EventType.OTHER);
        event.setDetail("海康报警事件，命令: " + command);
        event.setOccurredAt(java.time.LocalDateTime.now());
        eventQueue.add(event);
        List<EventListener> list = listeners.get(EventType.OTHER);
        if (list != null) {
            list.forEach(l -> {
                try {
                    l.onEvent(event);
                } catch (Exception e) {
                    log.error("海康事件回调异常", e);
                }
            });
        }
        log.debug("海康收到报警事件: command={}", command);
        return true;
    }

    @Override
    /** 获取deviceid */
    public String getDeviceId() {
        return info.getIp();
    }

    @Override
    /** 是否Online */
    public boolean isOnline() {
        return online && userId >= 0;
    }

    @Override
    /** 关闭 */
    public void close() {
        previewBuffers.clear();
        if (userId >= 0) {
            sdk.NET_DVR_Logout(userId);
            userId = -1;
        }
        sdk.NET_DVR_Cleanup();
        online = false;
        log.info("海康设备会话已关闭: {}", info.getIp());
    }

    @Override
    /** Preview */
    public InputStream preview(int channel) {
        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
        previewInfo.setPreviewChannel(channel, HCNetSDK.NET_DVR_MAIN_STREAM,
                HCNetSDK.NET_DVR_NETWORK_TCP, true);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        HCNetSDK.RealDataCallBack callback = (handle, dataType, pBuffer, bufSize, pUser) -> {
            if (dataType == HCNetSDK.NET_DVR_SYSHEAD || dataType == HCNetSDK.NET_DVR_STREAMDATA) {
                byte[] data = pBuffer.getByteArray(0, bufSize);
                synchronized (buffer) {
                    if (buffer.size() + data.length <= MAX_STREAM_BUFFER) {
                        buffer.write(data, 0, data.length);
                    }
                }
            }
        };
        int handle = sdk.NET_DVR_RealPlay_V40(userId, previewInfo, callback, Pointer.NULL);
        if (handle < 0) {
            throw new IllegalStateException("海康预览失败: channel=" + channel
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        previewBuffers.put(handle, buffer);
        /**
    * bytearray输入流。
    * @param buffer.toByteArray( 缓冲.转为bytearray(
    * @return bytearray输入流的结果
    */
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    @Override
    /** 查询Records */
    public List<RecordFile> queryRecords(int channel, TimeRange range) {
 // 海康按时间检索录像，此处返回空列表，真实场景需结合 NET_DVR_findrecord API
        return Collections.emptyList();
    }

    @Override
    /** 获取playbackurl */
    public String getPlaybackUrl(int channel, TimeRange range) {
 // JNA 直连模式不返回 URL，由前端调用 /视频/playback 拿到 SDK 拉流字节流
        return null;
    }

    @Override
    /** 获取liveurl */
    public String getLiveUrl(int channel, String protocol) {
 // JNA 直连模式不返回 URL，由前端调用 /视频/流 拿到 SDK 拉流字节流
        return null;
    }

    @Override
    /** 测试connection */
    public boolean testConnection() {
        return isOnline();
    }

    @Override
    /** Playback */
    public InputStream playback(int channel, TimeRange range) {
        NET_DVR_PLAYBACK_COND cond = new NET_DVR_PLAYBACK_COND();
        cond.lChannel = channel;
        cond.bByTime = 1;
        cond.byStreamType = HCNetSDK.NET_DVR_MAIN_STREAM;
        cond.startTime = new NET_DVR_TIME();
        cond.startTime.byYear = (byte) (range.getStart().getYear() - 2000);
        cond.startTime.byMonth = (byte) range.getStart().getMonthValue();
        cond.startTime.byDay = (byte) range.getStart().getDayOfMonth();
        cond.startTime.byHour = (byte) range.getStart().getHour();
        cond.startTime.byMinute = (byte) range.getStart().getMinute();
        cond.startTime.bySecond = (byte) range.getStart().getSecond();
        cond.stopTime = new NET_DVR_TIME();
        cond.stopTime.byYear = (byte) (range.getEnd().getYear() - 2000);
        cond.stopTime.byMonth = (byte) range.getEnd().getMonthValue();
        cond.stopTime.byDay = (byte) range.getEnd().getDayOfMonth();
        cond.stopTime.byHour = (byte) range.getEnd().getHour();
        cond.stopTime.byMinute = (byte) range.getEnd().getMinute();
        cond.stopTime.bySecond = (byte) range.getEnd().getSecond();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        HCNetSDK.PlayBackDataCallBack callback = (handle, dataType, pBuffer, bufSize, pUser) -> {
            if (dataType == HCNetSDK.NET_DVR_SYSHEAD || dataType == HCNetSDK.NET_DVR_STREAMDATA) {
                byte[] data = pBuffer.getByteArray(0, bufSize);
                synchronized (buffer) {
                    if (buffer.size() + data.length <= MAX_STREAM_BUFFER) {
                        buffer.write(data, 0, data.length);
                    }
                }
            }
        };
        int handle = sdk.NET_DVR_StartPlayBackByTime(userId, cond, null, callback, Pointer.NULL);
        if (handle < 0) {
            throw new IllegalStateException("海康回放失败: channel=" + channel
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        /**
    * bytearray输入流。
    * @param buffer.toByteArray( 缓冲.转为bytearray(
    * @return bytearray输入流的结果
    */
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    @Override
    /** ptzcontrol */
    public void ptzControl(PtzCommand cmd, double speed) {
        Integer command = PTZ_COMMANDS.get(cmd);
        if (command == null) {
            return;
        }
        int speedCode = (int) Math.max(1, Math.min(255, Math.round(speed * 255)));
        if (cmd == PtzCommand.STOP) {
            sdk.NET_DVR_PTZControlWithSpeed(userId, 1, 0, HCNetSDK.NET_DVR_PTZ_STOP);
            return;
        }
        boolean start = sdk.NET_DVR_PTZControlWithSpeed(userId, 1, command, HCNetSDK.NET_DVR_PTZ_START);
        log.debug("海康云台控制: cmd={}, speed={}, result={}", cmd, speedCode, start);
    }

    @Override
    /** 设置Preset */
    public void setPreset(int presetId, String name) {
        boolean ok = sdk.NET_DVR_PTZPreset_Other(userId, 1, HCNetSDK.NET_DVR_SET_PRESET, presetId, 0);
        if (!ok) {
            throw new IllegalStateException("海康设置预置位失败: presetId=" + presetId
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        log.info("海康设置预置位成功: presetId={}, name={}", presetId, name);
    }

    @Override
    /** 调用Preset */
    public void callPreset(int presetId) {
        boolean ok = sdk.NET_DVR_PTZPreset_Other(userId, 1, HCNetSDK.NET_DVR_GOTO_PRESET, presetId, 0);
        if (!ok) {
            throw new IllegalStateException("海康调用预置位失败: presetId=" + presetId
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        log.info("海康调用预置位成功: presetId={}", presetId);
    }

    @Override
    /** 删除Preset */
    public void deletePreset(int presetId) {
        boolean ok = sdk.NET_DVR_PTZPreset_Other(userId, 1, HCNetSDK.NET_DVR_DEL_PRESET, presetId, 0);
        if (!ok) {
            throw new IllegalStateException("海康删除预置位失败: presetId=" + presetId
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        log.info("海康删除预置位成功: presetId={}", presetId);
    }

    @Override
    /** 列表presets */
    public List<PresetInfo> listPresets() {
        // 海康按通道枚举预置位，此处简化返回空列表
        return Collections.emptyList();
    }

    @Override
    /** 远程解锁 */
    public void remoteUnlock(int doorId) {
        boolean ok = sdk.NET_DVR_RemoteControl(userId, NET_DVR_CMD_OPEN_DOOR, Pointer.NULL, 0, Pointer.NULL, 0);
        if (!ok) {
            throw new IllegalStateException("海康远程开门失败: doorId=" + doorId
                    + "，错误码: " + sdk.NET_DVR_GetLastError());
        }
        log.info("海康远程开门成功: doorId={}", doorId);
    }

    @Override
    /** 查询accessrecords */
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
 // 海康门禁记录需通过 获取_门_状态 等命令查询，此处简化返回空列表
        return Collections.emptyList();
    }

    @Override
    /** 订阅事件 */
    public void subscribeEvent(EventType type, EventListener listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    /** 取消订阅事件 */
    public void unsubscribeEvent(EventType type) {
        listeners.remove(type);
    }

    @Override
    /** 取出事件 */
    public List<DeviceEvent> pollEvents(TimeRange range) {
        if (eventQueue.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceEvent> result = new ArrayList<>(eventQueue);
        eventQueue.clear();
        return result;
    }

    /**
    * 获取设备序列号
    *
    * @return 序列号
    */
    public String getSerial() {
        return serial;
    }

    /**
    * 获取通道数量
    *
    * @return 通道数量
    */
    public int getChannelCount() {
        return channelCount;
    }
}
