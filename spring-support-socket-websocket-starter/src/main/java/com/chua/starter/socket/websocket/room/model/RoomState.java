package com.chua.starter.socket.websocket.room.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 房间状态（RoomState）。
 *
 * <p>管理一个房间的全部参与端点（最大 {@code 2} 个），以及房间进度：
 * <ul>
 *   <li>{@code epoch} - 房间代数：每一次新的" participant pair" 对局递增</li>
 *   <li>{@code offererSlotId} - 发起连接（第一个加入）的端点</li>
 * </ul>
 *
 * @author CH
 * @since 2026-11-03
 */
public class RoomState {

    /** 端点上限（P2P 双人房） */
    public static final int MAX_PEERS = 2;

    /** 状态结构版本（V2） */
    private int version = 2;

    /** 房间纪元号：每个新的对局对递增 */
    private long epoch = 1;

    /** 发起连接的 SlotID（第一个成功加入的端点） */
    private String offererSlotId;

    /** 房间参与端点列表（同一 Session 内保持加入顺序） */
    private final List<SlotState> slots = new ArrayList<>();

    /** 房间编号（四位数字） */
    private String roomId;

    /** 房间创建时间（ms） */
    private long createdAt;

    /** 房间最后活跃时间（ms） */
    private long lastActiveAt;

    /**
     * 创建房间状态
     */
    public RoomState() {
    }

    /**
     * 创建房间状态
     *
     * @param roomId 房间编号（四位数字）
     */
    public RoomState(String roomId) {
        this.roomId = roomId;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = this.createdAt;
    }

    /**
     * 获取状态结构版本
     *
     * @return 状态结构版本
     */
    public int getVersion() {
        return version;
    }

    /**
     * 设置状态结构版本
     *
     * @param version 状态结构版本
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * 获取房间纪元号
     *
     * @return 房间纪元号
     */
    public long getEpoch() {
        return epoch;
    }

    /**
     * 设置房间纪元号
     *
     * @param epoch 房间纪元号
     */
    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    /**
     * 递增房间纪元号（新一局开始时调用）
     */
    public void bumpEpoch() {
        this.epoch++;
    }

    /**
     * 获取发起连接端点的 SlotID
     *
     * @return 发起端点 SlotID
     */
    public String getOffererSlotId() {
        return offererSlotId;
    }

    /**
     * 设置发起连接端点的 SlotID
     *
     * @param offererSlotId 发起端点 SlotID
     */
    public void setOffererSlotId(String offererSlotId) {
        this.offererSlotId = offererSlotId;
    }

    /**
     * 获取房间参与端点列表（同一 Session 内保持加入顺序）
     *
     * @return 端点列表
     */
    public List<SlotState> getSlots() {
        return slots;
    }

    /**
     * 获取房间编号
     *
     * @return 房间编号（四位数字）
     */
    public String getRoomId() {
        return roomId;
    }

    /**
     * 设置房间编号
     *
     * @param roomId 房间编号（四位数字）
     */
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    /**
     * 获取房间创建时间（ms）
     *
     * @return 创建时间（ms）
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置房间创建时间（ms）
     *
     * @param createdAt 创建时间（ms）
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取房间最后活跃时间（ms）
     *
     * @return 最后活跃时间（ms）
     */
    public long getLastActiveAt() {
        return lastActiveAt;
    }

    /**
     * 设置房间最后活跃时间（ms）
     *
     * @param lastActiveAt 最后活跃时间（ms）
     */
    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    /**
     * 端点上的运行情况
     *
     * @param slot 端点
     * @return true 表示端点活跃
     */
    private boolean isActive(SlotState slot) {
        return slot != null && !slot.isDisconnected() && !slot.isLeaseExpired(System.currentTimeMillis());
    }

    /**
     * 活跃端点数量
     *
     * @return 活跃端点数量
     */
    public int activePeerCount() {
        return (int) slots.stream().filter(this::isActive).count();
    }

    /**
     * 获取第一个活跃的端点（发起连接方，offerer）
     *
     * @return 活跃端点（可能为空）
     */
    public Optional<SlotState> getActiveOfferer() {
        return slots.stream().filter(this::isActive).findFirst();
    }

    /**
     * 获取第二个活跃的端点（发起加入方，initiator）
     *
     * @return 活跃端点（可能为空）
     */
    public Optional<SlotState> getActiveInitiator() {
        return slots.stream().filter(this::isActive).skip(1).findFirst();
    }

    /**
     * 添加端点，房间已满时（超过 MAX_PEERS）拒绝
     *
     * @param slot 待添加的端点
     * @return true 表示添加成功
     */
    public synchronized boolean addSlot(SlotState slot) {
        if (slots.size() >= MAX_PEERS) {
            return false;
        }
        slots.add(slot);
        slot.setConnectedAt(System.currentTimeMillis());
        return true;
    }

    /**
     * 标记端点为断开（非活跃的修复）
     *
     * @param slotId 端点 SlotID
     */
    public synchronized void disconnectSlot(String slotId) {
        slots.stream().filter(s -> s.getSlotId().equals(slotId)).findFirst().ifPresent(s -> s.setDisconnectedAt(System.currentTimeMillis()));
    }

    /**
     * 清空上一局的端点记录（新一局开始时调用）。
     *
     * <p>端点断开后 slot 仍留在 {@code slots} 里（续接要用），但房间开新一局时
     * 必须清掉，否则 {@code slots} 会一直累积到 {@link #MAX_PEERS}，新端点永远加不进来。</p>
     */
    public synchronized void resetSlots() {
        slots.clear();
        offererSlotId = null;
    }

    /**
     * 按 ID 查找端点
     *
     * @param slotId 端点 SlotID
     * @return 命中的端点（可能为空）
     */
    public Optional<SlotState> findSlot(String slotId) {
        return slots.stream().filter(s -> s.getSlotId().equals(slotId)).findFirst();
    }

    /**
     * 房间满载（2 个活跃端点就位）
     *
     * @return true 表示满载
     */
    public boolean isFull() {
        return slots.size() >= MAX_PEERS;
    }

    /**
     * 房间内还有活跃端点
     *
     * @return true 表示还有活跃端点
     */
    public boolean hasActiveSlot() {
        return activePeerCount() > 0;
    }

    /**
     * 获取端点旁的对端
     *
     * @param slotId 端点 SlotID
     * @return 对端的 SlotID（null = 无对端）
     */
    public String getOtherSlotId(String slotId) {
        for (SlotState slot : slots) {
            if (isActive(slot) && !slot.getSlotId().equals(slotId)) {
                return slot.getSlotId();
            }
        }
        return null;
    }
}
