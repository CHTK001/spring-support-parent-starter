package com.chua.starter.server.support.provider.project;

import com.chua.common.support.spi.annotations.Spi;

/**
 * macOS 平台发布命令提供者。
 *
 * <p>macOS 属 POSIX 家族，命令面与 Linux 一致（tar / mkdir -p / cd / export 等），
 * 故直接复用 {@link PosixProjectPlatformProvider}，只把 {@code osType()} 标为 MACOS。
 * 这样 macOS 主机是<b>显式登记</b>命中 POSIX 实现，而不是被静默当成 Linux。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "macos", order = 100)
public class MacosProjectPlatformProvider extends PosixProjectPlatformProvider {

    @Override
    public String osType() {
        return "MACOS";
    }
}
