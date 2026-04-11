package com.chua.starter.server.support.service.impl;

import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ServerAuditExecutor {

    private static final String SYSTEM_AUDIT_USER_ID = "0";
    private static final String SYSTEM_AUDIT_USERNAME = "server-system";

    /**
     * 在保证审计上下文可用的前提下执行写操作。
     */
    public void run(Runnable action) {
        supply(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 当线程中没有登录用户时，临时注入系统审计账号执行数据库写入。
     */
    public <T> T supply(Supplier<T> supplier) {
        AuthClientExecute authClientExecute = AuthClientExecute.getInstance();
        UserResult current = authClientExecute.getSafeUserResult();
        if (current != null) {
            return supplier.get();
        }
        String previousUsername = AuthClientExecute.getUsername();
        UserResume previousUser = AuthClientExecute.getUserInfo(UserResume.class);
        UserResult systemUser = new UserResult();
        systemUser.setUserId(SYSTEM_AUDIT_USER_ID);
        systemUser.setUsername(SYSTEM_AUDIT_USERNAME);
        AuthClientExecute.setUsername(SYSTEM_AUDIT_USERNAME);
        AuthClientExecute.setUserInfo(systemUser);
        try {
            return supplier.get();
        } finally {
            if (previousUsername != null) {
                AuthClientExecute.setUsername(previousUsername);
            } else {
                AuthClientExecute.removeUsername();
            }
            if (previousUser != null) {
                AuthClientExecute.setUserInfo(previousUser);
            } else {
                AuthClientExecute.removeUserInfo();
            }
        }
    }
}
