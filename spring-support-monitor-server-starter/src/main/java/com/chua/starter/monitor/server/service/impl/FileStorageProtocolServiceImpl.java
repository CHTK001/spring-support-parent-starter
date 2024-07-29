package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.chain.filter.UserAgentFilter;
import com.chua.common.support.chain.filter.storage.FileStorageChainFilter;
import com.chua.common.support.constant.Action;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.oss.WebdavStorage;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.protocol.server.Server;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.server.entity.FileStorage;
import com.chua.starter.monitor.server.entity.FileStorageProtocol;
import com.chua.starter.monitor.server.mapper.FileStorageMapper;
import com.chua.starter.monitor.server.mapper.FileStorageProtocolMapper;
import com.chua.starter.monitor.server.service.FileStorageProtocolService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_SEMICOLON;
import static com.chua.common.support.http.HttpHeaders.X_DOWNLOAD_USER_AGENT;
import static com.chua.common.support.http.HttpHeaders.X_USER_AGENT;

/**
 * @author CH
 * @since 2024/7/22
 */
@Service
@RequiredArgsConstructor
public class FileStorageProtocolServiceImpl extends ServiceImpl<FileStorageProtocolMapper, FileStorageProtocol> implements FileStorageProtocolService, InitializingBean {

    private static final Map<String, Server> SERVER_MAP = new ConcurrentHashMap<>();
    private static final Map<String, FileStorageChainFilter.FileStorageFactory> SERVER_FACTORY_FILTER_MAP = new ConcurrentHashMap<>();
    final TransactionTemplate transactionTemplate;
    final FileStorageMapper fileStorageMapper;

