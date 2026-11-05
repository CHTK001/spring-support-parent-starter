package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.HostOperations;
import com.chua.starter.monitor.support.spi.HostOperationsProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;

/**
 * Windows 平台能力集提供者。
 *
 * <p>只声明平台与建实例，命令文本全部在 {@link WindowsHostOperations} 里；
 * 增加新的 Windows 家族系统（如 Windows Server 核心版）不需要动本类。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Spi(value = "windows", order = 10)
public class WindowsHostOperationsProvider implements HostOperationsProvider {

    @Override
    public String[] platforms() {
        return new String[]{Platforms.WINDOWS};
    }

    @Override
    public HostOperations create(SystemInfoProvider channel, MsServerHost host) {
        return new WindowsHostOperations(channel, host);
    }

    /**
     * 本机命令经 cmd.exe 执行，并先把代码页切到 UTF-8：
     * 不切的话中文路径/中文回显会被按 GBK 解码成乱码。
     *
     * @param cmd 原生命令
     * @return argv
     */
    @Override
    public String[] localShell(String cmd) {
        return new String[]{"cmd.exe", "/c", "chcp 65001 >nul && " + cmd};
    }
}
