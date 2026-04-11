package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.spi.SoftConfigManager;
import org.springframework.stereotype.Component;

@Component
public class DefaultSoftConfigManager implements SoftConfigManager {

    private final LocalSoftTargetCommandExecutor localExecutor;
    private final SshSoftTargetCommandExecutor sshExecutor;
    private final WinRmSoftTargetCommandExecutor winRmExecutor;

    public DefaultSoftConfigManager(
            LocalSoftTargetCommandExecutor localExecutor,
            SshSoftTargetCommandExecutor sshExecutor,
            WinRmSoftTargetCommandExecutor winRmExecutor) {
        this.localExecutor = localExecutor;
        this.sshExecutor = sshExecutor;
        this.winRmExecutor = winRmExecutor;
    }

    @Override
    public String read(SoftExecutionContext context, String configPath) throws Exception {
        return executor(context).readFile(context.getTarget(), configPath);
    }

    @Override
    public void write(SoftExecutionContext context, String configPath, String content) throws Exception {
        executor(context).writeFile(context.getTarget(), configPath, content);
    }

    private SoftTargetCommandExecutor executor(SoftExecutionContext context) {
        String type = context.getTarget().getTargetType();
        if (localExecutor.supports(type)) {
            return localExecutor;
        }
        if (sshExecutor.supports(type)) {
            return sshExecutor;
        }
        return winRmExecutor;
    }
}
