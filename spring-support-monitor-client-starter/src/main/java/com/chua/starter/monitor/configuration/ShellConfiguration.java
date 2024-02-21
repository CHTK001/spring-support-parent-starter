package com.chua.starter.monitor.configuration;

import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.annotations.ServiceMapping;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.protocol.boot.ProtocolServer;
import com.chua.common.support.shell.BaseShell;
import com.chua.common.support.shell.Command;
import com.chua.common.support.shell.WebShell;
import com.chua.common.support.shell.mapping.DelegateCommand;
import com.chua.common.support.shell.mapping.HelpCommand;
import com.chua.common.support.shell.mapping.SystemCommand;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.shell.command.CfrCommand;
import com.chua.starter.monitor.shell.command.SpringCommand;
import com.chua.starter.monitor.shell.resolver.Resolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 外壳配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
public class ShellConfiguration implements BeanFactoryAware {

    public static BaseShell shell = new WebShell();
    static {
        shell.register(new HelpCommand());
        shell.register(new DelegateCommand());
        shell.register(new SystemCommand());
        shell.register(new SpringCommand());
        shell.register(new CfrCommand());
    }
    private ProtocolServer protocolServer;
    public static final String ADDRESS = "IP.ADDRESS";
    private ConfigurableListableBeanFactory beanFactory;

    @ServiceMapping("shell")
    public BootResponse listen(BootRequest request) {
        if(request.getCommandType() != CommandType.REQUEST) {
            return BootResponse.notSupport("The non-register command is not supported");
        }

        Command command = Json.fromJson(request.getContent(), Command.class);
        if(null == command || null == command.getCommand()) {
            return BootResponse.notSupport("The non-register command is not supported");
        }

        Resolver resolver = ServiceProvider.of(Resolver.class).getNewExtension(command.getCommand());
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(CommandType.RESPONSE)
                        .content(resolver.execute(command, shell))
                        .build())
                .build();
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "ConfigValueAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(ProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }

        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(ProtocolServer.class);
        this.protocolServer.addListen(this);
    }

}
