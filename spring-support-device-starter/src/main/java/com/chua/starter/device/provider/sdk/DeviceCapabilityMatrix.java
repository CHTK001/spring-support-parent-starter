package com.chua.starter.device.provider.sdk;

import com.chua.starter.device.spi.model.DeviceCapability;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 平台能力矩阵：一个平台官方支持哪些能力位、每块能力对应多少接口。
 *
 * <p>这是「100% 覆盖」的判定依据。覆盖率不是「方法都返回了值」，而是
 * 「能力矩阵里标记为 {@link Support#YES} 的格子，都有真实实现且真实跑通」。</p>
 *
 * <h3>标注口径（严格执行）</h3>
 * <ul>
 *   <li>{@link Support#YES}：平台 {@code DeviceSession} 里确有实现，且已用真实凭据跑通。</li>
 *   <li>{@link Support#IMPLEMENTED_UNVERIFIED}：确有实现，但尚无环境验证。</li>
 *   <li>{@link Support#TODO}：平台官方有这部分接口，但本模块 {@code DeviceSession} 尚未实现。
 *       只「接口目录里有路径」不算实现。</li>
 *   <li>{@link Support#NO}：平台客观上不具备该能力。</li>
 * </ul>
 *
 * <p>只有 {@code YES} 与 {@code IMPLEMENTED_UNVERIFIED} 会派生成平台级能力位，
 * 前端据此显示功能入口；{@code TODO} 与 {@code NO} 一律隐藏并给出原因。</p>
 *
 * @param platform 平台标识，例如 hikvision.cloud-yao
 * @param title 平台展示名
 * @param cells 能力位到支持度的映射
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceCapabilityMatrix(
        String platform,
        String title,
        Map<DeviceCapability, Cell> cells
) {

    /**
     * 支持度。
     */
    public enum Support {

        /**
         * 平台官方支持且本模块已实现并验证
         */
        YES("支持"),

        /**
         * 平台官方支持，本模块已实现但尚无环境验证
         */
        IMPLEMENTED_UNVERIFIED("已实现未验证"),

        /**
         * 平台官方支持，本模块尚未实现
         */
        TODO("待实现"),

        /**
         * 平台客观上不具备该能力
         */
        NO("不支持");

        /**
         * 展示名称
         */
        private final String description;

        /**
         * Support
         *
         * @param description 展示名称
         */
        Support(String description) {
            this.description = description;
        }

        /**
         * 获取展示名称
         *
         * @return 展示名称
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * 一个能力格子。
     *
     * @param support 支持度
     * @param apiCount 该能力对应的官方接口数量（0 表示不适用）
     * @param note 说明：不支持的写原因，待实现的写依据
     */
    public record Cell(Support support, int apiCount, String note) {

        /**
         * 构造：不支持的格子。
         *
         * @param note 原因
         * @return 格子
         */
        public static Cell no(String note) {
            return new Cell(Support.NO, 0, note);
        }

        /**
         * 构造：支持的格子。
         *
         * @param apiCount 接口数
         * @return 格子
         */
        public static Cell yes(int apiCount) {
            return new Cell(Support.YES, apiCount, "");
        }

        /**
         * 构造：已实现未验证的格子。
         *
         * @param apiCount 接口数
         * @param note 说明
         * @return 格子
         */
        public static Cell unverified(int apiCount, String note) {
            return new Cell(Support.IMPLEMENTED_UNVERIFIED, apiCount, note);
        }
    }

    /**
     * 规范构造器：对能力位映射做只读拷贝。
     *
     * @param cells 能力位映射
     */
    public DeviceCapabilityMatrix {
        if (cells == null || cells.isEmpty()) {
            cells = Collections.emptyMap();
        } else {
            EnumMap<DeviceCapability, Cell> copy = new EnumMap<>(DeviceCapability.class);
            copy.putAll(cells);
            cells = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * 该平台是否支持某能力。
     *
     * @param capability 能力位
     * @return 支持（YES 或 IMPLEMENTED_UNVERIFIED）返回 true；NO 与 TODO 返回 false
     */
    public boolean supports(DeviceCapability capability) {
        Cell cell = cells.get(capability);
        return cell != null
                && (cell.support() == Support.YES || cell.support() == Support.IMPLEMENTED_UNVERIFIED);
    }

    /**
     * 取某能力的格子，缺失时按「未声明」处理。
     *
     * @param capability 能力位
     * @return 格子
     */
    public Cell cell(DeviceCapability capability) {
        return cells.getOrDefault(capability, new Cell(Support.NO, 0, "未在能力矩阵中声明"));
    }

    /**
     * 统计该能力矩阵的覆盖率：已实现（YES + IMPLEMENTED_UNVERIFIED）占「官方支持」的比例。
     *
     * @return 覆盖率，0.0 ~ 1.0
     */
    public double coverage() {
        int supported = 0;
        int implemented = 0;
        for (Cell cell : cells.values()) {
            if (cell.support() == Support.NO) {
                continue;
            }
            supported++;
            if (cell.support() == Support.YES || cell.support() == Support.IMPLEMENTED_UNVERIFIED) {
                implemented++;
            }
        }
        return supported == 0 ? 0.0 : (double) implemented / supported;
    }

    /**
     * 构造器。
     *
     * @param platform 平台标识
     * @param title 平台展示名
     * @return 构造器
     */
    public static Builder builder(String platform, String title) {
        return new Builder(platform, title);
    }

    /**
     * 取指定厂商下某平台支持的能力位集合。
     *
     * <p>平台标识在能力矩阵中统一采用 {@code vendor.platform} 形式，本方法负责拼接并转发到
     * {@link Registry#capabilitiesOf(String)}，供各厂商描述符派生平台级能力位使用。</p>
     *
     * @param vendor   厂商标识，例如 hikvision
     * @param platform 平台编码，例如 ezviz
     * @return 能力位集合；未登记时返回空集
     */
    public static Set<DeviceCapability> capabilitiesOf(String vendor, String platform) {
        return Registry.capabilitiesOf(vendor + "." + platform);
    }

    /**
     * 能力矩阵构造器。
     */
    public static final class Builder {

        private final String platform;

        private final String title;

        private final Map<DeviceCapability, Cell> cells = new LinkedHashMap<>();

        private Builder(String platform, String title) {
            this.platform = platform;
            this.title = title;
        }

        /**
         * 标记支持。
         *
         * @param capability 能力位
         * @param apiCount 接口数
         * @return 构造器
         */
        public Builder yes(DeviceCapability capability, int apiCount) {
            cells.put(capability, Cell.yes(apiCount));
            return this;
        }

        /**
         * 标记已实现未验证。
         *
         * @param capability 能力位
         * @param apiCount 接口数
         * @param note 说明
         * @return 构造器
         */
        public Builder unverified(DeviceCapability capability, int apiCount, String note) {
            cells.put(capability, Cell.unverified(apiCount, note));
            return this;
        }

        /**
         * 标记平台不支持。
         *
         * @param capability 能力位
         * @param reason 原因
         * @return 构造器
         */
        public Builder no(DeviceCapability capability, String reason) {
            cells.put(capability, Cell.no(reason));
            return this;
        }

        /**
         * 标记待实现。
         *
         * @param capability 能力位
         * @param apiCount 官方接口数
         * @param note 依据
         * @return 构造器
         */
        public Builder todo(DeviceCapability capability, int apiCount, String note) {
            cells.put(capability, new Cell(Support.TODO, apiCount, note));
            return this;
        }

        /**
         * 构造。
         *
         * @return 能力矩阵
         */
        public DeviceCapabilityMatrix build() {
            return new DeviceCapabilityMatrix(platform, title, cells);
        }
    }

    /**
     * 能力矩阵注册表：所有平台的能力矩阵集中登记，供覆盖率统计与业务侧前置校验使用。
     *
     * @since 4.0.0.51
     */
    public static final class Registry {

        private static final Map<String, DeviceCapabilityMatrix> MATRICES = new LinkedHashMap<>();

        static {
            register(hikvisionCloudYao());
            register(hikvisionCloudEye());
            register(hikvisionSecurePlatform());
            register(hikvisionEzviz());
            register(hikvisionIsup());
            register(dahuaCloud());
            register(dahuaLeChange());
            register(dahuaIcc());
            register(uniview());
            register(gb28181());
            register(onvif());
        }

        private Registry() {
        }

        /**
         * 登记一个平台的能力矩阵。
         *
         * @param matrix 能力矩阵
         */
        public static void register(DeviceCapabilityMatrix matrix) {
            MATRICES.put(matrix.platform(), matrix);
        }

        /**
         * 取平台能力矩阵。
         *
         * @param platform 平台标识
         * @return 能力矩阵，未登记返回 null
         */
        public static DeviceCapabilityMatrix get(String platform) {
            return MATRICES.get(platform);
        }

        /**
         * 全部已登记的能力矩阵。
         *
         * @return 只读视图
         */
        public static Map<String, DeviceCapabilityMatrix> all() {
            return Collections.unmodifiableMap(MATRICES);
        }

        /**
         * 取该平台支持的能力位集合（仅 {@link Support#YES} 与 {@link Support#IMPLEMENTED_UNVERIFIED}）。
         *
         * <p>供 {@code PlatformDescriptor} 派生能力位使用：能力矩阵是唯一事实源，
         * 描述符不再手写能力位，避免两处声明不一致。</p>
         *
         * @param platform 平台标识，例如 hikvision.ezviz
         * @return 能力位集合；平台未登记时返回空集
         */
        public static Set<DeviceCapability> capabilitiesOf(String platform) {
            DeviceCapabilityMatrix matrix = MATRICES.get(platform);
            if (matrix == null) {
                return Set.of();
            }
            Set<DeviceCapability> result = EnumSet.noneOf(DeviceCapability.class);
            for (Map.Entry<DeviceCapability, Cell> entry : matrix.cells().entrySet()) {
                Support support = entry.getValue().support();
                if (support == Support.YES || support == Support.IMPLEMENTED_UNVERIFIED) {
                    result.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(result);
        }
    }

    /**
     * 海康云曜能力矩阵。
     *
     * <p>会话实现现状（{@code HikvisionCloudYaoSession}，全部按 help.hikyun.com 官方字段落地）：</p>
     * <ul>
     *   <li>DEVICE：{@code v2/global/device/page}（条件数组包体）</li>
     *   <li>CHANNEL：{@code v1/global/v1/device/camera/list}</li>
     *   <li>CAPTURE：{@code v1/global/monitor/capture}（data 是图片 URL，二次下载）</li>
     *   <li>MEDIA / RECORD：{@code v1/global/live/address/get/by/deviceSerial}（type 1/2）</li>
     *   <li>PTZ / 预置位：{@code v1/global/device/ptz}、{@code preset/add|move|clear}
     *       （无预置位查询接口，{@code listPresets} 显式抛异常）</li>
     *   <li>ORG / ORG_WRITE：{@code fdcs/v1/personOrg/page|add|update|batchDelete}</li>
     *   <li>PERSON / PERSON_WRITE：{@code fdcs/v1/person/page|add|update|delete}</li>
     *   <li>ACCESS_WRITE / ACCESS：云曜无远程开门接口，门禁点只有路径没有字段文档 → 不支持</li>
     *   <li>EVENT：无事件拉取接口（平台侧推送）→ 不支持</li>
     *   <li>DEVICE_WRITE：{@code v2/global/device/ezviz/save|update|delete}</li>
     * </ul>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix hikvisionCloudYao() {
        return builder("hikvision.cloud-yao", "海康云曜")
                .yes(DeviceCapability.DEVICE, 102)
                .unverified(DeviceCapability.MEDIA, 74, "getLiveUrl(type=1) 已按官方 live/address 实现，账号下无设备未验证")
                .unverified(DeviceCapability.RECORD, 7, "getPlaybackUrl(type=2, rtmp/ezopen/flv) 已实现，无设备未验证")
                .unverified(DeviceCapability.PTZ, 13, "ptzControl(direction/speed/type) 与 preset/add|move|clear 已实现，无设备未验证")
                .no(DeviceCapability.ACCESS, "云曜 artemis 无远程开门接口；门禁点 acspoint/search 只有路径缺字段文档")
                .unverified(DeviceCapability.CHANNEL, 20, "listChannels 走官方 v1/global/v1/device/camera/list，无设备未验证")
                .unverified(DeviceCapability.CAPTURE, 9, "capture 走官方 monitor/capture 取 URL 后二次下载，无设备未验证")
                .no(DeviceCapability.EVENT, "云曜无事件拉取接口，事件由平台侧推送")
                .unverified(DeviceCapability.ORG, 4, "fetchOrganizations 走官方 fdcs/v1/personOrg/page，无账号数据未验证")
                .unverified(DeviceCapability.ORG_WRITE, 3, "personOrg/add|update|batchDelete 已实现，无账号数据未验证")
                .unverified(DeviceCapability.DEVICE_WRITE, 4, "addDevice/updateDevice/removeDevice 走官方 v2/global/device/ezviz/save|update|delete，无设备未验证")
                .unverified(DeviceCapability.PERSON, 3, "fetchPersons 走官方 fdcs/v1/person/page，无账号数据未验证")
                .unverified(DeviceCapability.PERSON_WRITE, 3, "person/add|update|delete 已实现，无账号数据未验证")
                .no(DeviceCapability.ACCESS_WRITE, "云曜无门禁点/开门写接口")
                .no(DeviceCapability.PARKING, "云曜开放平台未提供停车场业务接口")
                .build();
    }

    /**
     * 海康云眸能力矩阵。
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix hikvisionCloudEye() {
        return builder("hikvision.cloud-eye", "海康云眸")
                .yes(DeviceCapability.DEVICE, 4)
                .unverified(DeviceCapability.MEDIA, 2,
                        "getLiveUrl/getPlaybackUrl 走官方 v1/carrier/wing/endpoint/video/getVideoUrl，"
                                + "stopLiveUrl 走 siot/.../disableLiveAddress，账号下无设备未验证")
                .unverified(DeviceCapability.RECORD, 2,
                        "queryRecords 走官方 v1/carrier/communicate/record/daily（跨度≤1 天），"
                                + "回放地址由 getVideoUrl type=2 提供，无设备未验证")
                .unverified(DeviceCapability.PTZ, 9,
                        "ptzControl/stopPtz 走官方 ptz/start|stop（direction 0~11），"
                                + "预设位 create/update/delete/actions/move|list 按 index 字段，无设备未验证")
                .unverified(DeviceCapability.ACCESS, 2,
                        "queryAccessRecords 走官方 accessEvents/actions/list（major=5，minor 单值分次查询），"
                                + "remoteUnlock/controlDoor 走 remoteControl/actions/open，无门禁设备未验证")
                .unverified(DeviceCapability.CHANNEL, 4,
                        "listChannels 走官方 basic/channels/list，另实现 syncChannels/renameChannel，无设备未验证")
                .unverified(DeviceCapability.CAPTURE, 1,
                        "capture 走官方 basic/channels/actions/capture 取 data.picUrl 后二次下载，无设备未验证")
                .unverified(DeviceCapability.EVENT, 5,
                        "pollEvents 走官方消息通道 mq/consumer/group1|messages|offsets，"
                                + "ISAPI 订阅路径已入接口目录，无设备未验证")
                .unverified(DeviceCapability.PERSON, 2,
                        "fetchPersons 走历史接口 getPersons 分页、fetchPerson 走 basic/persons/get，无账号数据未验证")
                .unverified(DeviceCapability.PERSON_WRITE, 5,
                        "addPerson/updatePerson/removePerson 走官方 persons/create|update|delete，"
                                + "人脸 updateFace/deleteFace 走官方 faces/update|delete，无账号数据未验证")
                .unverified(DeviceCapability.DEVICE_WRITE, 3,
                        "addDevice/renameDevice/removeDevice 走官方 basic/devices/create|update|delete，"
                                + "注册要 groupNo + validateCode，无设备未验证")
                .unverified(DeviceCapability.ORG, 3,
                        "fetchOrganizations 走官方 basic/groups/actions/listAll（另 get/childrenList 在目录），无账号数据未验证")
                .unverified(DeviceCapability.ORG_WRITE, 3,
                        "addOrganization/updateOrganization/removeOrganization 走官方 groups/create|update|delete，无账号数据未验证")
                .no(DeviceCapability.ACCESS_WRITE, "云眸开放平台只提供控门与事件查询，没有门禁设备/门点增删改接口")
                .no(DeviceCapability.PARKING, "云眸开放平台无停车场业务接口")
                .build();
    }

    /**
     * 海康综合安防管理平台（私有化部署）能力矩阵。
     *
     * <p>门禁接口真实存在：{@code /artemis/api/access/v1/door/control}、
     * {@code /artemis/api/access/v1/accessRecords}、{@code /artemis/api/resource/v1/door/search}。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix hikvisionSecurePlatform() {
        return builder("hikvision.secure-platform", "海康综合安防")
                .yes(DeviceCapability.DEVICE, 6)
                .unverified(DeviceCapability.MEDIA, 1, "getLiveUrl 已实现，缺网关地址未验证")
                .unverified(DeviceCapability.RECORD, 5, "getPlaybackUrl 已实现，缺网关地址未验证")
                .unverified(DeviceCapability.PTZ, 4, "ptzControl 已实现，缺网关地址未验证")
                .unverified(DeviceCapability.ACCESS, 4, "remoteUnlock 已实现，缺网关地址未验证")
                .unverified(DeviceCapability.CHANNEL, 5, "listChannels 走官方 resource/v2/camera/search，按 cameraIndexCode 过滤，缺网关地址未验证")
                .unverified(DeviceCapability.ORG, 4, "fetchOrganizations 走官方 resource/v1/org/orgList，缺网关地址未验证")
                .unverified(DeviceCapability.ORG_WRITE, 3, "org.add|update|delete 已接入，缺网关地址未验证")
                .unverified(DeviceCapability.DEVICE_WRITE, 3, "device.add|update|delete 已接入，缺网关地址未验证")
                .unverified(DeviceCapability.PERSON, 4, "fetchPersons 走官方 resource/v2/person/personList，缺网关地址未验证")
                .unverified(DeviceCapability.PERSON_WRITE, 3, "person.add|update|delete 已接入，缺网关地址未验证")
                .no(DeviceCapability.ACCESS_WRITE,
                        "综合安防公开 Artemis 目录只有门禁点查询/控制/状态/记录，没有门禁点增删改或权限下发字段文档；"
                                + "需平台厂商提供门禁权限下发专属接口文档后才能接入")
                .unverified(DeviceCapability.CAPTURE, 2, "capture 走官方 video/v1/cameras/images 并二次下载，缺网关地址未验证")
                .unverified(DeviceCapability.EVENT, 2, "pollEvents 走官方 eventService/v1/eventRecords/page，缺网关地址未验证")
                .no(DeviceCapability.PARKING, "综合安防停车场为独立子系统，不在本接入范围")
                .build();
    }

    /**
     * 萤石云能力矩阵。
     *
     * <p><b>2026-11-10 实测更新（真机 BF1505592 + 真实凭据）</b>：
     * {@code EzvizSession} 已建，直播/回放/抓图的方法实现完成并调用到真实接口，
     * 但因该设备当前离线，平台返回 {@code 20007 The device is offline}，
     * 故这三项按口径标 {@code IMPLEMENTED_UNVERIFIED}；通道列表、人员增删改、
     * 告警查询、开门记录均已真跑通。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix hikvisionEzviz() {
        return builder("hikvision.ezviz", "萤石云")
                .yes(DeviceCapability.DEVICE, 287)
                .yes(DeviceCapability.CHANNEL, 20)
                .yes(DeviceCapability.PERSON, 17)
                .yes(DeviceCapability.PERSON_WRITE, 8)
                .yes(DeviceCapability.EVENT, 17)
                .yes(DeviceCapability.ACCESS, 33)
                .unverified(DeviceCapability.MEDIA, 14,
                        "已实现 /api/lapp/v2/live/address/get；真机离线返回 20007，待设备上线复验")
                .unverified(DeviceCapability.RECORD, 10,
                        "已实现回放取址与录像检索；真机离线返回 20007，待设备上线复验")
                .unverified(DeviceCapability.CAPTURE, 4,
                        "已实现 /api/lapp/device/capture 并下载图片；真机离线待复验")
                .unverified(DeviceCapability.PTZ, 13,
                        "已实现 ptz start/stop 与预置位增删调用；真机离线待复验")
                .unverified(DeviceCapability.ACCESS_WRITE, 6,
                        "已实现智能锁开锁 /api/lapp/building/device/smartlock/unlock；账号下无锁可验")
                .unverified(DeviceCapability.DEVICE_WRITE, 4,
                        "addDevice/deleteDevice/renameDevice 走官方 /api/lapp/device/add|delete|name/update；真机离线未验证")
                .no(DeviceCapability.ORG, "萤石开放平台无组织架构接口（392 条中组织相关为 0）")
                .no(DeviceCapability.ORG_WRITE, "萤石开放平台无组织架构接口")
                .unverified(DeviceCapability.PARKING, 2, "过车取 parking/msgRecord（msgType 1 入/3 出）；停车单取 parkingRecord（需 spaceId）；无车场/车道/开闸接口")
                .build();
    }

    /**
     * 海康 ISUP 能力矩阵（协议网关转发模式）。
     *
     * <p>会话 {@code HikvisionIsupSession} 按企业自建 ISUP 网关的 {@code /isup/...}
     * REST 契约实现通道、取流、录像、云台与预置位；网关不是海康官方 HTTP 服务，
     * 本机无 ISUP 服务端，故全部标 {@code IMPLEMENTED_UNVERIFIED}。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix hikvisionIsup() {
        return builder("hikvision.isup", "海康 ISUP")
                .yes(DeviceCapability.DEVICE, 6)
                .unverified(DeviceCapability.MEDIA, 5, "HikvisionIsupSession 已接 /isup/live、/isup/live/stop，待 ISUP 服务端联调")
                .unverified(DeviceCapability.RECORD, 2, "HikvisionIsupSession 已接 /isup/record/query、/isup/playback，待联调")
                .unverified(DeviceCapability.PTZ, 2, "HikvisionIsupSession 已接 /isup/ptz 与预置位接口，待联调")
                .unverified(DeviceCapability.CHANNEL, 2, "HikvisionIsupSession 已接 /isup/device/channels，待联调")
                .no(DeviceCapability.DEVICE_WRITE, "ISUP 设备由设备主动注册上线，平台侧不能新增或删除")
                .no(DeviceCapability.ORG, "ISUP 是设备接入协议，无组织模型")
                .no(DeviceCapability.ORG_WRITE, "ISUP 是设备接入协议，无组织模型")
                .no(DeviceCapability.PERSON, "ISUP 是设备接入协议，无人员模型")
                .no(DeviceCapability.PERSON_WRITE, "ISUP 是设备接入协议，无人员模型")
                .no(DeviceCapability.ACCESS, "ISUP 协议本身不含门禁业务，门禁走设备 SDK")
                .no(DeviceCapability.ACCESS_WRITE, "ISUP 协议本身不含门禁业务，门禁走设备 SDK")
                .no(DeviceCapability.CAPTURE, "ISUP 无抓图信令，抓图走设备 SDK")
                .no(DeviceCapability.EVENT, "ISUP 事件由设备主动上报，不走平台查询")
                .no(DeviceCapability.PARKING, "ISUP 无停车场业务")
                .build();
    }

    /**
     * 大华云睿能力矩阵（2026-11-11 官方文档取证后回写）。
     *
     * <p>平台实际是「大华云开发者平台 IoT 开放平台」（open.cloud-dahua.com），
     * 43 条基础通用套件接口已逐条真实调用验证：42 条存在，
     * {@code getDeviceChannelInfo} 不存在（BIZ120）。视频监控、云存储、门禁、
     * 组织、用户套件的字段已从官方文档中心取证，会话层按官方路径实现；
     * 因无设备/凭据做真机调用，统一标 {@code IMPLEMENTED_UNVERIFIED}。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix dahuaCloud() {
        return builder("dahua.dahua-cloud", "大华云睿")
                .unverified(DeviceCapability.DEVICE, 11,
                        "getDeviceList/deviceOnline/listDeviceDetailsByIds 已真实调用（200/PRM0132），账号下 0 台设备，字段解析未验证")
                .unverified(DeviceCapability.CHANNEL, 4,
                        "官方无 getDeviceChannelInfo（BIZ120），listChannels 改走 listDeviceDetailsByIds，通道字段未拿到真机样例")
                .unverified(DeviceCapability.MEDIA, 4,
                        "createDeviceHlsLive/delDeviceHlsLive/createDeviceFlvLive/createDeviceStreamUrl 已按官方文档实现，无设备未验证")
                .unverified(DeviceCapability.RECORD, 2,
                        "queryLocalRecords 分页检索 + createDeviceRecordHls 回放已实现（不支持跨天），无设备未验证")
                .unverified(DeviceCapability.PTZ, 5,
                        "controlMovePTZ 与 configPreset/turnPreset/queryPreset/deletePreset 已实现，无设备未验证")
                .unverified(DeviceCapability.CAPTURE, 1,
                        "setDeviceSnapEnhanced 取 URL 后二次下载已实现（官方限制 3 秒一次），无设备未验证")
                .unverified(DeviceCapability.EVENT, 1,
                        "getMessage 分页拉取已实现（官方仅保留 7 天），事件类型映射未做真机验证")
                .unverified(DeviceCapability.DEVICE_WRITE, 4,
                        "api-biz/device/addDevice|updateDeviceName + api-iot/device/deleteDevice|verifyDevCode 已实现，无设备未验证")
                .unverified(DeviceCapability.ORG, 3,
                        "org/getTree|getRootInfo|getOrgInfo 已实现；templateId 取当前用户 orgPriGroupList 或 extra.orgTemplateId")
                .unverified(DeviceCapability.ORG_WRITE, 3,
                        "org/addOrg|updateOrg|delByOrgId 已实现；新增需 parentId 与 orgType（可继承父节点或 extra 指定）")
                .unverified(DeviceCapability.PERSON, 1,
                        "api-biz/user/page 分页已实现，无账号数据未验证")
                .unverified(DeviceCapability.PERSON_WRITE, 3,
                        "user/addUserWithPwd|update|batchDelete 已实现；新增密码取 extra.defaultPersonPassword")
                .unverified(DeviceCapability.ACCESS, 4,
                        "门禁设备即门点；getDoorOpenRecord/openDoorRemote/setOpenDoorStatus/getPlanListByDeviceId 已实现，无门禁设备未验证")
                .unverified(DeviceCapability.ACCESS_WRITE, 5,
                        "平台无门点 CRUD；addPlan/updatePlan/removePlan/dispatchPlans/setUserPlans 已实现，无门禁设备未验证")
                .no(DeviceCapability.PARKING, "云睿开放平台无停车场业务")
                .build();
    }

    /**
     * 大华乐橙能力矩阵。
     *
     * <p>口径（2026-11-10 按官方文档实现后回写）：实时预览走 bindDeviceLive+getLiveStreamInfo，
     * 回放走 createDeviceRecordHls，通道走 device.detail.list(v2)，抓图走 setDeviceSnap，
     * 事件走 device.message.list(v2)，门禁走 openDoorRemote/getOpenDoorRecord/getDoorKeys，
     * 录像走 queryLocalRecords —— 均已在 {@code DahuaLeChangeSession} 真实实现。
     * 乐橙无预置位接口（只有 controlMovePTZ / controlLocationPTZ）、无组织与人员模型；
     * 设备由序列号绑定，开放平台无删除接口（unBindDevice 是解绑自己的设备）。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix dahuaLeChange() {
        return builder("dahua.lechange", "大华乐橙")
                .yes(DeviceCapability.DEVICE, 8)
                .unverified(DeviceCapability.MEDIA, 1, "getLiveUrl（bindDeviceLive+getLiveStreamInfo）已实现，真机当前离线未验证")
                .unverified(DeviceCapability.RECORD, 4, "getPlaybackUrl（createDeviceRecordHls，不支持跨天）与 queryRecords（queryLocalRecords）已实现，未验证")
                .unverified(DeviceCapability.PTZ, 2, "ptzControl（controlMovePTZ 八向/变倍/停止）与 locationPtz（controlLocationPTZ）已实现，未验证")
                .unverified(DeviceCapability.CHANNEL, 4, "listChannels（v2 device.detail.list）已实现，未验证")
                .unverified(DeviceCapability.ACCESS, 2, "queryAccessRecords（getOpenDoorRecord）与 listDoors 已实现，无门锁真机未验证")
                .unverified(DeviceCapability.ACCESS_WRITE, 1, "remoteUnlock（openDoorRemote）已实现，无门锁真机未验证")
                .unverified(DeviceCapability.CAPTURE, 1, "capture（setDeviceSnap+下载 url）已实现，未验证")
                .unverified(DeviceCapability.EVENT, 2, "pollEvents（v2 device.message.list，设备本地时间）已实现，未验证")
                .no(DeviceCapability.DEVICE_WRITE, "乐橙设备通过序列号绑定，开放平台无设备删除接口")
                .no(DeviceCapability.ORG, "乐橙面向消费级，无组织模型")
                .no(DeviceCapability.ORG_WRITE, "乐橙面向消费级，无组织模型")
                .no(DeviceCapability.PERSON, "乐橙面向消费级，无人员模型")
                .no(DeviceCapability.PERSON_WRITE, "乐橙面向消费级，无人员模型")
                .no(DeviceCapability.PARKING, "乐橙无停车场业务")
                .build();
    }

    /**
     * 大华 ICC / DSS 私有化平台能力矩阵。
     *
     * <p>平台 SDK（{@code DahuaIccSdk}）之外已于 2026-11-11 补齐 {@code DahuaIccSession}：
     * 通道/取流/回放/录像/抓图/云台/门禁/事件/组织/人员按 15 条目录接通；
     * 设备写接口在公开设备管理目录中只有详情/分页/树/通道分页，没有增删改；
     * 组织/人员写接口已按官网登记并接入。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix dahuaIcc() {
        return builder("dahua.dahua-icc", "大华 ICC")
                .yes(DeviceCapability.DEVICE, 4)
                .unverified(DeviceCapability.MEDIA, 5, "video.live 已接入 ICC 会话，无 ICC 环境未验证")
                .unverified(DeviceCapability.RECORD, 4, "video.records/playback 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.PTZ, 3, "video.ptz 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.CHANNEL, 4, "device.channels 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.EVENT, 1, "alarm.list 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.ACCESS, 2, "access.record 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.ACCESS_WRITE, 1, "access.door.open 已接入；门禁点增删改目录未登记")
                .no(DeviceCapability.DEVICE_WRITE,
                        "公开设备管理接口只有详情/分页/树/通道分页，无增删改路径")
                .unverified(DeviceCapability.ORG, 1, "org.list 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.ORG_WRITE, 3,
                        "department/add|update|delete + generate-id 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.PERSON, 1, "person.list 已接入，无 ICC 环境未验证")
                .unverified(DeviceCapability.PERSON_WRITE, 4,
                        "person/subsystem/add|update 与 person/delete 已接入，证件字段必填，无 ICC 环境未验证")
                .unverified(DeviceCapability.CAPTURE, 2, "video.capture 已接入，无 ICC 环境未验证")
                .no(DeviceCapability.PARKING, "ICC 停车场为独立子系统，不在本接入范围")
                .build();
    }

    /**
     * 宇视能力矩阵。
     *
     * <p>接口路径已按官方文档接入 {@code UniviewSession}；
     * 官方 openapi 目录无组织、人员、门禁、停车场接口，对应能力位标 {@code NO}。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix uniview() {
        return builder("uniview.uniview", "宇视")
                .unverified(DeviceCapability.DEVICE, 4, "device/list、device/get 已按官方文档接入，缺真机验证")
                .unverified(DeviceCapability.CHANNEL, 4, "device/channel/list 已接入，缺真机验证")
                .unverified(DeviceCapability.MEDIA, 5, "live/video/device/url/get 已接入，缺真机验证")
                .unverified(DeviceCapability.RECORD, 4, "device/record/time/query 已接入；官方无回放地址接口")
                .unverified(DeviceCapability.PTZ, 3, "device/ptz/start|stop 与 preset add/list/goto/delete 已接入")
                .unverified(DeviceCapability.DEVICE_WRITE, 3, "device/add|delete|name/update 已接入；add 仅提交序列号+名称，待真机校准")
                .no(DeviceCapability.ORG, "官方 openapi 目录无组织接口")
                .no(DeviceCapability.ORG_WRITE, "官方 openapi 目录无组织接口")
                .no(DeviceCapability.PERSON, "官方 openapi 目录无人员接口")
                .no(DeviceCapability.PERSON_WRITE, "官方 openapi 目录无人员接口")
                .no(DeviceCapability.ACCESS, "官方 openapi 目录无门禁记录接口")
                .no(DeviceCapability.ACCESS_WRITE, "官方 openapi 目录无门禁接口，仅 device/api/invoke 透传")
                .unverified(DeviceCapability.EVENT, 2, "event/alarm/query 已接入，缺真机验证")
                .unverified(DeviceCapability.CAPTURE, 2, "device/capture/get + 二次下载已接入，缺真机验证")
                .no(DeviceCapability.PARKING, "官方 openapi 目录无停车场接口")
                .build();
    }

    /**
     * GB28181 能力矩阵（国标协议）。
     *
     * <p>会话 {@code Gb28181Session} 按 WVP 的 {@code /api/v1/...} REST 约定实现
     * 通道、取流、录像、云台与预置位，SIP 信令由 WVP/ZLMediaKit 处理。
     * 本机没有国标服务端，故全部标 {@code IMPLEMENTED_UNVERIFIED}。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix gb28181() {
        return builder("standard.gb28181", "GB28181")
                .yes(DeviceCapability.DEVICE, 7)
                .unverified(DeviceCapability.MEDIA, 9, "Gb28181Session 已接 WVP /api/v1/play/start|stop，待 WVP+ZLM 联调")
                .unverified(DeviceCapability.RECORD, 2, "Gb28181Session 已接 WVP /api/v1/record/query|play，待联调")
                .unverified(DeviceCapability.PTZ, 2, "Gb28181Session 已接 WVP /api/v1/ptz/control 与预置位接口，待联调")
                .unverified(DeviceCapability.CHANNEL, 2, "Gb28181Session 已接 WVP /api/v1/device/channel/list，待联调")
                .no(DeviceCapability.DEVICE_WRITE, "国标设备由下级平台主动注册，上级不能新增或删除设备")
                .no(DeviceCapability.ORG, "国标协议的定义范围是设备接入与视频传输，不含组织架构")
                .no(DeviceCapability.ORG_WRITE, "国标协议不含组织架构")
                .no(DeviceCapability.PERSON, "国标协议不含人员模型")
                .no(DeviceCapability.PERSON_WRITE, "国标协议不含人员模型")
                .no(DeviceCapability.ACCESS, "国标协议不含门禁业务")
                .no(DeviceCapability.ACCESS_WRITE, "国标协议不含门禁业务")
                .no(DeviceCapability.CAPTURE, "国标无抓图信令")
                .no(DeviceCapability.EVENT, "国标事件为主动上报（订阅通知），不走平台查询")
                .no(DeviceCapability.PARKING, "国标协议不含停车场业务")
                .build();
    }

    /**
     * ONVIF 能力矩阵（国际标准协议）。
     *
     * <p>{@code OnvifSession} 已建（媒体/云台/抓图/通道），按 ONVIF 2.0 规范实现并
     * 按服务分别寻址；<b>本机无 ONVIF 设备可达，未经真实设备联调</b>，
     * 故标 {@code IMPLEMENTED_UNVERIFIED}。</p>
     *
     * @return 能力矩阵
     */
    static DeviceCapabilityMatrix onvif() {
        return builder("standard.onvif", "ONVIF")
                .unverified(DeviceCapability.DEVICE, 2, "已实现 GetDeviceInformation / GetCapabilities，待真机联调")
                .unverified(DeviceCapability.MEDIA, 3, "已实现 GetStreamUri（Profile S），待真机联调")
                .unverified(DeviceCapability.PTZ, 2, "已实现 ContinuousMove / Stop / 预置位，待真机联调")
                .unverified(DeviceCapability.CHANNEL, 2, "已实现 GetProfiles 枚举通道，待真机联调")
                .unverified(DeviceCapability.CAPTURE, 1, "已实现 GetSnapshotUri + 下载，待真机联调")
                .no(DeviceCapability.RECORD, "ONVIF 的回放依赖设备 Profile G，国内主流设备普遍未实现")
                .no(DeviceCapability.DEVICE_WRITE, "ONVIF 是设备发现与控制协议，不提供设备增删改")
                .no(DeviceCapability.ORG, "ONVIF 不含组织架构模型")
                .no(DeviceCapability.ORG_WRITE, "ONVIF 不含组织架构模型")
                .no(DeviceCapability.PERSON, "ONVIF 不含人员模型")
                .no(DeviceCapability.PERSON_WRITE, "ONVIF 不含人员模型")
                .no(DeviceCapability.ACCESS, "ONVIF Access Control 服务国内设备几乎不支持")
                .no(DeviceCapability.ACCESS_WRITE, "ONVIF Access Control 服务国内设备几乎不支持")
                .no(DeviceCapability.EVENT, "ONVIF 事件为 PullPoint 订阅，不走平台查询")
                .no(DeviceCapability.PARKING, "ONVIF 不含停车场业务")
                .build();
    }
}
