package com.chua.webrtc.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.webrtc.support.entity.WebRtcUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebRTC用户Mapper接口
 *
 * @author CH
 * @since 4.1.0
 */
@Mapper
public interface WebRtcUserMapper extends BaseMapper<WebRtcUser> {

    /**
     * 根据用户名查询用户
     *
     * @param userName 用户名
     * @return 用户信息
     */
    WebRtcUser selectByUserName(@Param("userName") String userName);

    /**
     * 根据房间ID查询房间内的用户列表
     *
     * @param roomId 房间ID
     * @return 用户列表
     */
    List<WebRtcUser> selectByRoomId(@Param("roomId") String roomId);

    /**
     * 查询在线用户列表
     *
     * @return 在线用户列表
     */
    List<WebRtcUser> selectOnlineUsers();

    /**
     * 查询房间内用户数量
     *
     * @param roomId 房间ID
     * @return 用户数量
     */
    int countByRoomId(@Param("roomId") String roomId);

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 用户状态
     * @return 更新行数
     */
    int updateUserStatus(@Param("userId") String userId, @Param("status") String status);

    /**
     * 更新用户当前房间
     *
     * @param userId   用户ID
     * @param roomId   房间ID
     * @param joinTime 加入时间
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int updateUserRoom(@Param("userId") String userId, @Param("roomId") String roomId, @Param("joinTime") LocalDateTime joinTime);

    /**
     * 用户离开房间
     *
     * @param userId    用户ID
     * @param leaveTime 离开时间
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int leaveRoom(@Param("userId") String userId, @Param("leaveTime") LocalDateTime leaveTime);

    /**
     * 更新用户媒体状态
     *
     * @param userId        用户ID
     * @param audioEnabled  音频是否启用
     * @param videoEnabled  视频是否启用
     * @param screenSharing 屏幕共享是否启用
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int updateMediaState(@Param("userId") String userId, @Param("audioEnabled") Boolean audioEnabled, @Param("videoEnabled") Boolean videoEnabled, @Param("screenSharing") Boolean screenSharing);

    /**
     * 批量清空房间内所有用户的房间信息
     *
     * @param roomId    房间ID
     * @param leaveTime 离开时间
     * @return 更新行数
     * @author CH
     * @since 1.0.0
     */
    int clearRoomUsers(@Param("roomId") String roomId, @Param("leaveTime") LocalDateTime leaveTime);
}