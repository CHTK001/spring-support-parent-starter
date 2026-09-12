package com.chua.starter.device.provider.dahua.sdk;

import com.chua.starter.device.provider.dahua.sdk.DahuaNetSDK.DEVICEINFO;
import com.chua.starter.device.provider.dahua.sdk.DahuaNetSDK.LOGIN_INFO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
   * 大华设备真实会话（基于 netsdk JNA）
 * <p>通过 JNA 加载本机 dhnetsdk.dll 与真实设备通信。</p>
 *
 * <p>若未安装 SDK 动态库或设备不可达，构造函数会抛出异常，</p>
 * <p>由 {@code DahuaProvider} 捕获并回退到模拟会话。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DahuaRealSession implements DeviceSession {

    /**
     * 大华 SDK 库名（不含扩展名）
     */
    private static final String SDK_LIBRARY_NAME = "dhnetsdk";

    /**
     * 码流数据缓冲上限（8MB）
     */
    private static final int MAX_STREAM_BUFFER = 8 * 1024 * 1024;

    /**
     * 云台指令映射：业务枚举 -> 大华命令码
     */
    private static final Map<PtzCommand, Integer> PTZ_COMMANDS = new ConcurrentHashMap<>();

    static {
        PTZ_COMMANDS.put(PtzCommand.UP, DahuaNetSDK.DH_PTZ_UP);
        PTZ_COMMANDS.put(PtzCommand.DOWN, DahuaNetSDK.DH_PTZ_DOWN);
        PTZ_COMMANDS.put(PtzCommand.LEFT, DahuaNetSDK.DH_PTZ_LEFT);
        PTZ_COMMANDS.put(PtzCommand.RIGHT, DahuaNetSDK.DH_PTZ_RIGHT);
        PTZ_COMMANDS.put(PtzCommand.ZOOM_IN, DahuaNetSDK.DH_PTZ_ZOOM_ADD);
        PTZ_COMMANDS.put(PtzCommand.ZOOM_OUT, DahuaNetSDK.DH_PTZ_ZOOM_DEC);
        PTZ_COMMANDS.put(PtzCommand.FOCUS_IN, DahuaNetSDK.DH_PTZ_FOCUS_ADD);
        PTZ_COMMANDS.put(PtzCommand.FOCUS_OUT, DahuaNetSDK.DH_PTZ_FOCUS_DEC);
        PTZ_COMMANDS.put(PtzCommand.IRIS_IN, DahuaNetSDK.DH_PTZ_IRIS_ADD);
        PTZ_COMMANDS.put(PtzCommand.IRIS_OUT, DahuaNetSDK.DH_PTZ_IRIS_DEC);
    }

    /**
     * SDK 接口实例
     */
    private final DahuaNetSDK sdk;

    /**
     * 设备连接信息
     */
    private final DeviceConnectInfo info;

    /**
     * 在线状态
     */
    private volatile boolean online = true;

    /**
     * 登录句柄
     */
    private long loginHandle = -1;

    /**
     * 设备序列号
     */
    private String serial;

    /**
     * 通道数量
     */
    private int channelCount;

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
    public DahuaRealSession(DeviceConnectInfo info) {
        this.info = info;
        try {
            this.sdk = Native.load(SDK_LIBRARY_NAME, DahuaNetSDK.class);
        } catch (UnsatisfiedLinkError e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("dhnetsdk 动态库加载失败，请将 SDK 的 dll 放入系统库路径: " + e.getMessage(), e);
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
        if (!sdk.CLIENT_Init((handle, user) -> log.info("大华设备连接状态变化: handle={}", handle), Pointer.NULL)) {
            /**
             * illegal状态异常。
             * @param 初始化失败" 初始化失败"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("大华 NetSDK 初始化失败");
        }
        LOGIN_INFO loginInfo = new LOGIN_INFO();
        loginInfo.setLoginInfo(info.getIp(), info.getPort(), info.getUsername(), info.getPassword());
        DEVICEINFO deviceInfo = new DEVICEINFO();
        loginHandle = sdk.CLIENT_LoginWithHighLevelSecurity(loginInfo, deviceInfo, Pointer.NULL);
        if (loginHandle == 0) {
            sdk.CLIENT_Cleanup();
            /**
             * illegal状态异常。
             * @param info.getIp( 信息.获取ip(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("大华设备登录失败: " + info.getIp()
                    + "，错误码: " + sdk.CLIENT_GetLastError());
        }
        this.serial = new String(deviceInfo.szSerialNumber, StandardCharsets.US_ASCII).trim();
        this.channelCount = deviceInfo.byChanNum;
        log.info("大华设备登录成功: ip={}, serial={}, channels={}",
                info.getIp(), serial, channelCount);
    }

    @Override
    /** 获取deviceid */
    public String getDeviceId() {
        return info.getIp();
    }

    @Override
    /** 是否Online */
    public boolean isOnline() {
        return online && loginHandle > 0;
    }

    @Override
    /** 关闭 */
    public void close() {
        if (loginHandle > 0) {
            sdk.CLIENT_Logout(loginHandle);
            loginHandle = -1;
        }
        sdk.CLIENT_Cleanup();
        online = false;
        log.info("大华设备会话已关闭: {}", info.getIp());
    }

    @Override
    /** Preview */
    public InputStream preview(int channel) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DahuaNetSDK.RealDataCallback callback = (playHandle, dataType, pBuffer, bufSize, user) -> {
            if (dataType == DahuaNetSDK.DH_AV_CTRL_GET_SYSHEAD || dataType == DahuaNetSDK.DH_AV_CTRL_GET_STREAM) {
                byte[] data = pBuffer.getByteArray(0, bufSize);
                synchronized (buffer) {
                    if (buffer.size() + data.length <= MAX_STREAM_BUFFER) {
                        buffer.write(data, 0, data.length);
                    }
                }
            }
        };
        long handle = sdk.CLIENT_RealPlay(loginHandle, channel, DahuaNetSDK.DH_PLAY_STREAM_TYPE_MAIN,
                Pointer.NULL, callback, 0, Pointer.NULL);
        if (handle < 0) {
            throw new IllegalStateException("大华预览失败: channel=" + channel
                    + "，错误码: " + sdk.CLIENT_GetLastError());
        }
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
        return Collections.emptyList();
    }

    @Override
    /** 获取playbackurl */
    public String getPlaybackUrl(int channel, TimeRange range) {
        // JNA 直连模式不返回 URL
        return null;
    }

    @Override
    /** 获取liveurl */
    public String getLiveUrl(int channel, String protocol) {
        // JNA 直连模式不返回 URL
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
 // 大华回放需配合 客户端_findrecord / 客户端_playbackbyrecord API，此处简化
        /**
         * bytearray输入流。
         * @param byte[0] byte[0]
         * @return bytearray输入流的结果
         */
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    /** ptzcontrol */
    public void ptzControl(PtzCommand cmd, double speed) {
        Integer command = PTZ_COMMANDS.get(cmd);
        if (command == null) {
            return;
        }
        int param = (int) Math.max(1, Math.min(255, Math.round(speed * 255)));
        if (cmd == PtzCommand.STOP) {
            sdk.CLIENT_DHPTZControl(loginHandle, 0, 0, 0, 0);
            return;
        }
        boolean start = sdk.CLIENT_DHPTZControl(loginHandle, 0, command, 1, param);
        log.debug("大华云台控制: cmd={}, speed={}, result={}", cmd, speed, start);
    }

    @Override
    /** 设置Preset */
    public void setPreset(int presetId, String name) {
        boolean ok = sdk.CLIENT_SetPreset(loginHandle, 0, presetId);
        if (!ok) {
            throw new IllegalStateException("大华设置预置位失败: presetId=" + presetId
                    + "，错误码: " + sdk.CLIENT_GetLastError());
        }
        log.info("大华设置预置位成功: presetId={}, name={}", presetId, name);
    }

    @Override
    /** 调用Preset */
    public void callPreset(int presetId) {
        boolean ok = sdk.CLIENT_GotoPreset(loginHandle, 0, presetId);
        if (!ok) {
            throw new IllegalStateException("大华调用预置位失败: presetId=" + presetId
                    + "，错误码: " + sdk.CLIENT_GetLastError());
        }
        log.info("大华调用预置位成功: presetId={}", presetId);
    }

    @Override
    /** 删除Preset */
    public void deletePreset(int presetId) {
        boolean ok = sdk.CLIENT_DelPreset(loginHandle, 0, presetId);
        if (!ok) {
            throw new IllegalStateException("大华删除预置位失败: presetId=" + presetId
                    + "，错误码: " + sdk.CLIENT_GetLastError());
        }
        log.info("大华删除预置位成功: presetId={}", presetId);
    }

    @Override
    /** 列表presets */
    public List<PresetInfo> listPresets() {
        return Collections.emptyList();
    }

    @Override
    /** 远程解锁 */
    public void remoteUnlock(int doorId) {
        boolean ok = sdk.CLIENT_RemoteDoorOpen(loginHandle, doorId, 1);
        if (!ok) {
            throw new IllegalStateException("大华远程开门失败: doorId=" + doorId
                    + "，错误码: " + sdk.CLIENT_GetLastError());
        }
        log.info("大华远程开门成功: doorId={}", doorId);
    }

    @Override
    /** 查询accessrecords */
    public List<AccessRecord> queryAccessRecords(TimeRange range) {
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
