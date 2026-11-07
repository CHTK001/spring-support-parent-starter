package com.chua.starter.lowcode.support;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 低代码-审计字段自动填充。
 *
 * <p>实体全部继承自 {@link com.chua.starter.mybatis.pojo.SysBase}，审计列（create_name / create_by /
 * create_time / update_name / update_by / update_time）的自动回写交给本类。</p>
 *
 * <p>本类不自己声明为组件：{@code @ConditionalOnMissingBean} 在组件扫描阶段判定不可靠
 * （实测会整个 bean 不注册），因此由 {@code LowcodeAutoConfiguration} 用
 * {@code @Bean + @ConditionalOnMissingBean} 装配，消费方注册了自己的实现时自动让位。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
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
        fillCreateUser(metaObject);
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
        fillUpdateUser(metaObject);
    }

    /**
     * 插入时填充创建人信息，用户不存在则静默跳过。
     *
     * @param metaObject 元对象
     */
    private void fillCreateUser(MetaObject metaObject) {
        CurrentUser user = currentUser();
        if (user == null) {
            return;
        }
        if (metaObject.hasSetter("createName")) {
            this.strictInsertFill(metaObject, "createName", String.class, user.getName());
        }
        if (metaObject.hasSetter("createBy")) {
            this.strictInsertFill(metaObject, "createBy", Integer.class, toUserId(user.getUserId()));
        }
    }

    /**
     * 更新时填充更新人信息，用户不存在则静默跳过。
     *
     * @param metaObject 元对象
     */
    private void fillUpdateUser(MetaObject metaObject) {
        CurrentUser user = currentUser();
        if (user == null) {
            return;
        }
        if (metaObject.hasSetter("updateName")) {
            this.strictUpdateFill(metaObject, "updateName", String.class, user.getName());
        }
        if (metaObject.hasSetter("updateBy")) {
            this.strictUpdateFill(metaObject, "updateBy", Integer.class, toUserId(user.getUserId()));
        }
    }

    /**
     * 当前登录用户，取不到返回 null。
     *
     * @return 当前用户
     */
    private CurrentUser currentUser() {
        AuthService authService = authServiceProvider == null ? null : authServiceProvider.getIfAvailable();
        return authService == null ? null : authService.getCurrentUser();
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
