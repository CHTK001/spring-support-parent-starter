package com.chua.socketio.support.wrapper;

import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.corundumstudio.socketio.Configuration;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 包裹配置
 * @author CH
 * @since 2024/12/11
 */
@Data
@AllArgsConstructor
public class WrapperConfiguration {

    /**
     * 配置
     */
    private Configuration configuration;

    /**
     * 房间配置
     */
    private SocketIoProperties.Room room;


    /**
     * 客户端ID
     * @return
     */
    public String getClientId () {
        return StringUtils.defaultString(room.getClientId(), String.valueOf(room.getPort()));
    }
}
