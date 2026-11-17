package com.chua.starter.socket.websocket.room.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 加入房间的结果。
 *
 * @author CH
 * @since 2026-11-03
 */
public class RoomJoinResult {

    /** 房间编号 */
    private final String roomId;

    /** 房间中的活跃端点列表 */
    private final List<Endpoint> endpoints;

    /** 是否已匹配到对等方 */
    private final boolean ready;

    /**
     * 构造加入房间的结果。
     *
     * @param roomId    房间编号
     * @param endpoints 房间中的活跃端点列表
     * @param ready     是否已匹配到对等方
     */
    public RoomJoinResult(String roomId, List<Endpoint> endpoints, boolean ready) {
        this.roomId = roomId;
        this.endpoints = endpoints;
        this.ready = ready;
    }

    /**
     * 获取房间编号。
     *
     * @return 房间编号
     */
    public String getRoomId() {
        return roomId;
    }

    /**
     * 获取房间中的活跃端点列表。
     *
     * @return 活跃端点列表
     */
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    /**
     * 是否已匹配到对等方。
     *
     * @return 是否已匹配到对等方
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 端点
     */
    public record Endpoint(String id, String slotId) {
    }

    /**
     * 获取全部端点 ID。
     *
     * @return 端点 ID 列表
     */
    public List<String> getEndpointIds() {
        List<String> ids = new ArrayList<>();
        for (Endpoint e : endpoints) {
            ids.add(e.id());
        }
        return ids;
    }
}
