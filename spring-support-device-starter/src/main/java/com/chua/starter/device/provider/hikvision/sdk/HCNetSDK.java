package com.chua.starter.device.provider.hikvision.sdk;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

import java.util.Arrays;
import java.util.List;

/**
   * 海康 hcnetsdk JNA 绑定（仅声明本模块使用到的函数与结构体）
 * <p>完整头文件见海康官方《设备网络SDK 编程指南》。</p>
 *
 * <p>使用方式：{@code HCNetSDK INSTANCE = Native.load("HCNetSDK", HCNetSDK.class)}，</p>
 * <p>运行环境需将 HCNetSDK.dll、HCCore.dll 等放入系统库路径或应用目录。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface HCNetSDK extends Library {

    /** 设备地址最大长度 */
    int NET_DVR_DEV_ADDRESS_MAX_LEN = 129;

    /** 登录用户名最大长度 */
    int NET_DVR_LOGIN_USERNAME_MAX_LEN = 64;

    /** 登录密码最大长度 */
    int NET_DVR_LOGIN_PASSWD_MAX_LEN = 64;

    /** 通道号最大值 */
    int MAX_CHANNUM = 16;

    /** 预览：获取系统头 */
    int NET_DVR_SYSHEAD = 1;

    /** 预览：流数据 */
    int NET_DVR_STREAMDATA = 2;

    /** TCP 连接 */
    int NET_DVR_NETWORK_TCP = 1;

    /** 码流类型：主码流 */
    int NET_DVR_MAIN_STREAM = 0;

    /** 云台指令：开始/停止 */
    byte NET_DVR_PTZ_START = 1;
    byte NET_DVR_PTZ_STOP = 0; // netdvrptz停止

    /** 云台预置位操作 */
    int NET_DVR_PTZ_PRESET_OTHER = 0x0E;
    int NET_DVR_GOTO_PRESET = 0x00; // NET_DVR_GOTO_PRESET
    int NET_DVR_SET_PRESET = 0x01; // netdvr设置preset
    int NET_DVR_DEL_PRESET = 0x02; // NET_DVR_DEL_PRESET

    /**
     * 初始化 SDK
     *
     * @return 是否成功
     */
    boolean NET_DVR_Init();

    /**
     * 清理 SDK 资源
     *
     * @return 是否成功
     */
    boolean NET_DVR_Cleanup();

    /**
     * 设备登录
     *
     * @param loginInfo 登录信息
     * @param deviceInfo 设备信息（输出）
     * @return 用户 标识，负数表示失败
     */
    int NET_DVR_Login_V40(NET_DVR_USER_LOGIN_INFO loginInfo, NET_DVR_DEVICEINFO_V40 deviceInfo);

    /**
     * 注销登录
     *
     * @param lUserID 用户 标识
     * @return 是否成功
     */
    boolean NET_DVR_Logout(int lUserID);

    /**
     * 获取最近一次错误码
     *
     * @return 错误码
     */
    int NET_DVR_GetLastError();

    /**
     * 开始实时预览
     *
     * @param lUserID     用户 标识
     * @param previewInfo 预览参数
     * @param cbRealData  码流回调
     * @param pUser       用户自定义数据
     * @return 预览句柄，负数表示失败
     */
    int NET_DVR_RealPlay_V40(int lUserID, NET_DVR_PREVIEWINFO previewInfo, RealDataCallBack cbRealData, Pointer pUser);

    /**
     * 停止实时预览
     *
     * @param lRealHandle 预览句柄
     * @return 是否成功
     */
    boolean NET_DVR_StopRealPlay(int lRealHandle);

    /**
     * 云台控制（带速度）
     *
     * @param lUserID   用户 标识
     * @param lChannel  通道号
     * @param dwPTZCommand 云台指令
     * @param dwStop    0 开始、1 停止
     * @return 是否成功
     */
    boolean NET_DVR_PTZControlWithSpeed(int lUserID, int lChannel, int dwPTZCommand, byte dwStop);

    /**
     * 预置位操作
     *
     * @param lUserID  用户 标识
     * @param lChannel 通道号
     * @param dwPTZPresetCmd 预置位命令
     * @param dwPresetIndex  预置位号
     * @param dwReserved     保留
     * @return 是否成功
     */
    boolean NET_DVR_PTZPreset_Other(int lUserID, int lChannel, int dwPTZPresetCmd, int dwPresetIndex, int dwReserved);

    /**
     * 按时间回放
     *
     * @param lUserID     用户 标识
     * @param playBackCond 回放条件
     * @param pDownloadFileName 回放文件名
     * @param cbStreamData 码流回调
     * @param pUser       用户数据
     * @return 回放句柄
     */
    int NET_DVR_StartPlayBackByTime(int lUserID, NET_DVR_PLAYBACK_COND playBackCond, String pDownloadFileName,
                                    PlayBackDataCallBack cbStreamData, Pointer pUser);

    /**
     * 停止回放
     *
     * @param lPlayHandle 回放句柄
     * @return 是否成功
     */
    boolean NET_DVR_StopPlayBack(int lPlayHandle);

    /**
     * 设置报警消息回调
     *
     * @param cbMessageCallback 回调
     * @param pUser            用户数据
     * @return 是否成功
     */
    boolean NET_DVR_SetDVRMessageCallBack(MessageCallback cbMessageCallback, Pointer pUser);

    /**
     * 远程控制（门禁开门等）
     *
     * @param lUserID      用户 标识
     * @param dwCommand    命令码
     * @param lpInBuffer   输入缓冲
     * @param dwInBufferSize 输入缓冲大小
     * @param lpOutBuffer  输出缓冲
     * @param dwOutBufferSize 输出缓冲大小
     * @return 是否成功
     */
    boolean NET_DVR_RemoteControl(int lUserID, int dwCommand, Pointer lpInBuffer, int dwInBufferSize,
                                  Pointer lpOutBuffer, int dwOutBufferSize);

    /**
     * 实时码流回调
     * @author CH
     * @since 4.0.0
     */
    interface RealDataCallBack extends Callback {
        /**
         * 码流数据回调
         *
         * @param lRealHandle 预览句柄
         * @param dwDataType  数据类型
         * @param pBuffer     数据缓冲
         * @param dwBufSize   数据长度
         * @param pUser       用户数据
         */
        void invoke(int lRealHandle, int dwDataType, Pointer pBuffer, int dwBufSize, Pointer pUser);
    }

    /**
     * 回放码流回调
     * @author CH
     * @since 4.0.0
     */
    interface PlayBackDataCallBack extends Callback {
        /**
         * 回放数据回调
         *
         * @param lPlayHandle 回放句柄
         * @param dwDataType  数据类型
         * @param pBuffer     数据缓冲
         * @param dwBufSize   数据长度
         * @param pUser       用户数据
         */
        void invoke(int lPlayHandle, int dwDataType, Pointer pBuffer, int dwBufSize, Pointer pUser);
    }

    /**
     * 报警消息回调
     * @author CH
     * @since 4.0.0
     */
    interface MessageCallback extends Callback {
        /**
         * 报警消息回调
         *
         * @param lCommand   命令类型
         * @param pAlarmer   报警设备信息
         * @param pAlarmInfo 报警信息
         * @param dwBufLen   缓冲长度
         * @param pUser      用户数据
         * @return 是否成功
         */
        boolean invoke(int lCommand, NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser);
    }

    /**
     * 登录信息结构体
     */
    @Structure.FieldOrder({"sDeviceAddress", "byUseTransport", "wPort", "sUserName", "sPassword",
            "dwLoginMode", "byHttps", "iProxyID", "byVerifyMode", "byReserved", "byReserved2"})
    /**
     * netdvr用户login信息类。
     *
     * @author CH
     * @since 4.0.0
     */
    class NET_DVR_USER_LOGIN_INFO extends Structure {

        /** 设备 IP 地址 */
        /** Sdevice地址 */
        public byte[] sDeviceAddress = new byte[NET_DVR_DEV_ADDRESS_MAX_LEN];

        /** 是否使用传输协议 */
        /** byusetransport */
        public byte byUseTransport;

        /** 设备端口 */
        /** W端口 */
        public short wPort;

        /** 登录用户名 */
        /** S用户名称 */
        public byte[] sUserName = new byte[NET_DVR_LOGIN_USERNAME_MAX_LEN];

        /** 登录密码 */
        /** S密码 */
        public byte[] sPassword = new byte[NET_DVR_LOGIN_PASSWD_MAX_LEN];

        /** 登录模式 */
        /** dwlogin模式 */
        public int dwLoginMode;

        /** 是否使用 HTTPS */
        /** BYHTTPS */
        public byte byHttps;

        /** 代理 标识 */
        /** iproxyid */
        public int iProxyID;

        /** 校验模式 */
        /** byverify模式 */
        public byte byVerifyMode;

        /** 保留字节 */
        /** byreserved */
        public byte[] byReserved = new byte[127];

        /** 保留字节 2 */
        /** byreserved2 */
        public byte[] byReserved2 = new byte[64];

        /**
         * 设置连接信息
         *
         * @param ip       IP 地址
         * @param port     端口
         * @param username 用户名
         * @param password 密码
         */
        public void setLoginInfo(String ip, int port, String username, String password) {
            byte[] ipBytes = ip.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(ipBytes, 0, sDeviceAddress, 0, Math.min(ipBytes.length, sDeviceAddress.length));
            this.wPort = (short) port;
            this.byUseTransport = 0;
            this.dwLoginMode = 0;
            byte[] userBytes = username.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(userBytes, 0, sUserName, 0, Math.min(userBytes.length, sUserName.length));
            byte[] pwdBytes = password.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(pwdBytes, 0, sPassword, 0, Math.min(pwdBytes.length, sPassword.length));
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sDeviceAddress", "byUseTransport", "wPort", "sUserName", "sPassword",
                    "dwLoginMode", "byHttps", "iProxyID", "byVerifyMode", "byReserved", "byReserved2");
        }
    }

    /**
     * 设备信息结构体
     */
    @Structure.FieldOrder({"sSerialNumber", "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum", "byDVRType",
            "byChanNum", "byStartChan", "byAudioChanNum", "byIPChanNum", "byZeroChanNum", "byMainProto",
            "bySubProto", "bySupport", "bySupport1", "bySupport2", "wDevType", "bySupport3",
            "byMultiStreamProto", "byStartDChan", "byStartDTalkChan", "byHighDChanNum", "bySupport4",
            "bySupport5", "byIPChanInHighMax", "byReserved1"})
    /**
     * NET_DVR_DEVICEINFO_V40类。
     *
     * @author CH
     * @since 4.0.0
     */
    class NET_DVR_DEVICEINFO_V40 extends Structure {

        /** 设备序列号 */
        /** Sserial数字 */
        public byte[] sSerialNumber = new byte[48];

        /** 报警输入口个数 */
        /** byalarm入端口NUM */
        public byte byAlarmInPortNum;

        /** 报警输出口个数 */
        /** byalarm出端口NUM */
        public byte byAlarmOutPortNum;

        /** 硬盘个数 */
        /** bydisknum */
        public byte byDiskNum;

        /** 设备类型 */
        /** bydvrtype */
        public byte byDVRType;

        /** 模拟通道个数 */
        /** bychannum */
        public byte byChanNum;

        /** 起始通道号 */
        /** BY开始chan */
        public byte byStartChan;

        /** 语音通道数 */
        /** BY音频channum */
        public byte byAudioChanNum;

        /** 数字通道个数 */
        /** byipchannum */
        public byte byIPChanNum;

        /** 零通道编码个数 */
        /** byzerochannum */
        public byte byZeroChanNum;

        /** 主协议 */
        /** bymainproto */
        public byte byMainProto;

        /** 子协议 */
        /** bysubproto */
        public byte bySubProto;

        /** 支持能力 */
        /** bysupport */
        public byte bySupport;

        /** 支持能力 1 */
        /** bysupport1 */
        public byte bySupport1;

        /** 支持能力 2 */
        /** bysupport2 */
        public byte bySupport2;

        /** 设备类型 */
        /** WDEV类型 */
        public short wDevType;

        /** 支持能力 3 */
        /** bysupport3 */
        public byte bySupport3;

        /** 多码流能力 */
        /** bymulti流Proto.io.io */
        public byte byMultiStreamProto;

        /** 起始数字通道 */
        /** BY开始dchan */
        public byte byStartDChan;

        /** 起始数字对讲通道 */
        /** BY开始dtalkchan */
        public byte byStartDTalkChan;

        /** 高清数字通道数 */
        /** byhighdchannum */
        public byte byHighDChanNum;

        /** 支持能力 4 */
        /** bysupport4 */
        public byte bySupport4;

        /** 支持能力 5 */
        /** bysupport5 */
        public byte bySupport5;

        /** 高清数字通道最大值 */
        /** byipchaninhigh最大值 */
        public byte byIPChanInHighMax;

        /** 保留字节 */
        /** byreserved1 */
        public byte[] byReserved1 = new byte[24];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sSerialNumber", "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum", "byDVRType",
                    "byChanNum", "byStartChan", "byAudioChanNum", "byIPChanNum", "byZeroChanNum", "byMainProto",
                    "bySubProto", "bySupport", "bySupport1", "bySupport2", "wDevType", "bySupport3",
                    "byMultiStreamProto", "byStartDChan", "byStartDTalkChan", "byHighDChanNum", "bySupport4",
                    "bySupport5", "byIPChanInHighMax", "byReserved1");
        }
    }

    /**
     * 预览参数结构体
     */
    @Structure.FieldOrder({"lChannel", "dwStreamType", "dwLinkMode", "hPlayWnd", "bBlocked", "bPassbackRecord",
            "byPreviewMode", "byStreamID", "byProtoType", "byRes1", "byRes2", "dwDisplayBufNum", "byRes"})
    /**
     * NET_DVR_PREVIEWINFO类。
     *
     * @author CH
     * @since 4.0.0
     */
    class NET_DVR_PREVIEWINFO extends Structure {

        /** 通道号 */
        /** L通道 */
        public int lChannel;

        /** 码流类型 */
        /** DW流类型 */
        public int dwStreamType;

        /** 连接方式 */
        /** dwlink模式 */
        public int dwLinkMode;

        /** 播放窗口句柄 */
        /** hplaywnd */
        public Pointer hPlayWnd;

        /** 阻塞播放 */
        /** Bblocked */
        public byte bBlocked;

        /** 是否回放录像 */
        /** Bpassbackrecord */
        public byte bPassbackRecord;

        /** 预览模式 */
        /** bypreview模式 */
        public byte byPreviewMode;

        /** 流 标识 */
        /** BY流标识 */
        public byte[] byStreamID = new byte[32];

        /** 协议类型 */
        /** byproto类型 */
        public byte byProtoType;

        /** 保留 1 */
        /** byres1 */
        public byte byRes1;

        /** 保留 2 */
        /** byres2 */
        public byte byRes2;

        /** 播放缓冲帧数 */
        /** dwdisplaybufnum */
        public int dwDisplayBufNum;

        /** 保留 */
        /** BYRES */
        public byte[] byRes = new byte[216];

        /**
         * 设置预览通道
         *
         * @param channel     通道号
         * @param streamType  码流类型
         * @param linkMode    连接方式
         * @param bBlocked    是否阻塞
         */
        public void setPreviewChannel(int channel, int streamType, int linkMode, boolean bBlocked) {
            this.lChannel = channel;
            this.dwStreamType = streamType;
            this.dwLinkMode = linkMode;
            this.bBlocked = (byte) (bBlocked ? 1 : 0);
            this.hPlayWnd = Pointer.NULL;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("lChannel", "dwStreamType", "dwLinkMode", "hPlayWnd", "bBlocked", "bPassbackRecord",
                    "byPreviewMode", "byStreamID", "byProtoType", "byRes1", "byRes2", "dwDisplayBufNum", "byRes");
        }
    }

    /**
     * 回放条件结构体
     */
    @Structure.FieldOrder({"byStreamType", "bByTime", "bNeedDecrypt", "byStreamID", "lChannel",
            "dwPos", "startTime", "stopTime"})
    /**
     * NET_DVR_PLAYBACK_COND类。
     *
     * @author CH
     * @since 4.0.0
     */
    class NET_DVR_PLAYBACK_COND extends Structure {

        /** 码流类型 */
        /** BY流类型 */
        public byte byStreamType;

        /** 是否按时间 */
        /** BBY时间 */
        public byte bByTime;

        /** 是否解密 */
        /** Bneeddecrypt */
        public byte bNeedDecrypt;

        /** 流 标识 */
        /** BY流标识 */
        public byte[] byStreamID = new byte[32];

        /** 通道号 */
        /** L通道 */
        public int lChannel;

        /** 位置 */
        /** DWPOS */
        public int dwPos;

        /** 开始时间 */
        public NET_DVR_TIME startTime = new NET_DVR_TIME();

        /** 结束时间 */
        /** 停止时间 */
        public NET_DVR_TIME stopTime = new NET_DVR_TIME();

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("byStreamType", "bByTime", "bNeedDecrypt", "byStreamID", "lChannel",
                    "dwPos", "startTime", "stopTime");
        }
    }

    /**
     * 时间结构体
     */
    @Structure.FieldOrder({"byYear", "byMonth", "byDay", "byHour", "byMinute", "bySecond", "byRes", "byISO8601",
            "wMillisecond", "cTimeZoneID", "byRes1", "byRes2"})
    /**
     * netdvr时间类。
     *
     * @author CH
     * @since 4.0.0
     */
    class NET_DVR_TIME extends Structure {

        /** 年 */
        /** byyear */
        public byte byYear;

        /** 月 */
        /** bymonth */
        public byte byMonth;

        /** 日 */
        /** BYDAY */
        public byte byDay;

        /** 时 */
        /** byhour */
        public byte byHour;

        /** 分 */
        /** byminute */
        public byte byMinute;

        /** 秒 */
        /** bysecond */
        public byte bySecond;

        /** 保留 */
        /** BYRES */
        public byte byRes;

        /** ISO8601 */
        /** byiso8601 */
        public byte byISO8601;

        /** 毫秒 */
        /** Wmillisecond */
        public short wMillisecond;

        /** 时区 */
        /** C时间zoneid */
        public byte cTimeZoneID;

        /** 保留 1 */
        /** byres1 */
        public byte byRes1;

        /** 保留 2 */
        /** byres2 */
        public byte byRes2;

        /**
         * 设置为当前时间
         */
        public void setNow() {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            this.byYear = (byte) (now.getYear() - 2000);
            this.byMonth = (byte) now.getMonthValue();
            this.byDay = (byte) now.getDayOfMonth();
            this.byHour = (byte) now.getHour();
            this.byMinute = (byte) now.getMinute();
            this.bySecond = (byte) now.getSecond();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("byYear", "byMonth", "byDay", "byHour", "byMinute", "bySecond", "byRes", "byISO8601",
                    "wMillisecond", "cTimeZoneID", "byRes1", "byRes2");
        }
    }

    /**
     * 报警设备信息结构体
     */
    @Structure.FieldOrder({"byUserIDValid", "bySerialValid", "byVersionValid", "byDeviceNameValid", "byMacAddrValid",
            "byLinkPortValid", "byDeviceIP", "bySocketIP", "wLinkPort", "sSerialNumber", "byAlarmInPortNum",
            "byAlarmOutPortNum", "byDiskNum", "byDVRType", "byChanNum", "byStartChan", "byAudioChanNum",
            "byIPChanNum", "byZeroChanNum", "byMainProto", "bySubProto", "bySupport", "wDevType", "sDeviceName",
            "byMacAddr", "byRes1", "sVersion", "byRes2"})
    /**
     * netdvr报警器类。
     *
     * @author CH
     * @since 4.0.0
     */
    class NET_DVR_ALARMER extends Structure {

        /** 用户 标识 有效 */
        /** BY用户idvalid */
        public byte byUserIDValid;

        /** 序列号有效 */
        /** byserial是否有效 */
        public byte bySerialValid;

        /** 版本有效 */
        /** BY版本是否有效 */
        public byte byVersionValid;

        /** 设备名有效 */
        /** bydevice名称是否有效 */
        public byte byDeviceNameValid;

        /** MAC 有效 */
        /** bymacaddr是否有效 */
        public byte byMacAddrValid;

        /** 连接端口有效 */
        /** bylink端口是否有效 */
        public byte byLinkPortValid;

        /** 设备 IP */
        /** bydeviceip */
        public byte[] byDeviceIP = new byte[4];

        /** 套接字 IP */
        /** by套接字ip */
        public byte[] bySocketIP = new byte[4];

        /** 连接端口 */
        /** Wlink端口 */
        public short wLinkPort;

        /** 序列号 */
        /** Sserial数字 */
        public byte[] sSerialNumber = new byte[48];

        /** 报警输入数 */
        /** byalarm入端口NUM */
        public byte byAlarmInPortNum;

        /** 报警输出数 */
        /** byalarm出端口NUM */
        public byte byAlarmOutPortNum;

        /** 硬盘数 */
        /** bydisknum */
        public byte byDiskNum;

        /** 设备类型 */
        /** bydvrtype */
        public byte byDVRType;

        /** 通道数 */
        /** bychannum */
        public byte byChanNum;

        /** 起始通道 */
        /** BY开始chan */
        public byte byStartChan;

        /** 语音通道数 */
        /** BY音频channum */
        public byte byAudioChanNum;

        /** 数字通道数 */
        /** byipchannum */
        public byte byIPChanNum;

        /** 零通道数 */
        /** byzerochannum */
        public byte byZeroChanNum;

        /** 主协议 */
        /** bymainproto */
        public byte byMainProto;

        /** 子协议 */
        /** bysubproto */
        public byte bySubProto;

        /** 支持能力 */
        /** bysupport */
        public byte bySupport;

        /** 设备类型 */
        /** WDEV类型 */
        public short wDevType;

        /** 设备名称 */
        /** Sdevice名称 */
        public byte[] sDeviceName = new byte[32];

        /** MAC 地址 */
        /** bymacaddr */
        public byte[] byMacAddr = new byte[6];

        /** 保留 1 */
        /** byres1 */
        public byte[] byRes1 = new byte[2];

        /** 版本 */
        /** S版本 */
        public byte[] sVersion = new byte[16];

        /** 保留 2 */
        /** byres2 */
        public byte[] byRes2 = new byte[188];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("byUserIDValid", "bySerialValid", "byVersionValid", "byDeviceNameValid",
                    "byMacAddrValid", "byLinkPortValid", "byDeviceIP", "bySocketIP", "wLinkPort", "sSerialNumber",
                    "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum", "byDVRType", "byChanNum", "byStartChan",
                    "byAudioChanNum", "byIPChanNum", "byZeroChanNum", "byMainProto", "bySubProto", "bySupport",
                    "wDevType", "sDeviceName", "byMacAddr", "byRes1", "sVersion", "byRes2");
        }
    }
}
