package com.chua.starter.lowcode.support;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 低代码-审计字段自动填充。
 *
 * <p>实体全部继承自 {@link com.chua.starter.mybatis.pojo.SysBase}，审计列（create_name / create_by /
 * create_time / update_name / update_by / update_time）的自动回写交给本类。消费者若注册了自己的
 * {@link MetaObjectHandler}（通常是消费方统一获取登录用户的公共实现），{@link ConditionalOnMissingBean}
 * 会优先消费方的那一个。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@ConditionalOnMissingBean(MetaObjectHandler.class)
public class LowcodeMetaObjectHandler implements MetaObjectHandler {

    /**
     * 懒加载当前上下文中的认证服务，避免引用不到任何上下文时影响启动。
     */
    private final org.springframework.beans.factory.ObjectProvider<AuthService> authServiceProvider;

    public LowcodeMetaObjectHandler(org.springframework.beans.factory.ObjectProvider<AuthService> authServiceProvider) {
        this.authServiceProvider = authServiceProvider;
    }

    /**
     * 插入时自动填充。
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        fillByUser(metaObject, now);
    }

    /**
     * 更新时自动填充。
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        fillByUser(metaObject, now);
    }

    /**
     * 由当前登录用户填充审计列，用户不存在则静默跳过。
     *
     * @param metaObject 元对象
     * @param now        搜索时间
     */
    private void fillByUser(MetaObject metaObject, LocalDateTime now) {
        if (!metaObject.hasSetter("createName") || !metaObject.hasSetter("createBy")) {
            return;
        }
        AuthService authService = authServiceProvider == null ? null : authServiceProvider.getIfAvailable();
        CurrentUser user = authService == null ? null : authService.getCurrentUser();
        if (user == null) {
            return;
        }
        this.strictInsertFill(metaObject, "createName", String.class, user.getName());
        this.strictInsertFill(metaObject, "createBy", Integer.class, toUserId(user.getUserId()));
    }

    /**
     * 用户 id，可能是字符串（如 openId），无法解析时不填充创建人。
     *
     * @param userId 用户标识
     * @return 解析后的标识
     */
    private Integer toUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(userId.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
