package com.chua.starter.device.provider.sdk.dahua;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 大华云开发者平台（open.cloud-dahua.com，IoT 开放平台）官方接口目录。
 *
 * <p>这是用户实际接入的平台：控制台 {@code console/develop/iotList}，产品 ID 86118185。
 * 与「大华云睿 SaaS」({@code www.cloud-dahua.com/gateway}) 不是同一套：
 * 本平台统一走 {@code https://open.cloud-dahua.com/open-api/...}，
 * 认证为 AccessKey/SecretAccessKey 的 HMAC-SHA512 签名 + AppAccessToken。</p>
 *
 * <h3>认证</h3>
 * <ul>
 *   <li>换 token：{@code POST /open-api/api-base/auth/getAppAccessToken}，
 *       签名 {@code HMAC-SHA512(SK, AK+timestamp+nonce)}（十六进制大写），
 *       头带 AccessKey / Timestamp / Nonce / Sign / ProductId / Version=V1 / Sign-Type=simple，
 *       返回 {@code data.appAccessToken}（约 7 天）。</li>
 *   <li>业务接口：同签名头 + {@code AppAccessToken}，
 *       签名因子 {@code AK+appAccessToken+timestamp+nonce}。</li>
 * </ul>
 *
 * <p>响应统一信封 {@code {code:"200", success:true, data, msg}}。</p>
 *
 * <p>路径来自大华云开放平台官方文档中心：基础通用套件、视频监控、云存储、门禁、
 * 组织、用户、设备与消息套件。凭据可用 {@code extra("apiPath.&lt;code&gt;")} 覆盖。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class DahuaCloudApis {

    /** IoT 设备前缀 */
    private static final String IOT = "/open-api/api-iot/device";

    /** AIoT 外设前缀（SD 卡/铃音/WiFi/配置） */
    private static final String AIOT = "/open-api/api-aiot/device";

    /** 消息订阅前缀 */
    private static final String MSG = "/open-api/api-message";

    /**
     * 工具类不可实例化。
     */
    private DahuaCloudApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // ---------- 认证 ----------
            PlatformApiSpec.global("auth.token", "获取 AppAccessToken", "POST",
                    "/open-api/api-base/auth/getAppAccessToken", "V1", "other"),

            // ---------- 设备管理 ----------
            PlatformApiSpec.global("device.list", "分页查询设备列表", "POST",
                    IOT + "/getDeviceList", "V1", "device"),
            PlatformApiSpec.global("device.add", "添加设备", "POST",
                    IOT + "/addDevice", "V1", "device"),
            PlatformApiSpec.global("device.delete", "删除设备", "POST",
                    IOT + "/deleteDevice", "V1", "device"),
            PlatformApiSpec.global("device.verify", "校验设备密码", "POST",
                    IOT + "/verifyDevCode", "V1", "device"),
            PlatformApiSpec.global("device.online", "查询设备在线状态", "POST",
                    IOT + "/deviceOnline", "V1", "device"),
            PlatformApiSpec.global("device.details", "批量查询设备详细信息", "POST",
                    IOT + "/listDeviceDetailsByIds", "V1", "device"),
            PlatformApiSpec.global("device.bind", "查询设备绑定状态", "POST",
                    IOT + "/checkDeviceBindInfo", "V1", "device"),
            PlatformApiSpec.global("device.category", "查询设备品类", "POST",
                    IOT + "/getCategory", "V1", "device"),
            PlatformApiSpec.global("device.rename.iot", "修改设备/通道名称（IoT 套件）", "POST",
                    IOT + "/modifyDeviceName", "V1", "device"),
            PlatformApiSpec.global("device.password", "修改设备密码", "POST",
                    IOT + "/modifyDevCode", "V1", "device"),
            PlatformApiSpec.global("device.rename", "修改设备或通道名称", "POST",
                    "/open-api/api-biz/device/updateDeviceName", "V1", "device"),
            PlatformApiSpec.global("device.org", "批量修改设备所属组织", "POST",
                    "/open-api/api-biz/device/updateDeviceOrg", "V1", "device"),
            PlatformApiSpec.global("device.biz.add", "添加设备（业务侧）", "POST",
                    "/open-api/api-biz/device/addDevice", "V1", "device"),

            // ---------- 视频监控 ----------
            PlatformApiSpec.global("media.hls", "创建设备 HLS 直播", "POST",
                    IOT + "/createDeviceHlsLive", "V1", "media"),
            PlatformApiSpec.global("media.hls.delete", "删除设备 HLS 直播", "POST",
                    IOT + "/delDeviceHlsLive", "V1", "media"),
            PlatformApiSpec.global("media.flv", "创建设备 FLV 直播", "POST",
                    IOT + "/createDeviceFlvLive", "V1", "media"),
            PlatformApiSpec.global("media.flv.delete", "删除设备 FLV 直播", "POST",
                    IOT + "/delDeviceFlvLive", "V1", "media"),
            PlatformApiSpec.global("media.stream", "创建设备私有拉流地址", "POST",
                    IOT + "/createDeviceStreamUrl", "V1", "media"),
            PlatformApiSpec.global("record.local", "设备录像查询", "POST",
                    IOT + "/queryLocalRecords", "V1", "record"),
            PlatformApiSpec.global("record.hls", "录像片段转 HLS 回放", "POST",
                    IOT + "/createDeviceRecordHls", "V1", "record"),
            PlatformApiSpec.global("capture.snap", "设备抓图", "POST",
                    IOT + "/setDeviceSnapEnhanced", "V1", "media"),
            PlatformApiSpec.global("ptz.move", "云台移动控制", "POST",
                    AIOT + "/controlMovePTZ", "V1", "ptz"),
            PlatformApiSpec.global("ptz.preset.set", "设置预置点", "POST",
                    AIOT + "/configPreset", "V1", "ptz"),
            PlatformApiSpec.global("ptz.preset.turn", "转到预置点", "POST",
                    AIOT + "/turnPreset", "V1", "ptz"),
            PlatformApiSpec.global("ptz.preset.query", "查询预置点", "POST",
                    AIOT + "/queryPreset", "V1", "ptz"),
            PlatformApiSpec.global("ptz.preset.delete", "删除预置点", "POST",
                    AIOT + "/deletePreset", "V1", "ptz"),

            // ---------- 门禁 ----------
            PlatformApiSpec.global("access.record", "查询开门记录", "GET",
                    IOT + "/accessControl/getDoorOpenRecord", "V1", "access"),
            PlatformApiSpec.global("access.open", "远程开门 V2", "POST",
                    IOT + "/v2/device/accessControl/openDoorRemote", "V1", "access"),
            PlatformApiSpec.global("access.status", "设置开门状态", "POST",
                    IOT + "/accessControl/setOpenDoorStatus", "V1", "access"),
            PlatformApiSpec.global("access.plan.list", "查询设备开门计划", "GET",
                    IOT + "/accessControl/getPlanListByDeviceId", "V1", "access"),
            PlatformApiSpec.global("access.plan.add", "新增普通开门计划", "POST",
                    IOT + "/accessControl/addPlan", "V1", "access"),
            PlatformApiSpec.global("access.plan.update", "修改普通开门计划", "POST",
                    IOT + "/accessControl/updatePlan", "V1", "access"),
            PlatformApiSpec.global("access.plan.delete", "删除普通开门计划", "POST",
                    IOT + "/accessControl/removePlan", "V1", "access"),
            PlatformApiSpec.global("access.plan.dispatch", "下发普通开门计划", "POST",
                    IOT + "/accessControl/dispatchPlans", "V1", "access"),
            PlatformApiSpec.global("access.userPlans", "人员关联开门计划", "POST",
                    IOT + "/accessControl/setUserPlans", "V1", "access"),
            PlatformApiSpec.global("access.user.page", "分页查询门禁人员", "GET",
                    IOT + "/accessControl/getUserInfoPage", "V1", "access"),

            // ---------- 组织 / 用户 ----------
            PlatformApiSpec.global("org.tree", "查询组织树", "GET",
                    "/open-api/api-biz/org/getTree", "V1", "org"),
            PlatformApiSpec.global("org.root", "查询根节点", "GET",
                    "/open-api/api-biz/org/getRootInfo", "V1", "org"),
            PlatformApiSpec.global("org.info", "查询组织基础信息", "GET",
                    "/open-api/api-biz/org/getOrgInfo", "V1", "org"),
            PlatformApiSpec.global("org.add", "新增组织节点", "POST",
                    "/open-api/api-biz/org/addOrg", "V1", "org"),
            PlatformApiSpec.global("org.update", "更新组织节点", "POST",
                    "/open-api/api-biz/org/updateOrg", "V1", "org"),
            PlatformApiSpec.global("org.delete", "删除组织节点", "POST",
                    "/open-api/api-biz/org/delByOrgId", "V1", "org"),
            PlatformApiSpec.global("user.current", "获取当前登录用户信息", "GET",
                    "/open-api/api-biz/user/getUserInfo", "V1", "user"),
            PlatformApiSpec.global("user.page", "分页查询用户列表", "POST",
                    "/open-api/api-biz/user/page", "V1", "user"),
            PlatformApiSpec.global("user.add", "添加用户", "POST",
                    "/open-api/api-biz/user/addUserWithPwd", "V1", "user"),
            PlatformApiSpec.global("user.update", "更新用户信息", "POST",
                    "/open-api/api-biz/user/update", "V1", "user"),
            PlatformApiSpec.global("user.delete", "批量删除用户", "POST",
                    "/open-api/api-biz/user/batchDelete", "V1", "user"),

            // ---------- 消息 ----------
            PlatformApiSpec.global("msg.pull", "分页查询消息", "GET",
                    MSG + "/getMessage", "V1", "message"),

            // ---------- 国标 GB28181 ----------
            PlatformApiSpec.global("gb.sip", "获取国标设备注册信息", "POST",
                    IOT + "/getSipInfo", "V1", "gb"),
            PlatformApiSpec.global("gb.code", "获取国标码列表", "POST",
                    IOT + "/listGbCode", "V1", "gb"),
            PlatformApiSpec.global("gb.add", "添加国标设备", "POST",
                    IOT + "/addGbDevice", "V1", "gb"),
            PlatformApiSpec.global("gb.update", "修改国标设备信息", "POST",
                    IOT + "/modifyGbDevice", "V1", "gb"),
            PlatformApiSpec.global("gb.details", "国标设备查询详细信息", "POST",
                    IOT + "/deviceInfoDetailAll", "V1", "gb"),

            // ---------- SD 卡 ----------
            PlatformApiSpec.global("sd.status", "查询 SD 卡状态", "POST",
                    AIOT + "/getSDCardStatus", "V1", "storage"),
            PlatformApiSpec.global("sd.storage", "查询 SD 卡容量", "POST",
                    AIOT + "/getSDCardStorage", "V1", "storage"),
            PlatformApiSpec.global("sd.list", "获取 SD 卡列表", "POST",
                    AIOT + "/listSDCardStorage", "V1", "storage"),
            PlatformApiSpec.global("sd.format", "格式化 SD 卡", "POST",
                    AIOT + "/formatSDCard", "V1", "storage"),

            // ---------- 铃音 ----------
            PlatformApiSpec.global("ring.list", "获取铃声列表", "POST",
                    AIOT + "/listCustomRing", "V1", "config"),
            PlatformApiSpec.global("ring.add", "新增自定义铃声", "POST",
                    AIOT + "/addCustomRing", "V1", "config"),
            PlatformApiSpec.global("ring.delete", "删除自定义铃声", "POST",
                    AIOT + "/deleteCustomRing", "V1", "config"),
            PlatformApiSpec.global("ring.set", "设置铃声", "POST",
                    AIOT + "/setCustomRing", "V1", "config"),

            // ---------- WiFi ----------
            PlatformApiSpec.global("wifi.current", "获取设备当前热点", "POST",
                    AIOT + "/currentDeviceWifi", "V1", "config"),
            PlatformApiSpec.global("wifi.around", "获取周边热点", "POST",
                    AIOT + "/wifiAround", "V1", "config"),
            PlatformApiSpec.global("wifi.control", "修改设备连接热点", "POST",
                    AIOT + "/controlDeviceWifi", "V1", "config"),

            // ---------- 设备配置 ----------
            PlatformApiSpec.global("ability.get", "获取设备使能状态", "POST",
                    AIOT + "/getAbilityStatus", "V1", "config"),
            PlatformApiSpec.global("ability.set", "设置设备使能状态", "POST",
                    AIOT + "/setAbilityStatus", "V1", "config"),
            PlatformApiSpec.global("device.utc", "设备校时", "POST",
                    AIOT + "/setCurrentUTC", "V1", "config"),
            PlatformApiSpec.global("device.sim", "获取 SIM 信号强度", "POST",
                    AIOT + "/getSimSignalStrength", "V1", "config"),

            // ---------- 图片解密 ----------
            PlatformApiSpec.global("image.decrypt", "图片解密", "POST",
                    "/open-api/api-decrypto/image/decrypto", "V1", "media"),

            // ---------- 消息订阅 ----------
            PlatformApiSpec.global("msg.callback.add", "添加回调配置", "POST",
                    MSG + "/addCallbackConfig", "V1", "message"),
            PlatformApiSpec.global("msg.callback.list", "分页获取回调配置", "POST",
                    MSG + "/getAllCallbackConfigId", "V1", "message"),
            PlatformApiSpec.global("msg.callback.info", "搜索回调配置信息", "POST",
                    MSG + "/getInfoByCallbackConfigId", "V1", "message"),
            PlatformApiSpec.global("msg.callback.update", "更新回调配置", "POST",
                    MSG + "/updateByCallbackConfigId", "V1", "message"),
            PlatformApiSpec.global("msg.callback.delete", "删除回调配置", "POST",
                    MSG + "/deleteCallbackId", "V1", "message"),
            PlatformApiSpec.global("msg.subscribe", "按设备ID列表订阅消息", "POST",
                    MSG + "/messageSubscribeByDeviceIds", "V1", "message"),
            PlatformApiSpec.global("msg.subscribe.update", "根据回调配置更新订阅", "POST",
                    MSG + "/updateSubscribeByCallbackConfigId", "V1", "message"),
            PlatformApiSpec.global("msg.subscribe.byConfig", "搜索已订阅设备消息", "POST",
                    MSG + "/getSubscribeInfoByCallbackConfigId", "V1", "message"),
            PlatformApiSpec.global("msg.subscribe.byDevice", "按设备ID查订阅信息", "POST",
                    MSG + "/getMessageSubscribeInfoByDeviceId", "V1", "message"),
            PlatformApiSpec.global("msg.type.page", "按设备类别获取消息类型", "POST",
                    MSG + "/getMessageTypePage", "V1", "message")
    );

    /**
     * 全部接口目录。
     *
     * @return 接口声明
     */
    public static List<PlatformApiSpec> all() {
        return ALL;
    }
}
