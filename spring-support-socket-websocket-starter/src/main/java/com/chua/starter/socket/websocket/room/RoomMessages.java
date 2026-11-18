package com.chua.starter.socket.websocket.room;

/**
 * 房间消息出站信封组合。
 *
 * <p>对应 ZestSend {@code src/worker/room.ts} 的 `admit` 方法出站流程：
 * <ul>
 *   <li>{@code welcome} - 发送给加入者的房间欢迎信封</li>
 *   <li>{@code negotiateToJoiner} - 发送给加入者的 `negotiate` 信封</li>
 *   <li>{@code peerReadyToOther} - 发送给房间中已存在对端的 `peer-ready` 信封</li>
 *   <li>{@code negotiateToOther} - 发送给房间中已存在对端的 `negotiate` 信封</li>
 * </ul>
 *
 * @author CH
 * @since 2026-11-03
 */
public record RoomMessages(

        /** 房间内对端的欢迎信封 */
        RoomMessage welcome,

        /** 对进入者的 negotiate 信封 */
        RoomMessage negotiateToJoiner,

        /** 对已有对端的 peer-ready 信封 */
        RoomMessage peerReadyToOther,

        /** 对已有对端的 negotiate 信封 */
        RoomMessage negotiateToOther) {

    /**
     * 空信封：四个分量均为空，用于「尚未就绪」的初始状态。
     *
     * @return 空房间消息信封
     */
    public static RoomMessages empty() {
        return new RoomMessages(null, null, null, null);
    }

    /**
     * 是否已就绪：欢迎信封已设置。
     *
     * @return 已就绪返回 true
     */
    public boolean isReady() {
        return welcome != null;
    }
}
