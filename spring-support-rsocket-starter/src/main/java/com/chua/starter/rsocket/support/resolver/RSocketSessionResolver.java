package com.chua.starter.rsocket.support.resolver;

import com.chua.starter.rsocket.support.session.RSocketSession;

/**
 * RSocket会话解析器接口
 * <p>
 * 用于解析和管理RSocket会话
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
public interface RSocketSessionResolver {

    /**
     * 添加会话
     * 
     * @param session RSocket会话
     */
    void addSession(RSocketSession session);

    /**
     * 移除会话
     * 
     * @param sessionId 会话ID
     */
    void removeSession(String sessionId);

    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return RSocket会话，不存在则返回null
     */
    RSocketSession getSession(String sessionId);

    /**
     * 判断会话是否存在
     * 
     * @param sessionId 会话ID
     * @return 是否存在
     */
    boolean hasSession(String sessionId);

    /**
     * 获取在线会话数量
     * 
     * @return 在线会话数量
     */
    int getSessionCount();
}

