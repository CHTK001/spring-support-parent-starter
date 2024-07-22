package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.chain.filter.FileStorageChainFilter;
import com.chua.common.support.constant.Action;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.protocol.server.Server;
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
            return ReturnResult.error("代理已启动, 请刷新页面");
        }

        return transactionTemplate.execute(it -> {
            fileStorageProtocol.setFileStorageProtocolStatus(1);
            int i = baseMapper.updateById(fileStorageProtocol);
            if (i > 0) {
                FileStorageChainFilter.FileStorageFactory fileStorageFactory = createFileStorage(fileStorageProtocol);
                Server server = createServer(fileStorageProtocol, fileStorageFactory);
                try {
                    server.start();
                    registerFileStorage(fileStorageProtocol);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SERVER_MAP.put(key, server);
                SERVER_FACTORY_FILTER_MAP.put(key, fileStorageFactory);
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
                .host(fileStorageProtocol.getFileStorageProtocolHost())
                .port(fileStorageProtocol.getFileStorageProtocolPort())
                .build()
        );
        server.addFilter(new FileStorageChainFilter(fileStorageFactory));
        return server;
    }

    private FileStorageChainFilter.FileStorageSetting createSetting(FileStorageProtocol fileStorageProtocol) {
        return FileStorageChainFilter.FileStorageSetting.builder()
                .openPlugin(fileStorageProtocol.getFileStorageProtocolPluginOpen() == 1)
                .openSetting(fileStorageProtocol.getFileStorageProtocolSettingOpen() == 1)
                .plugins(StringUtils.defaultString(fileStorageProtocol.getFileStorageProtocolPlugins(), "").split(","))
                .settings(StringUtils.defaultString(fileStorageProtocol.getFileStorageProtocolSetting(), "").split(","))
                .build();
    }

    @Override
    public ReturnResult<Boolean> stop(FileStorageProtocol fileStorageProtocol) {
        if (ObjectUtils.isEmpty(fileStorageProtocol.getFileStorageProtocolHost()) || ObjectUtils.isEmpty(fileStorageProtocol.getFileStorageProtocolPort())) {
            return ReturnResult.error("代理地址不能为空");
        }

        String key = createKey(fileStorageProtocol);
        if (!SERVER_MAP.containsKey(key) && 0 == fileStorageProtocol.getFileStorageProtocolPort()) {
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
    public void refresh(String fileStorageProtocolId) {
        FileStorageProtocol fileStorageProtocol = baseMapper.selectById(fileStorageProtocolId);
        refresh(fileStorageProtocol);
    }

    /**
     * 刷新
     *
     * @param fileStorageProtocol
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
        FileStorageChainFilter.FileStorageFactory fileStorageFactory = SERVER_FACTORY_FILTER_MAP.get(createKey(fileStorageProtocol));
        if (null == fileStorageFactory) {
            return false;
        }
        String fileStorageName = t.getFileStorageName();

        return fileStorageFactory.upgrade( com.chua.common.support.oss.FileStorage.createStorage(t.getFileStorageType(), t.createBucketSetting()), fileStorageName, action);
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
