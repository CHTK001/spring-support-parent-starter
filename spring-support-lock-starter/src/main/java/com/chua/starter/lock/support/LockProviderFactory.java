package com.chua.starter.lock.support;

import com.chua.common.support.concurrent.lock.LockFlow;
import com.chua.common.support.concurrent.lock.LockProvider;
import com.chua.common.support.concurrent.lock.LockSetting;
import com.chua.starter.lock.provider.ReadLockProvider;
import com.chua.starter.lock.provider.StripedLockProvider;
import com.chua.starter.lock.provider.WriteLockProvider;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 锁提供者工厂。
 *
 * <p>对 read/write/striped 等 spring-support 扩展类型做显式分派，
 * 其余类型继续委托给 utils-support 的 LockFlow。</p>
 *
 * @author CH
 * @since 2026-03-28
 */
public class LockProviderFactory {

    public LockProvider createLock(String name, LockSetting setting) {
        String lockType = StringUtils.hasText(setting.getLockType())
                ? setting.getLockType().toLowerCase(Locale.ROOT)
                : "reentrant";

        return switch (lockType) {
            case "read" -> new ReadLockProvider(name, setting.isFair());
            case "write" -> new WriteLockProvider(name, setting.isFair());
            case "striped" -> new StripedLockProvider(name);
            default -> LockFlow.createLock(name, setting);
        };
    }
}
