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

    public static RoomMessages empty() {
        return new RoomMessages(null, null, null, null);
    }

    public boolean isReady() {
        return welcome != null;
    }
}
