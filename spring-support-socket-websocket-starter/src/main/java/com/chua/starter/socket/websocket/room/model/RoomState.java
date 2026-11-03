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

    public RoomState() {
    }

    public RoomState(String roomId) {
        this.roomId = roomId;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = this.createdAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public void bumpEpoch() {
        this.epoch++;
    }

    public String getOffererSlotId() {
        return offererSlotId;
    }

    public void setOffererSlotId(String offererSlotId) {
        this.offererSlotId = offererSlotId;
    }

    public List<SlotState> getSlots() {
        return slots;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    /**
     * 端点上的运行情况
     */
    private boolean isActive(SlotState slot) {
        return slot != null && !slot.isDisconnected() && !slot.isLeaseExpired(System.currentTimeMillis());
    }

    /**
     * 活跃端点数量
     */
    public int activePeerCount() {
        return (int) slots.stream().filter(this::isActive).count();
    }

    /**
     * 获取第一个活跃的端点（发起连接方，offerer）
     */
    public Optional<SlotState> getActiveOfferer() {
        return slots.stream().filter(this::isActive).findFirst();
    }

    /**
     * 获取第二个活跃的端点（发起加入方，initiator）
     */
    public Optional<SlotState> getActiveInitiator() {
        return slots.stream().filter(this::isActive).skip(1).findFirst();
    }

    /**
     * 添加端点，房间已满时（超过 MAX_PEERS）拒绝
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
     */
    public synchronized void disconnectSlot(String slotId) {
        slots.stream().filter(s -> s.getSlotId().equals(slotId)).findFirst().ifPresent(s -> s.setDisconnectedAt(System.currentTimeMillis()));
    }

    /**
     * 按 ID 查找端点
     */
    public Optional<SlotState> findSlot(String slotId) {
        return slots.stream().filter(s -> s.getSlotId().equals(slotId)).findFirst();
    }

    /**
     * 房间满载（2 个活跃端点就位）
     */
    public boolean isFull() {
        return slots.size() >= MAX_PEERS;
    }

    /**
     * 房间内还有活跃端点
     */
    public boolean hasActiveSlot() {
        return activePeerCount() > 0;
    }

    /**
     * 获取端点旁的对端
     *
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
