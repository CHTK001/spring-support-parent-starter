package com.chua.starter.common.support.configuration;


import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.oss.setting.BucketSetting;
import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.filestorage.FileStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;

/**
 * 文件存储
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private FileStorageProperties fileStorageProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!fileStorageProperties.isEnabled()) {
            return;
        }

        List<FileStorageProperties.Setting> properties = fileStorageProperties.getProperties();
        if (null == properties) {
            return;
        }
        properties.forEach((value) -> {
            registerFileStorage(registry, value);
        });

    }

    /**
     * 注册文件存储
     *
     * @param registry registry
     * @param value    value
     */
    private void registerFileStorage(BeanDefinitionRegistry registry, FileStorageProperties.Setting value) {
        FileStorage fileStorage = FileStorage.createStorage(value.getType(), BucketSetting.builder()
                .endpoint(value.getEndpoint())
                .bucket(value.getBucket())
                .accessKeyId(value.getAccessKeyId())
                .accessKeySecret(value.getAccessKeySecret())
                .connectionTimeoutMills(value.getConnectionTimeoutMills())
                .sessionTimeoutMills(value.getSessionTimeoutMills())
                .region(value.getRegion())
                .charset(value.getCharset())
                .build());
        String port = value.getPort();
        String[] split = StringUtils.split(port, ",");
        for (String s : split) {
            s = s.trim();
            if (!NumberUtils.isNumber(s)) {
                continue;
            }
            var server = ProtocolServer.create("http", Integer.parseInt(s));
            server.addFilter(new FileStorageChainFilter(FileStorageChainFilter.FileStorageFactory.create(
                            FileStorageChainFilter.FileStorageSetting.builder()
                                    .webjars(true)
                                    .build()

                    )
                    .addFileStorage("/" + value.getBucket(), fileStorage)));

            registry.registerBeanDefinition("fileStorage" + UUID.randomUUID(), BeanDefinitionBuilder
                    .rootBeanDefinition(ProtocolServer.class, () -> server)
                    .setInitMethodName("start")
                    .setDestroyMethodName("stop")
                    .getBeanDefinition()
            );

            log.info("文件存储服务启动成功,端口:{}", s);
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        fileStorageProperties = Binder.get(environment)
                .bindOrCreate(FileStorageProperties.PRE, FileStorageProperties.class);
    }
}
