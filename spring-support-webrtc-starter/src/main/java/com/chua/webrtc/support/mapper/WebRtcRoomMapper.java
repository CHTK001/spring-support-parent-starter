package com.chua.webrtc.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.webrtc.support.entity.WebRtcRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebRTC房间Mapper接口
 *
 * @author CH
 * @since 4.1.0
 */
@Mapper
public interface WebRtcRoomMapper extends BaseMapper<WebRtcRoom> {

    /**
     * 根据房间号查询房间
     *
     * @param roomNumber 房间号
     * @return 房间信息
     */
    WebRtcRoom selectByRoomNumber(@Param("roomNumber") Long roomNumber);

    /**
     * 根据创建人ID查询房间列表
     *
     * @param creatorId 创建人ID
     * @return 房间列表
     */
    List<WebRtcRoom> selectByCreatorId(@Param("creatorId") String creatorId);

    /**
     * 查询活跃房间列表
     *
     * @return 活跃房间列表
     */
    List<WebRtcRoom> selectActiveRooms();

    /**
     * 查询过期的非活跃房间
     *
     * @param expireTime 过期时间
     * @return 过期房间列表
     */
    List<WebRtcRoom> selectExpiredRooms(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 更新房间状态
     *
     * @param roomId 房间ID
     * @param status 房间状态
     * @return 更新行数
     */
    int updateRoomStatus(@Param("roomId") String roomId, @Param("status") String status);

    /**
     * 更新房间最后活跃时间
     *
     * @param roomId         房间ID
     * @param lastActiveTime 最后活跃时间
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int updateLastActiveTime(@Param("roomId") String roomId, @Param("lastActiveTime") LocalDateTime lastActiveTime);

    /**
     * 更新房间当前用户数
     *
     * @param roomId       房间ID
     * @param currentUsers 当前用户数
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int updateCurrentUsers(@Param("roomId") String roomId, @Param("currentUsers") Integer currentUsers);

    /**
     * 关闭房间
     *
     * @param roomId    房间ID
     * @param closeTime 关闭时间
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int closeRoom(@Param("roomId") String roomId, @Param("closeTime") LocalDateTime closeTime);
}