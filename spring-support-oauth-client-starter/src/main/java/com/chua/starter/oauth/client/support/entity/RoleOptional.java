package com.chua.starter.oauth.client.support.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 角色选项
 *
 * @author CH
 * @since 2024/12/31
 */
public class RoleOptional<T> {
    private final Set<String> roles;
    private final Map<String, Supplier<T>> condition = new LinkedHashMap<>();

    public RoleOptional(Set<String> roles) {
        this.roles = roles;
    }


    /**
     * 是否包含
     *
     * @param role 角色
     * @return this
     */
    public RoleOptional<T> withRole(String role, Supplier<T> supplier) {
        condition.put(role, supplier);
        return this;
    }


    /**
     * 默认为空
     *
     * @return this
     */
    public RoleOptional<T> withSafeOfNull(Supplier<T> supplier) {
        return withRole("", supplier);
    }


    public T get() {
        for (String role : condition.keySet()) {
            if (roles.contains(role)) {
                return condition.get(role).get();
            }
        }

        return condition.get("").get();
    }

}
