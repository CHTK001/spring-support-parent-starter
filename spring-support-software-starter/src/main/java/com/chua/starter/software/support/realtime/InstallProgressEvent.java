package com.chua.starter.software.support.realtime;

import com.chua.socket.support.MsgStep;
import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.enums.InstallStatus;

/**
 * 软件安装进度事件
 *
 * <p>这是安装进度在<b>业务层</b>的载荷，只描述「哪条安装记录、什么状态、百分之几」，
 * 不含任何 Socket / SSE / WebSocket 类型，保证 software 模块在没有实时通道时也能独立编译运行。</p>
 *
 * <p>由 {@code SoftInstallRecordServiceImpl} 在每次落库后构造，交给
 * {@link InstallProgressPublisher} 的具体实现转发到前端。</p>
 *
 * <p><b>为什么进度要单独抽一层</b>：安装记录的状态机（0 未安装 / 1 安装中 / 2 已安装 /
 * 3 失败 / 4 卸载中 / 5 已卸载 / 6 更新中 / 7 更新失败）在库里是数字，前端要显示的是
 * 中文与百分比。把「数字 → 可展示文案」的转换放在一处，避免每个推送实现各写一套映射。</p>
 *
 * <p>推给前端时，通用进度字段（步骤 / 总数 / 百分比 / 状态 / 时间戳）一律由
 * {@link #toMsgStep()} 产出为统一步进抽象 {@link MsgStep}，安装自身的上下文
 * （软件、发起人、设备、操作、是否终态）再作为附加字段并列，不另造协议。</p>
 *
 * @param recordId   安装记录标识（前端按它定位一条进度）
 * @param softwareId 软件标识
 * @param userId     发起用户标识
 * @param deviceId   设备标识，可为 {@code null}
 * @param operation  操作类型：install / uninstall / update
 * @param status     状态码，即 {@link InstallStatus#getValue()}
 * @param statusText 状态中文名，未知状态为空串
 * @param progress   进度百分比，已收敛到 0~100
 * @param message    进度说明或错误信息，不为 {@code null}
 * @param timestamp  事件产生时间戳（毫秒）
 * @author CH
 * @since 4.0.0.51
 */
public record InstallProgressEvent(
        String recordId,
        String softwareId,
        String userId,
        String deviceId,
        String operation,
        int status,
        String statusText,
        int progress,
        String message,
        long timestamp) {

    /**
     * 进度上限
     */
    private static final int PROGRESS_MAX = 100;

    /**
     * 未知状态码
     */
    private static final int STATUS_UNKNOWN = -1;

    /**
     * 从安装记录构造事件
     *
     * @param record  安装记录，不允许为 {@code null}
     * @param message 进度说明或错误信息，可为 {@code null}
     * @return 进度事件
     * @throws IllegalArgumentException 记录为 {@code null} 时抛出，属于调用方编程错误
     */
    public static InstallProgressEvent of(SoftInstallRecord record, String message) {
        if (record == null) {
            throw new IllegalArgumentException("安装记录不能为空");
        }
        int status = record.getInstallRecordStatus() == null
                ? STATUS_UNKNOWN
                : record.getInstallRecordStatus();
        return new InstallProgressEvent(
                record.getInstallRecordId(),
                record.getInstallRecordSoftwareId(),
                record.getInstallRecordUserId(),
                record.getInstallRecordDeviceId(),
                record.getInstallRecordOperation(),
                status,
                statusText(status),
                normalizeProgress(record.getInstallRecordProgress()),
                message == null ? "" : message,
                System.currentTimeMillis());
    }

    /**
     * 是否已进入终态
     *
     * <p>前端据此决定收起进度浮层：安装成功、安装失败、卸载完成、更新失败都不会再有后续帧。</p>
     *
     * @return true 表示不会再有新的进度帧
     */
    public boolean finished() {
        return status == InstallStatus.INSTALLED.getValue()
                || status == InstallStatus.FAILED.getValue()
                || status == InstallStatus.UNINSTALLED.getValue()
                || status == InstallStatus.UPDATE_FAILED.getValue();
    }

    /**
     * 状态码转中文名
     *
     * @param status 状态码
     * @return 中文名，未知状态返回空串
     */
    public static String statusText(int status) {
        InstallStatus value = InstallStatus.fromValue(status);
        return value == null ? "" : value.getName();
    }

    /**
     * 进度收敛到 0~100
     *
     * <p>库里是整型列，历史数据或并发更新可能写进越界值；越界会让前端进度条溢出或倒退，
     * 因此统一在出口收敛，而不是指望每个写入点都自觉。</p>
     *
     * @param progress 原始进度，可为 {@code null}
     * @return 0~100 之间的进度
     */
    private static int normalizeProgress(Integer progress) {
        if (progress == null || progress < 0) {
            return 0;
        }
        return Math.min(progress, PROGRESS_MAX);
    }

    /**
     * 转换为统一步进消息
     *
     * <p>复用全局抽象 {@link MsgStep} 的字段语义：{@code 步骤} 是百分比本身（安装没有
     * 「第几项 / 共几项」的分段概念），{@code 总数} 固定 100，保证前端进度条只需读
     * {@code percentage}；{@code dataId} 落安装记录标识，前端据此把同一任务的帧归到一起，
     * 不会把两台机器同时安装的进度串成一条。</p>
     *
     * @return 步进消息
     */
    public MsgStep toMsgStep() {
        return MsgStep.builder()
                .eventId(SocketInstallProgressPublisher.EVENT)
                .dataId(recordId)
                .message(message)
                .step(progress)
                .total(PROGRESS_MAX)
                .percentage(progress)
                .status(statusText)
                .timestamp(timestamp)
                .build();
    }
}
