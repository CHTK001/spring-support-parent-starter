package com.chua.starter.device.provider.sdk.dahua;

import com.chua.starter.device.provider.sdk.PlatformApiSpec;

import java.util.List;

/**
 * 大华乐橙（Imou / LeChange）官方接口目录 —— 逐条核对 open.imou.com 文档页后登记。
 *
 * <p>乐橙统一网关：v1 为 {@code POST https://openapi.lechange.cn/openapi/{method}}，
 * v2 为 {@code POST https://openapi.lechange.cn/openapi/v2/{点号方法}}；
 * 请求体固定 {@code {system, id, params}} 三层，签名为
 * {@code md5("time:{t},nonce:{n},appSecret:{s}")}。</p>
 *
 * <p>目录里 {@code path} 记录 {@code /openapi/...} 完整路径；业务调用取最后一段作为方法名。
 * 本目录只登记「文档页确认存在、且请求/响应字段齐全」的接口；
 * 曾经推测的 {@code deviceInfo}、{@code deviceAbility}、{@code accessRecordList}、
 * {@code openDoor}、{@code personList}、{@code alarmMessageList}、
 * {@code subscribeAlarmMessage}、{@code listDeviceChannelByPage}、{@code deviceSnap}
 * 经实测返回 585 字节占位页（文档不存在），已全部移除。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class LeChangeApis {

    /**
     * 工具类不可实例化。
     */
    private LeChangeApis() {
    }

    /** 全部接口 */
    private static final List<PlatformApiSpec> ALL = List.of(
            // 认证（v1）
            PlatformApiSpec.global("auth.token", "获取管理员 accessToken", "POST",
                    "/openapi/accessToken", "v1", "other"),
            PlatformApiSpec.global("auth.subAccount.token", "获取子账号 Token", "POST",
                    "/openapi/subAccountToken", "v1", "other"),
            PlatformApiSpec.global("auth.subAccount.create", "创建子账号", "POST",
                    "/openapi/createSubAccount", "v1", "other"),
            PlatformApiSpec.global("auth.subAccount.page", "获取子账号列表", "POST",
                    "/openapi/listSubAccount", "v1", "other"),
            PlatformApiSpec.global("auth.subAccount.delete", "删除子账号", "POST",
                    "/openapi/deleteSubAccount", "v1", "other"),
            PlatformApiSpec.global("auth.subAccount.openId", "根据子账号获取 openId", "POST",
                    "/openapi/getOpenIdByAccount", "v1", "other"),
            PlatformApiSpec.global("auth.policy.add", "添加子账号权限", "POST",
                    "/openapi/addPolicy", "v1", "other"),
            PlatformApiSpec.global("auth.policy.clear", "清除子账号授权策略", "POST",
                    "/openapi/clearPolicy", "v1", "other"),
            PlatformApiSpec.global("auth.policy.devicePage", "子账号设备维度权限", "POST",
                    "/openapi/queryDevicePermission", "v1", "other"),
            PlatformApiSpec.global("auth.policy.deviceDelete", "删除子账号设备权限", "POST",
                    "/openapi/deleteDevicePermission", "v1", "other"),

            // 设备管理（v2 点号方法 + v1 兼容）
            PlatformApiSpec.global("device.page", "分页查询设备详情", "POST",
                    "/openapi/listDeviceDetailsByPage", "v1", "device"),
            PlatformApiSpec.global("device.detail.list", "按 ID 批量详情（1~50 项）", "POST",
                    "/openapi/v2/device.detail.list", "v2", "device"),
            PlatformApiSpec.global("device.detail.page", "分页详情（含通道列表）", "POST",
                    "/openapi/v2/device.detail.page", "v2", "device"),
            PlatformApiSpec.global("device.capability.list", "查询可用能力", "POST",
                    "/openapi/v2/device.capability.list", "v2", "device"),
            PlatformApiSpec.global("device.status.get", "查询设备在线状态", "POST",
                    "/openapi/v2/device.status.get", "v2", "device"),
            PlatformApiSpec.global("device.online", "查询设备在线状态（v1 兼容）", "POST",
                    "/openapi/deviceOnline", "v1", "device"),
            PlatformApiSpec.global("device.name.set", "修改设备名称", "POST",
                    "/openapi/v2/device.name.set", "v2", "device"),
            PlatformApiSpec.global("device.updateName", "修改设备名称（v1 兼容）", "POST",
                    "/openapi/modifyDeviceName", "v1", "device"),
            PlatformApiSpec.global("device.bind", "绑定设备", "POST",
                    "/openapi/bindDevice", "v1", "device"),
            PlatformApiSpec.global("device.unbind", "解绑设备", "POST",
                    "/openapi/unBindDevice", "v1", "device"),
            PlatformApiSpec.global("device.bindstate.get", "查询绑定状态", "POST",
                    "/openapi/v2/device.bindstate.get", "v2", "device"),
            PlatformApiSpec.global("device.network.get", "获取局域网信息", "POST",
                    "/openapi/v2/device.network.get", "v2", "device"),
            PlatformApiSpec.global("device.reboot.execute", "重启设备", "POST",
                    "/openapi/v2/device.reboot.execute", "v2", "device"),
            PlatformApiSpec.global("device.restore.execute", "恢复出厂", "POST",
                    "/openapi/v2/device.restore.execute", "v2", "device"),
            PlatformApiSpec.global("device.upgrade.execute", "升级设备", "POST",
                    "/openapi/v2/device.upgrade.execute", "v2", "device"),
            PlatformApiSpec.global("device.upgrade.process", "查询升级进度", "POST",
                    "/openapi/v2/device.upgrade.process", "v2", "device"),
            PlatformApiSpec.global("device.time.get", "获取设备时间", "POST",
                    "/openapi/v2/device.time.get", "v2", "device"),
            PlatformApiSpec.global("device.time.sync", "校时", "POST",
                    "/openapi/v2/device.time.sync", "v2", "device"),
            PlatformApiSpec.global("device.property.get", "读取属性", "POST",
                    "/openapi/v2/device.property.get", "v2", "device"),
            PlatformApiSpec.global("device.property.set", "设置属性", "POST",
                    "/openapi/v2/device.property.set", "v2", "device"),
            PlatformApiSpec.global("device.service.invoke", "调用服务（如 ptz.aim.relative）", "POST",
                    "/openapi/v2/device.service.invoke", "v2", "device"),
            PlatformApiSpec.global("device.cover.refresh", "刷新封面", "POST",
                    "/openapi/v2/device.cover.refresh", "v2", "device"),
            PlatformApiSpec.global("device.wifi.scan", "扫描 WiFi", "POST",
                    "/openapi/v2/device.wifi.scan", "v2", "device"),
            PlatformApiSpec.global("device.wifi.current", "查询当前 WiFi", "POST",
                    "/openapi/v2/device.wifi.current", "v2", "device"),
            PlatformApiSpec.global("device.wifi.control", "配网 / 删除 WiFi", "POST",
                    "/openapi/v2/device.wifi.control", "v2", "device"),
            PlatformApiSpec.global("device.password.set", "设置设备密码", "POST",
                    "/openapi/v2/device.password.set", "v2", "device"),
            PlatformApiSpec.global("device.password.verify", "校验设备密码", "POST",
                    "/openapi/v2/device.password.verify", "v2", "device"),
            PlatformApiSpec.global("device.wakeUp", "唤醒休眠的门锁设备", "POST",
                    "/openapi/wakeUpDevice", "v1", "device"),

            // 视频
            PlatformApiSpec.global("video.live.bind", "创建设备源直播地址", "POST",
                    "/openapi/bindDeviceLive", "v1", "video"),
            PlatformApiSpec.global("video.live.list", "获取直播列表", "POST",
                    "/openapi/liveList", "v1", "video"),
            PlatformApiSpec.global("video.live.info", "查询设备通道直播信息", "POST",
                    "/openapi/getLiveStreamInfo", "v1", "video"),
            PlatformApiSpec.global("video.live.status", "获取直播状态", "POST",
                    "/openapi/queryLiveStatus", "v1", "video"),
            PlatformApiSpec.global("video.live.unbind", "删除直播地址", "POST",
                    "/openapi/unbindLive", "v1", "video"),
            PlatformApiSpec.global("video.live.plan.set", "修改直播计划时间", "POST",
                    "/openapi/modifyLivePlan", "v1", "video"),
            PlatformApiSpec.global("video.live.plan.batch", "批量修改直播计划时间", "POST",
                    "/openapi/batchModifyLivePlan", "v1", "video"),
            PlatformApiSpec.global("video.live.plan.status", "设置直播计划开关", "POST",
                    "/openapi/modifyLivePlanStatus", "v1", "video"),
            PlatformApiSpec.global("video.live.flv.create", "创建设备 flv 直播", "POST",
                    "/openapi/createDeviceFlvLive", "v1", "video"),
            PlatformApiSpec.global("video.live.flv.query", "查询设备通道 flv 直播", "POST",
                    "/openapi/queryDeviceFlvLive", "v1", "video"),
            PlatformApiSpec.global("video.live.flv.delete", "删除设备 flv 直播", "POST",
                    "/openapi/deleteDeviceFlvLive", "v1", "video"),
            PlatformApiSpec.global("video.live.rtmp.create", "创建 rtmp 直播地址", "POST",
                    "/openapi/createDeviceRtmpLive", "v1", "video"),
            PlatformApiSpec.global("video.live.rtmp.query", "查询 rtmp 直播地址", "POST",
                    "/openapi/queryDeviceRtmpLive", "v1", "video"),
            PlatformApiSpec.global("video.live.rtmp.delete", "删除 rtmp 直播地址", "POST",
                    "/openapi/deleteDeviceRtmpLive", "v1", "video"),
            PlatformApiSpec.global("video.capture", "设备抓图", "POST",
                    "/openapi/setDeviceSnap", "v1", "video"),
            PlatformApiSpec.global("video.capture.enhanced", "设备抓图（升级版）", "POST",
                    "/openapi/setDeviceSnapEnhanced", "v1", "video"),
            PlatformApiSpec.global("video.heatMap.data", "热度图数据获取", "POST",
                    "/openapi/getHeatMapData", "v1", "video"),
            PlatformApiSpec.global("video.heatMap.enable.get", "获取热度分析使能开关", "POST",
                    "/openapi/getHeatMapEnable", "v1", "video"),
            PlatformApiSpec.global("video.heatMap.enable.set", "设置热度分析使能开关", "POST",
                    "/openapi/setHeatMapEnable", "v1", "video"),

            // 录像
            PlatformApiSpec.global("record.local", "查询设备本地录像片段", "POST",
                    "/openapi/queryLocalRecords", "v1", "record"),
            PlatformApiSpec.global("record.cloud.forward", "正序查询设备云录像片段", "POST",
                    "/openapi/queryCloudRecords", "v1", "record"),
            PlatformApiSpec.global("record.cloud.backward", "倒序查询设备云录像片段", "POST",
                    "/openapi/getCloudRecords", "v1", "record"),
            PlatformApiSpec.global("record.cloud.num", "查询设备云录像总数", "POST",
                    "/openapi/queryCloudRecordNum", "v1", "record"),
            PlatformApiSpec.global("record.cloud.bitmap", "按月查询云录像掩码", "POST",
                    "/openapi/queryCloudRecordBitmap", "v1", "record"),
            PlatformApiSpec.global("record.cloud.byToken", "根据 cloudToken 查询云录像片段", "POST",
                    "/openapi/queryCloudRecordByToken", "v1", "record"),
            PlatformApiSpec.global("record.cloud.delete", "删除设备云录像片段", "POST",
                    "/openapi/deleteCloudRecords", "v1", "record"),
            PlatformApiSpec.global("record.cloud.plan.get", "获取连续云存储录像计划", "POST",
                    "/openapi/queryCloudRecordPlan", "v1", "record"),
            PlatformApiSpec.global("record.cloud.plan.set", "设置连续云存储录像计划", "POST",
                    "/openapi/setCloudRecordPlan", "v1", "record"),
            PlatformApiSpec.global("record.cloud.open", "开通设备云存储", "POST",
                    "/openapi/openCloudRecord", "v1", "record"),
            PlatformApiSpec.global("record.cloud.close", "解绑设备云存储", "POST",
                    "/openapi/unBindDeviceCloud", "v1", "record"),
            PlatformApiSpec.global("record.cloud.strategy.set", "设置设备云存储服务开关", "POST",
                    "/openapi/setStorageStrategy", "v1", "record"),
            PlatformApiSpec.global("record.playback.hls", "根据设备录像片段生成 hls 回放地址", "POST",
                    "/openapi/createDeviceRecordHls", "v1", "record"),
            PlatformApiSpec.global("record.download.url", "获取临时非加密流地址", "POST",
                    "/openapi/createVideoDownloadStreamUrl", "v1", "record"),
            PlatformApiSpec.global("record.condensed.page", "查询智见录像片段", "POST",
                    "/openapi/getCondensedRecords", "v1", "record"),
            PlatformApiSpec.global("record.condensed.bitmap", "查询智见录像掩码", "POST",
                    "/openapi/queryCondensedRecordBitmap", "v1", "record"),

            // 云台
            PlatformApiSpec.global("ptz.move", "云台移动控制（八向 + 变倍 + 停止）", "POST",
                    "/openapi/controlMovePTZ", "v1", "ptz"),
            PlatformApiSpec.global("ptz.location", "云台定位（h/v/z 速度分量）", "POST",
                    "/openapi/controlLocationPTZ", "v1", "ptz"),

            // 门禁与门锁
            PlatformApiSpec.global("access.door.open", "远程开门", "POST",
                    "/openapi/openDoorRemote", "v1", "access"),
            PlatformApiSpec.global("access.record.list", "获取开门记录", "POST",
                    "/openapi/getOpenDoorRecord", "v1", "access"),
            PlatformApiSpec.global("access.key.page", "获取门锁密钥列表", "POST",
                    "/openapi/getDoorKeys", "v1", "access"),
            PlatformApiSpec.global("access.key.delete", "删除门锁密钥", "POST",
                    "/openapi/deleteDoorKey", "v1", "access"),
            PlatformApiSpec.global("access.snap.key.generate", "生成抓图鉴权 key", "POST",
                    "/openapi/generateSnapkey", "v1", "access"),
            PlatformApiSpec.global("access.snap.key.page", "获取抓图鉴权 key 列表", "POST",
                    "/openapi/getSnapkeyList", "v1", "access"),

            // 告警 / 事件（v2 点号方法 + v1 回调）
            PlatformApiSpec.global("event.message.list", "查询告警历史", "POST",
                    "/openapi/v2/device.message.list", "v2", "event"),
            PlatformApiSpec.global("event.message.search", "按类型检索告警", "POST",
                    "/openapi/v2/device.message.search", "v2", "event"),
            PlatformApiSpec.global("event.message.detail", "告警详情", "POST",
                    "/openapi/v2/device.message.detail", "v2", "event"),
            PlatformApiSpec.global("event.message.delete", "删除告警", "POST",
                    "/openapi/v2/device.message.delete", "v2", "event"),
            PlatformApiSpec.global("event.callback.get", "查看报警回调地址", "POST",
                    "/openapi/getMessageCallback", "v1", "event"),
            PlatformApiSpec.global("event.callback.set", "设置报警回调地址", "POST",
                    "/openapi/setMessageCallback", "v1", "event"),
            PlatformApiSpec.global("event.alarm.page", "查询用户报警信息", "POST",
                    "/openapi/getAlarmMessage", "v1", "event"),
            PlatformApiSpec.global("event.alarm.detail", "根据 id 查询详细报警消息", "POST",
                    "/openapi/getAlarmMessageById", "v1", "event"),
            PlatformApiSpec.global("event.alarm.statistics", "获取报警统计报表", "POST",
                    "/openapi/getAlarmMessageStatistics", "v1", "event"),
            PlatformApiSpec.global("event.alarm.search", "根据关键词智能搜索报警消息", "POST",
                    "/openapi/searchAiAlarmMessageByPage", "v1", "event"),
            PlatformApiSpec.global("event.alarm.delete", "删除报警消息", "POST",
                    "/openapi/deleteAlarmMessage", "v1", "event"),
            PlatformApiSpec.global("event.feedback.ai", "反馈 AI 智慧消息准确性", "POST",
                    "/openapi/reportAiMsgFeedback", "v1", "event"),

            // 人员（AI 检测库）
            PlatformApiSpec.global("person.detect.repository.create", "创建检测目标库", "POST",
                    "/openapi/createAiDetectRepository", "v1", "person"),
            PlatformApiSpec.global("person.detect.repository.page", "分页查询检测目标库", "POST",
                    "/openapi/listAiDetectRepositoryByPage", "v1", "person"),
            PlatformApiSpec.global("person.detect.repository.delete", "删除检测目标库", "POST",
                    "/openapi/deleteAiDetectRepository", "v1", "person"),
            PlatformApiSpec.global("person.detect.target.add", "注册单个目标信息到检测库", "POST",
                    "/openapi/addAiDetectTarget", "v1", "person"),
            PlatformApiSpec.global("person.detect.target.list", "查询检测库中注册的目标信息列表", "POST",
                    "/openapi/listAiDetectTarget", "v1", "person"),
            PlatformApiSpec.global("person.detect.target.delete", "删除检测库中注册的目标信息", "POST",
                    "/openapi/deleteAiDetectTarget", "v1", "person"),
            PlatformApiSpec.global("person.face.analysis", "人脸分析", "POST",
                    "/openapi/faceAnalysis", "v1", "person"),
            PlatformApiSpec.global("person.human.detect", "人形分析", "POST",
                    "/openapi/humanDetect", "v1", "person"),

            // 问卷化 AI 检测（行为类）
            PlatformApiSpec.global("detect.absence", "离岗检测", "POST",
                    "/openapi/absenceDetect", "v1", "ai"),
            PlatformApiSpec.global("detect.phoneUsing", "玩手机检测", "POST",
                    "/openapi/phoneUsingDetect", "v1", "ai"),
            PlatformApiSpec.global("detect.smoking", "抽烟检测", "POST",
                    "/openapi/smokingDetect", "v1", "ai"),
            PlatformApiSpec.global("detect.trashOverflow", "垃圾满溢检测", "POST",
                    "/openapi/trashOverflowDetect", "v1", "ai"),
            PlatformApiSpec.global("detect.shelfStatus", "货架状态检测", "POST",
                    "/openapi/shelfStatusDetect", "v1", "ai"),
            PlatformApiSpec.global("detect.heatmap", "热力数据分析", "POST",
                    "/openapi/heatmapDetect", "v1", "ai"),
            PlatformApiSpec.global("detect.workwear", "工装检测", "POST",
                    "/openapi/workwearDetect", "v1", "ai"),

            // 门铃
            PlatformApiSpec.global("doorbell.answer", "接听门铃呼叫", "POST",
                    "/openapi/doorbellCallAnswer", "v1", "device"),
            PlatformApiSpec.global("doorbell.refuse", "拒接门铃呼叫", "POST",
                    "/openapi/doorbellCallRefuse", "v1", "device"),
            PlatformApiSpec.global("doorbell.hangUp", "挂断门铃呼叫", "POST",
                    "/openapi/doorbellCallHangUp", "v1", "device"),

            // 设备电力
            PlatformApiSpec.global("device.power.info", "获取设备电力信息", "POST",
                    "/openapi/getDevicePowerInfo", "v1", "device"),

            // 托管设备（SaaS 平台）
            PlatformApiSpec.global("device.auth.page", "分页获取已托管的设备详情", "POST",
                    "/openapi/authorizedDeviceList", "v1", "device"),
            PlatformApiSpec.global("device.auth.statusPage", "分页获取指定状态的托管设备列表", "POST",
                    "/openapi/deviceListByAuthStatus", "v1", "device"),
            PlatformApiSpec.global("device.auth.statusSet", "批量设置设备的托管状态", "POST",
                    "/openapi/setDeviceAuthStatus", "v1", "device"),

            // 项目与云转存
            PlatformApiSpec.global("project.create", "创建项目", "POST",
                    "/openapi/createProject", "v1", "project"),
            PlatformApiSpec.global("project.page", "分页获取项目", "POST",
                    "/openapi/queryProjectsByPage", "v1", "project"),
            PlatformApiSpec.global("project.detail", "项目详情", "POST",
                    "/openapi/queryProjectDetailByProjectId", "v1", "project"),
            PlatformApiSpec.global("project.delete", "删除项目", "POST",
                    "/openapi/deleteProjectByProjectId", "v1", "project"),
            PlatformApiSpec.global("project.expire.set", "修改项目保存时间", "POST",
                    "/openapi/updateProjectExpireDay", "v1", "project"),
            PlatformApiSpec.global("project.task.create", "创建云转存任务", "POST",
                    "/openapi/createProjectTask", "v1", "project"),
            PlatformApiSpec.global("project.task.page", "分页获取任务", "POST",
                    "/openapi/queryProjectTasksByPage", "v1", "project"),
            PlatformApiSpec.global("project.task.detail", "任务详情", "POST",
                    "/openapi/queryProjectTaskDetailByTaskId", "v1", "project"),
            PlatformApiSpec.global("project.task.stop", "终止云转存任务", "POST",
                    "/openapi/stopProjectTask", "v1", "project"),
            PlatformApiSpec.global("project.task.file.page", "分页获取文件", "POST",
                    "/openapi/queryProjectTaskFilesByTaskId", "v1", "project"),
            PlatformApiSpec.global("project.task.file.detail", "文件详情", "POST",
                    "/openapi/queryProjectTaskFileDetailByFileId", "v1", "project"),
            PlatformApiSpec.global("project.task.file.delete", "删除单个文件", "POST",
                    "/openapi/deleteProjectTaskFileByFileId", "v1", "project"),

            // AI 智见
            PlatformApiSpec.global("aiInsight.bind", "开通 AI 智见套餐", "POST",
                    "/openapi/bindDeviceAiInsight", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.unbind", "解绑设备 AI 智见套餐", "POST",
                    "/openapi/unbindDeviceAiInsight", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.activate", "立即启用指定的 AI 智见套餐", "POST",
                    "/openapi/activateDeviceAiInsight", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.query", "获取设备生效中的 AI 智见套餐", "POST",
                    "/openapi/queryDeviceAiInsight", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.queryList", "查询设备通道上 AI 智见套餐列表", "POST",
                    "/openapi/queryDeviceAiInsightList", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.switch.set", "设置 AI 智见套餐服务开关", "POST",
                    "/openapi/setDeviceAiInsightSwitch", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.quota", "查询账号购买套餐的剩余额度", "POST",
                    "/openapi/queryAiInsightCount", "v1", "ai"),
            PlatformApiSpec.global("aiInsight.unusedList", "查询账号下库存的 AI 智见服务列表", "POST",
                    "/openapi/queryUnusedAiInsightList", "v1", "ai"),

            // 资源池
            PlatformApiSpec.global("pool.create", "创建资源池", "POST",
                    "/openapi/createResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.page", "分页查询资源池", "POST",
                    "/openapi/listResourcePoolByPage", "v1", "resource"),
            PlatformApiSpec.global("pool.detail", "查询资源池详情", "POST",
                    "/openapi/getResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.modify", "修改资源池配置", "POST",
                    "/openapi/modifyResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.remove", "删除资源池", "POST",
                    "/openapi/removeResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.device.bind", "绑定设备通道到资源池", "POST",
                    "/openapi/bindDeviceToResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.device.unbind", "从资源池移除设备通道", "POST",
                    "/openapi/unbindDeviceFromResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.device.page", "分页查询池中设备通道", "POST",
                    "/openapi/listDeviceResourcePoolConfigByPage", "v1", "resource"),
            PlatformApiSpec.global("pool.device.config", "查询设备通道关联的资源池", "POST",
                    "/openapi/getDeviceResourcePoolConfig", "v1", "resource"),
            PlatformApiSpec.global("pool.subAccount.bind", "绑定子账号到资源池", "POST",
                    "/openapi/bindSubAccountToResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.subAccount.unbind", "从资源池移除子账号", "POST",
                    "/openapi/unbindSubAccountFromResourcePool", "v1", "resource"),
            PlatformApiSpec.global("pool.subAccount.page", "分页查询池中子账号", "POST",
                    "/openapi/listSubAccountResourcePoolConfigByPage", "v1", "resource"),
            PlatformApiSpec.global("pool.subAccount.config", "查询子账号关联的资源池", "POST",
                    "/openapi/getSubAccountResourcePoolConfig", "v1", "resource"),

            // 云存储服务
            PlatformApiSpec.global("cloud.list", "查询设备通道下所有云存储服务", "POST",
                    "/openapi/deviceCloudList", "v1", "cloud"),
            PlatformApiSpec.global("cloud.detail", "获取当前设备的云存储服务信息", "POST",
                    "/openapi/getDeviceCloud", "v1", "cloud"),
            PlatformApiSpec.global("cloud.unusedList", "获取未启用的云存储服务列表", "POST",
                    "/openapi/unUsedCloudList", "v1", "cloud"),
            PlatformApiSpec.global("cloud.record.callNum", "查询云存储套餐的剩余可用额度", "POST",
                    "/openapi/queryCloudRecordCallNum", "v1", "cloud"),

            // 预案
            PlatformApiSpec.global("preparation.get", "获取预案", "POST",
                    "/openapi/getPreparation", "v1", "other"),
            PlatformApiSpec.global("preparation.set", "预案设置", "POST",
                    "/openapi/setPreparation", "v1", "other"),

            // 子账号设备
            PlatformApiSpec.global("subAccount.device.list", "查询子账号有权限的设备列表", "POST",
                    "/openapi/listSubAccountDevice", "v1", "device"),
            PlatformApiSpec.global("subAccount.device.info", "获取子账号下指定的授权设备详细信息", "POST",
                    "/openapi/subAccountDeviceInfo", "v1", "device"),
            PlatformApiSpec.global("subAccount.device.page", "分页获取子账号下授权设备的详细信息", "POST",
                    "/openapi/subAccountDeviceList", "v1", "device")
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
