package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.HostOperations;
import com.chua.starter.monitor.support.spi.HostOperationsProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;

/**
 * POSIX 平台能力集提供者：Linux / 各 BSD / AIX / Solaris，并作为兜底实现。
 *
 * <p>macOS 不在本清单里：它的命令族与 Linux 差异太大（launchd / settings / pf / 无 {@code /proc}），
 * 由 {@link MacHostOperationsProvider} 精确命中。把 macOS 留在这里会让主机画像页
 * 静默按 Linux 口径下发命令，得到「命令执行了但没生效」的结果。</p>
 *
 * <p>之所以把剩下的平台放进同一个实现：它们的命令集本来就是同一族
 * （{@code ps}、{@code kill -0}、{@code systemctl}、{@code /proc} 或 {@code sysctl}），
 * 差异由 {@link PosixHostOperations} 内部按真实平台取样，而不是在调用方写分支。</p>
 *
 * <p>兜底（{@link #fallback()}）的意义：台账里平台没填、或填了本版本还不认识的系统时，
 * 仍然按 POSIX 口径尝试一次，而不是直接拒绝——这与改造前「未知平台按 POSIX 处理」的
 * 历史行为保持一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(value = "posix", order = 100)
public class PosixHostOperationsProvider implements HostOperationsProvider {

    @Override
    public String[] platforms() {
        return new String[]{Platforms.LINUX, Platforms.FREEBSD, Platforms.AIX, Platforms.SOLARIS};
    }

    @Override
    public boolean fallback() {
        return true;
    }

    @Override
    public HostOperations create(SystemInfoProvider channel, MsServerHost host) {
        return new PosixHostOperations(channel, host);
    }
}
