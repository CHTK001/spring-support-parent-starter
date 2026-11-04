package com.chua.starter.ai.support.workspace.terminal;

/**
 * 终端会话唯一键：账号标识 + 聊天会话标识。
 *
 * <p>终端进程按该键在服务端内存中复用：同一账号的同一聊天会话重新打开终端时，
 * 附着到原进程而不是新建，从而实现页面刷新、WebSocket 断线后的状态延续。</p>
 *
 * @param userId        账号标识
 * @param chatSessionId 聊天会话标识
 * @author CH
 * @since 4.0.0.52
 */
record TerminalSessionKey(String userId, String chatSessionId) {
}
