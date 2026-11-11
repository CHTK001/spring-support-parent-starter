package com.chua.starter.device.provider.sdk.dahua;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 大华 ICC / DSS 私有化平台接口目录。
 *
 * <p><b>性质</b>：ICC（智能物联综合管理平台）与 DSS（数字监控系统）都是<b>私有化部署</b>，
 * 网关地址 = 客户现场服务器地址（形如 {@code https://10.x.x.x:443}），无固定公网域名。
 * 因此 {@code baseUrl} 必须由凭据填写（与综合安防一致）。</p>
 *
 * <p>接口走大华私有平台的 {@code /gateway/...} 或 {@code /openapi/...} 形态，
 * 需在客户现场控制台核对；凭据可用 {@code extra("apiPath.&lt;code&gt;")} 覆盖。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class DahuaIccApis {

    /** 大华私有平台网关前缀（待现场核对） */
    private static final String P = "/gateway";

    /**
     * 工具类不可实例化。
     */
    private DahuaIccApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // ---------- 认证 ----------
            PlatformApiSpec.global("auth.token", "登录/获取 token", "POST", P + "/auth/api/oauth/token", "v1", "other"),

            // ---------- 设备 ----------
            PlatformApiSpec.global("device.list", "查询设备列表", "POST", P + "/device/api/page", "v1", "device"),
            PlatformApiSpec.global("device.info", "设备详情", "POST", P + "/device/api/deviceInfo", "v1", "device"),
            PlatformApiSpec.global("device.channels", "设备通道列表", "POST", P + "/device/api/channel", "v1", "device"),
            PlatformApiSpec.global("device.status", "设备在线状态", "POST", P + "/device/api/status", "v1", "device"),

            // ---------- 组织 ----------
            PlatformApiSpec.global("org.list", "组织列表", "POST", P + "/membership/api/org/list", "v1", "org"),
            PlatformApiSpec.global("org.generateId", "部门全局 ID 生成", "GET",
                    "/evo-apigw/evo-brm/1.0.0/department/generate-id", "1.0.0", "org"),
            PlatformApiSpec.global("org.add", "部门新增", "POST",
                    "/evo-apigw/evo-brm/1.0.0/department/add", "1.0.0", "org"),
            PlatformApiSpec.global("org.update", "部门更新", "PUT",
                    "/evo-apigw/evo-brm/1.0.0/department/update", "1.0.0", "org"),
            PlatformApiSpec.global("org.delete", "部门删除", "DELETE",
                    "/evo-apigw/evo-brm/1.0.0/department/delete", "1.0.0", "org"),

            // ---------- 视频 ----------
            PlatformApiSpec.global("video.live", "实时预览地址", "POST", P + "/video/api/live", "v1", "video"),
            PlatformApiSpec.global("video.playback", "回放地址", "POST", P + "/video/api/playback", "v1", "video"),
            PlatformApiSpec.global("video.records", "录像查询", "POST", P + "/video/api/records", "v1", "video"),
            PlatformApiSpec.global("video.capture", "设备抓图", "POST", P + "/video/api/capture", "v1", "video"),
            PlatformApiSpec.global("video.ptz", "云台控制", "POST", P + "/video/api/ptz", "v1", "video"),

            // ---------- 门禁 / 人员 ----------
            PlatformApiSpec.global("access.record", "门禁记录", "POST", P + "/access/api/record", "v1", "access"),
            PlatformApiSpec.global("access.door.open", "远程开门", "POST", P + "/access/api/door/open", "v1", "access"),
            PlatformApiSpec.global("person.list", "人员列表", "POST", P + "/person/api/list", "v1", "person"),
            PlatformApiSpec.global("person.generateId", "人员全局 ID 生成", "GET",
                    "/evo-apigw/evo-brm/1.0.0/person/generate-id", "1.0.0", "person"),
            PlatformApiSpec.global("person.add", "人员新增", "POST",
                    "/evo-apigw/evo-brm/1.2.0/person/subsystem/add", "1.2.0", "person"),
            PlatformApiSpec.global("person.update", "人员更新", "PUT",
                    "/evo-apigw/evo-brm/1.2.0/person/subsystem/update", "1.2.0", "person"),
            PlatformApiSpec.global("person.delete", "人员删除", "POST",
                    "/evo-apigw/evo-brm/1.0.0/person/delete", "1.0.0", "person"),

            // ---------- 告警 ----------
            PlatformApiSpec.global("alarm.list", "告警列表", "POST", P + "/alarm/api/list", "v1", "alarm")
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