    @Override
    public ReturnResult<Boolean> start(FileStorageProtocol fileStorageProtocol) {
        if (ObjectUtils.isEmpty(fileStorageProtocol.getFileStorageProtocolHost()) || ObjectUtils.isEmpty(fileStorageProtocol.getFileStorageProtocolPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(fileStorageProtocol);
        if (SERVER_MAP.containsKey(key)) {
            if(fileStorageProtocol.getFileStorageProtocolStatus() == 0) {
                try {
                    Server server = SERVER_MAP.get(key);
                    if (null != server) {
                        server.stop();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SERVER_MAP.remove(key);
                SERVER_FACTORY_FILTER_MAP.remove(key);
            } else {
                return ReturnResult.error("代理已启动, 请刷新页面");
            }
        }

        return transactionTemplate.execute(it -> {
            fileStorageProtocol.setFileStorageProtocolStatus(1);
            int i = baseMapper.updateById(fileStorageProtocol);
            if (i > 0) {
                FileStorageChainFilter.FileStorageFactory fileStorageFactory = createFileStorage(fileStorageProtocol);
                Server server = createServer(fileStorageProtocol, fileStorageFactory);
                try {
                    server.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SERVER_MAP.put(key, server);
                SERVER_FACTORY_FILTER_MAP.put(key, fileStorageFactory);
                registerFileStorage(fileStorageProtocol);
                return ReturnResult.success();
            }
            return ReturnResult.error("代理启动失败");
        });

    }

    /**
     * 注册文件存储
     *
     * @param fileStorageProtocol
     */
    private void registerFileStorage(FileStorageProtocol fileStorageProtocol) {
        List<FileStorage> list = fileStorageMapper.selectList(Wrappers.<FileStorage>lambdaQuery().eq(FileStorage::getFileStorageProtocolId, fileStorageProtocol.getFileStorageProtocolId())
                .eq(FileStorage::getFileStorageStatus, 1)
        );
        for (FileStorage fileStorage : list) {
            updateFor(fileStorage, Action.UPDATE);
        }
    }

    private FileStorageChainFilter.FileStorageFactory createFileStorage(FileStorageProtocol fileStorageProtocol) {
        return FileStorageChainFilter.FileStorageFactory.create(createSetting(fileStorageProtocol));
    }

    private Server createServer(FileStorageProtocol fileStorageProtocol, FileStorageChainFilter.FileStorageFactory fileStorageFactory) {
        Server server = Server.create(fileStorageProtocol.getFileStorageProtocolName(), ServerSetting
                            .builder()
                            .addCustomHttpHeader(X_USER_AGENT)
                            .addCustomHttpHeader(X_DOWNLOAD_USER_AGENT)
                            .host(fileStorageProtocol.getFileStorageProtocolHost())
                            .port(fileStorageProtocol.getFileStorageProtocolPort())
                            .build()
                    );
        if(fileStorageProtocol.getFileStorageProtocolUaOpen() == 1 && StringUtils.isNotBlank(fileStorageProtocol.getFileStorageProtocolUa())) {
            server.addFilter(new UserAgentFilter(StringUtils.splitAndTrim(fileStorageProtocol.getFileStorageProtocolUa(), SYMBOL_SEMICOLON)));
        }

        server.addFilter(new FileStorageChainFilter(fileStorageFactory));
        return server;
    }

    private FileStorageChainFilter.FileStorageSetting createSetting(FileStorageProtocol fileStorageProtocol) {
        return FileStorageChainFilter.FileStorageSetting.builder()
                .openPlugin(null != fileStorageProtocol.getFileStorageProtocolPluginOpen() && fileStorageProtocol.getFileStorageProtocolPluginOpen() == 1)
                .openSetting(null != fileStorageProtocol.getFileStorageProtocolSettingOpen() && fileStorageProtocol.getFileStorageProtocolSettingOpen() == 1)
                .openRange(null != fileStorageProtocol.getFileStorageProtocolRangeOpen() && fileStorageProtocol.getFileStorageProtocolRangeOpen() == 1)
                .openPreview(null != fileStorageProtocol.getFileStorageProtocolPreviewOrDownload() && (fileStorageProtocol.getFileStorageProtocolPreviewOrDownload() == 0 || fileStorageProtocol.getFileStorageProtocolPreviewOrDownload() == 1))
                .openDownload(null != fileStorageProtocol.getFileStorageProtocolPreviewOrDownload() && (fileStorageProtocol.getFileStorageProtocolPreviewOrDownload() == 0 || fileStorageProtocol.getFileStorageProtocolPreviewOrDownload() == 2))
                .downloadUserAgent(StringUtils.split(fileStorageProtocol.getFileStorageProtocolUa(), ","))
                .plugins(StringUtils.split(fileStorageProtocol.getFileStorageProtocolPlugins(), ","))
                .settings(StringUtils.split(fileStorageProtocol.getFileStorageProtocolSetting(), ","))
                .build();
    }

    @Override
    public ReturnResult<Boolean> stop(FileStorageProtocol fileStorageProtocol) {
        if (ObjectUtils.isEmpty(fileStorageProtocol.getFileStorageProtocolHost()) || ObjectUtils.isEmpty(fileStorageProtocol.getFileStorageProtocolPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(fileStorageProtocol);
        if (!SERVER_MAP.containsKey(key) && 0 == fileStorageProtocol.getFileStorageProtocolStatus()) {
            return ReturnResult.error("代理已停止");
        }

        fileStorageProtocol.setFileStorageProtocolStatus(0);
        return transactionTemplate.execute(it -> {
            int i = baseMapper.updateById(fileStorageProtocol);
            if (i > 0) {
                try {
                    Server server = SERVER_MAP.get(key);
                    if (null != server) {
                        server.stop();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SERVER_MAP.remove(key);
                SERVER_FACTORY_FILTER_MAP.remove(key);
                return ReturnResult.success();
            }

            return ReturnResult.error("代理停止失败");
        });
    }

    @Override
    public void refresh(Integer fileStorageProtocolId) {
        FileStorageProtocol fileStorageProtocol = baseMapper.selectById(fileStorageProtocolId);
        refresh(fileStorageProtocol);
    }

    /**
     * 刷新
     *
     * @param fileStorageProtocol 配置
     */
    private boolean refresh(FileStorageProtocol fileStorageProtocol) {
        String key = createKey(fileStorageProtocol);
        FileStorageChainFilter.FileStorageFactory fileStorageFactory = SERVER_FACTORY_FILTER_MAP.get(key);
        if (null == fileStorageFactory) {
            return false;
        }

        fileStorageFactory.upgrade(createSetting(fileStorageProtocol));
        return true;
    }

    @Override
    public Boolean updateFor(FileStorageProtocol t) {
        return transactionTemplate.execute(it -> {
            baseMapper.updateById(t);
            refresh(t.getFileStorageProtocolId());
            return true;
        });
    }

    @Override
    public Boolean updateFor(FileStorage t, Action action) {
        Integer fileStorageProtocolId = t.getFileStorageProtocolId();
        if (null == fileStorageProtocolId) {
            return false;
        }
        FileStorageProtocol fileStorageProtocol = baseMapper.selectById(fileStorageProtocolId);
        if (null == fileStorageProtocol) {
            return false;
        }


        if(fileStorageProtocol.getFileStorageProtocolStatus() == 0) {
            return false;
        }

        FileStorageChainFilter.FileStorageFactory fileStorageFactory = SERVER_FACTORY_FILTER_MAP.get(createKey(fileStorageProtocol));
        if (null == fileStorageFactory) {
            return false;
        }

        String fileStorageBucket = t.getFileStorageBucket();
        if(action == Action.DELETE) {
            fileStorageFactory.removeFileStorage(fileStorageBucket);
            return true;
        }
        ServiceDefinition serviceDefinition = ServiceProvider.of(com.chua.common.support.oss.FileStorage.class).getDefinition(t.getFileStorageType());
        com.chua.common.support.oss.FileStorage storage = null;

        if(null == serviceDefinition) {
            throw new NullPointerException(t.getFileStorageName() + "(" + t.getFileStorageType() +")文件存储无法创建, 请检查参数!");
        }
        if(serviceDefinition.isAssignableFrom(WebdavStorage.class)) {
            storage = com.chua.common.support.oss.FileStorage.createStorage(t.getFileStorageType(), t.createBucketCookieSetting());
        } else {
            storage = com.chua.common.support.oss.FileStorage.createStorage(t.getFileStorageType(), t.createBucketSetting());
        }

        if(storage == null) {
            throw new NullPointerException(t.getFileStorageName() + "(" + t.getFileStorageType() +")文件存储无法创建, 请检查参数!");
        }
        return fileStorageFactory.upgrade(storage , fileStorageBucket, action);
    }

    @Override
    public FileStorageChainFilter.FileStorageFactory getFactory(FileStorage fileStorage) {
        Integer fileStorageProtocolId = fileStorage.getFileStorageProtocolId();
        if (null == fileStorageProtocolId) {
            return null;
        }
        FileStorageProtocol fileStorageProtocol = baseMapper.selectById(fileStorageProtocolId);
        if (null == fileStorageProtocol) {
            return null;
        }
        return SERVER_FACTORY_FILTER_MAP.get(createKey(fileStorageProtocol));
    }

    private String createKey(FileStorageProtocol fileStorageProtocol) {
        return fileStorageProtocol.getFileStorageProtocolHost() + ":" + fileStorageProtocol.getFileStorageProtocolPort();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                List<FileStorageProtocol> fileStorageProtocols = baseMapper.selectList(Wrappers.<FileStorageProtocol>lambdaQuery().eq(FileStorageProtocol::getFileStorageProtocolStatus, 1));
                for (FileStorageProtocol fileStorageProtocol : fileStorageProtocols) {
                    try {
                        start(fileStorageProtocol);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }
}
