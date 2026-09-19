package com.chua.starter.device.provider.dahua.sdk;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * 大华 netsdk JNA 绑定（仅声明本模块使用到的函数与结构体）
 * <p>完整头文件见大华官方《NetSDK 网络开发包》。</p>
 *
 * <p>使用方式：{@code DahuaNetSDK INSTANCE = Native.load("dhnetsdk", DahuaNetSDK.class)}，</p>
 * <p>运行环境需将 dhnetsdk.dll、dhconfigsdk.dll 等放入系统库路径或应用目录。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DahuaNetSDK extends Library {

    /** 设备序列号长度 */
    int DH_DEVICE_SERIALNO_LEN = 48;

    /** 最大通道数 */
    int DH_MAX_CHANNUM = 16;

    /** 主码流 */
    int DH_PLAY_STREAM_TYPE_MAIN = 0;

    /** 预览：系统头 */
    int DH_AV_CTRL_GET_SYSHEAD = 1;

    /** 预览：流数据 */
    int DH_AV_CTRL_GET_STREAM = 2;

    /** 云台指令：方向 */
    int DH_PTZ_UP = 0;
    int DH_PTZ_DOWN = 1; // DH_PTZ_DOWN
    int DH_PTZ_LEFT = 2; // DH_PTZ_LEFT
    int DH_PTZ_RIGHT = 3; // DH_PTZ_RIGHT
    int DH_PTZ_ZOOM_ADD = 11; // dhptzzoom添加
    int DH_PTZ_ZOOM_DEC = 12; // DH_PTZ_ZOOM_DEC
    int DH_PTZ_FOCUS_ADD = 13; // dhptzfocus添加
    int DH_PTZ_FOCUS_DEC = 14; // DH_PTZ_FOCUS_DEC
    int DH_PTZ_IRIS_ADD = 15; // dhptziris添加
    int DH_PTZ_IRIS_DEC = 16; // DH_PTZ_IRIS_DEC

    /**
     * 初始化 SDK
     *
     * @param cbConnectState 连接状态回调
     * @param user           用户数据
     * @return 是否成功
     */
    boolean CLIENT_Init(ConnectStateCallback cbConnectState, Pointer user);

    /**
     * 清理 SDK
     */
    void CLIENT_Cleanup();

    /**
     * 高安全等级登录
     *
     * @param pLoginInfo     登录信息
     * @param lpDeviceInfo   设备信息（输出）
     * @param outLoginHandle 登录句柄（输出）
     * @return 会话句柄
     */
    long CLIENT_LoginWithHighLevelSecurity(LOGIN_INFO pLoginInfo, DEVICEINFO deviceInfo,
                                           Pointer outLoginHandle);

    /**
     * 注销登录
     *
     * @param loginHandle 登录句柄
     */
    void CLIENT_Logout(long loginHandle);

    /**
     * 获取最近错误码
     *
     * @return 错误码
     */
    int CLIENT_GetLastError();

    /**
     * 开始实时预览
     *
     * @param loginHandle   登录句柄
     * @param channel       通道号
     * @param streamType    码流类型
     * @param hPlayWnd      播放窗口句柄
     * @param cbRealData    码流回调
     * @param dwUser        用户数据
     * @param pReserved     保留
     * @return 预览句柄
     */
    long CLIENT_RealPlay(long loginHandle, int channel, int streamType, Pointer hPlayWnd,
                         RealDataCallback cbRealData, long dwUser, Pointer pReserved);

    /**
     * 停止预览
     *
     * @param playHandle 预览句柄
     */
    void CLIENT_StopPlay(long playHandle);

    /**
     * 云台控制
     *
     * @param loginHandle  登录句柄
     * @param channel      通道号
     * @param cmd          云台指令
     * @param bStart       0 停止、1 开始
     * @param param        参数
     * @return 是否成功
     */
    boolean CLIENT_DHPTZControl(long loginHandle, int channel, int cmd, int bStart, int param);

    /**
     * 设置云台预置位
     *
     * @param loginHandle 登录句柄
     * @param channel     通道号
     * @param presetIndex 预置位号
     * @return 是否成功
     */
    boolean CLIENT_SetPreset(long loginHandle, int channel, int presetIndex);

    /**
     * 调用云台预置位
     *
     * @param loginHandle 登录句柄
     * @param channel     通道号
     * @param presetIndex 预置位号
     * @return 是否成功
     */
    boolean CLIENT_GotoPreset(long loginHandle, int channel, int presetIndex);

    /**
     * 删除云台预置位
     *
     * @param loginHandle 登录句柄
     * @param channel     通道号
     * @param presetIndex 预置位号
     * @return 是否成功
     */
    boolean CLIENT_DelPreset(long loginHandle, int channel, int presetIndex);

    /**
     * 远程开门
     *
     * @param loginHandle 登录句柄
     * @param channel     通道号
     * @param command     命令
     * @return 是否成功
     */
    boolean CLIENT_RemoteDoorOpen(long loginHandle, int channel, int command);

    /**
     * 连接状态回调
     * @author CH
     * @since 4.0.0
     */
    interface ConnectStateCallback extends Callback {
        /**
         * 连接状态变化
         *
         * @param loginHandle 登录句柄
         * @param user        用户数据
         */
        void invoke(long loginHandle, Pointer user);
    }

    /**
     * 实时码流回调
     * @author CH
     * @since 4.0.0
     */
    interface RealDataCallback extends Callback {
        /**
         * 码流数据回调
         *
         * @param playHandle 预览句柄
         * @param dataType   数据类型
         * @param pBuffer    数据缓冲
         * @param bufSize    数据长度
         * @param user       用户数据
         */
        void invoke(long playHandle, int dataType, Pointer pBuffer, int bufSize, Pointer user);
    }

    /**
     * 登录信息结构体
     */
    @Structure.FieldOrder({"szHost", "szUser", "szPassword", "nPort", "emType", "bUseSSL",
            "dwLoginType", "szDeviceID"})
    /**
     * login信息类。
     *
     * @author CH
     * @since 4.0.0
     */
    class LOGIN_INFO extends Structure {

        /** 设备地址 */
        public byte[] szHost = new byte[129];

        /** 用户名 */
        public byte[] szUser = new byte[64];

        /** 密码 */
        public byte[] szPassword = new byte[64];

        /** 端口 */
        public int nPort;

        /** 连接类型 */
        public int emType;

        /** 是否使用 SSL */
        public boolean bUseSSL;

        /** 登录类型 */
        public int dwLoginType;

        /** 设备 标识 */
        public byte[] szDeviceID = new byte[48];

        /**
         * 设置登录信息
         *
         * @param ip       IP 地址
         * @param port     端口
         * @param username 用户名
         * @param password 密码
         */
        public void setLoginInfo(String ip, int port, String username, String password) {
            byte[] hostBytes = ip.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(hostBytes, 0, szHost, 0, Math.min(hostBytes.length, szHost.length));
            this.nPort = port;
            this.emType = 0;
            byte[] userBytes = username.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(userBytes, 0, szUser, 0, Math.min(userBytes.length, szUser.length));
            byte[] pwdBytes = password.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(pwdBytes, 0, szPassword, 0, Math.min(pwdBytes.length, szPassword.length));
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("szHost", "szUser", "szPassword", "nPort", "emType", "bUseSSL",
                    "dwLoginType", "szDeviceID");
        }
    }

    /**
     * 设备信息结构体
     */
    @Structure.FieldOrder({"szSerialNumber", "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum", "byDVRType",
            "byChanNum", "byStartChan", "byAudioChanNum", "byIPChanNum", "byZeroChanNum", "byMainProto",
            "bySubProto", "bySupport", "bySupport1", "bySupport2", "wDevType", "bySupport3",
            "byMultiStreamProto", "byStartDChan", "byStartDTalkChan", "byHighDChanNum", "bySupport4",
            "bySupport5", "byIPChanInHighMax", "byReserved1"})
    /**
     * DEVICEINFO类。
     *
     * @author CH
     * @since 4.0.0
     */
    class DEVICEINFO extends Structure {

        /** 设备序列号 */
        public byte[] szSerialNumber = new byte[DH_DEVICE_SERIALNO_LEN];

        /** 报警输入口个数 */
        public byte byAlarmInPortNum;

        /** 报警输出口个数 */
        public byte byAlarmOutPortNum;

        /** 硬盘个数 */
        public byte byDiskNum;

        /** 设备类型 */
        public byte byDVRType;

        /** 模拟通道个数 */
        public byte byChanNum;

        /** 起始通道号 */
        public byte byStartChan;

        /** 语音通道数 */
        public byte byAudioChanNum;

        /** 数字通道个数 */
        public byte byIPChanNum;

        /** 零通道编码个数 */
        public byte byZeroChanNum;

        /** 主协议 */
        public byte byMainProto;

        /** 子协议 */
        public byte bySubProto;

        /** 支持能力 */
        public byte bySupport;

        /** 支持能力 1 */
        public byte bySupport1;

        /** 支持能力 2 */
        public byte bySupport2;

        /** 设备类型 */
        public short wDevType;

        /** 支持能力 3 */
        public byte bySupport3;

        /** 多码流能力 */
        public byte byMultiStreamProto;

        /** 起始数字通道 */
        public byte byStartDChan;

        /** 起始数字对讲通道 */
        public byte byStartDTalkChan;

        /** 高清数字通道数 */
        public byte byHighDChanNum;

        /** 支持能力 4 */
        public byte bySupport4;

        /** 支持能力 5 */
        public byte bySupport5;

        /** 高清数字通道最大值 */
        public byte byIPChanInHighMax;

        /** 保留字节 */
        public byte[] byReserved1 = new byte[24];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("szSerialNumber", "byAlarmInPortNum", "byAlarmOutPortNum", "byDiskNum",
                    "byDVRType", "byChanNum", "byStartChan", "byAudioChanNum", "byIPChanNum", "byZeroChanNum",
                    "byMainProto", "bySubProto", "bySupport", "bySupport1", "bySupport2", "wDevType",
                    "bySupport3", "byMultiStreamProto", "byStartDChan", "byStartDTalkChan", "byHighDChanNum",
                    "bySupport4", "bySupport5", "byIPChanInHighMax", "byReserved1");
        }
    }
}
