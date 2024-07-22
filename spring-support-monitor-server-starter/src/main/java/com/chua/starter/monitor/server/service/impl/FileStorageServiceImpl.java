package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.monitor.server.entity.FileStorage;
import com.chua.starter.monitor.server.mapper.FileStorageMapper;
import com.chua.starter.monitor.server.service.FileStorageProtocolService;
import com.chua.starter.monitor.server.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static com.chua.common.support.constant.Action.*;

/**
 *
 *
 * @since 2024/7/22 
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl extends ServiceImpl<FileStorageMapper, FileStorage> implements FileStorageService{

    final FileStorageProtocolService fileStorageProtocolService;
    final TransactionTemplate transactionTemplate;

    @Override
    public Boolean deleteFor(Set<String> ids) {
        return transactionTemplate.execute(status -> {
            List<FileStorage> fileStorages = baseMapper.selectBatchIds(ids);
            for (FileStorage fileStorage : fileStorages) {
                fileStorageProtocolService.updateFor(fileStorage, DELETE);
            }
            baseMapper.deleteBatchIds(ids);
            return true;
        });
    }

    @Override
    public Boolean updateFor(FileStorage t) {
        return transactionTemplate.execute(status -> {
            baseMapper.updateById(t);
            FileStorage fileStorage = baseMapper.selectById(t.getFileStorageId());
            Integer fileStorageStatus = fileStorage.getFileStorageStatus();
            if(null == fileStorageStatus) {
                fileStorageStatus = 1;
            }
            fileStorageProtocolService.updateFor(fileStorage, fileStorageStatus != 1 ? DELETE : UPDATE);
            return true;
        });
    }

    @Override
    public Boolean saveFor(FileStorage t) {
        return transactionTemplate.execute(status -> {
            t.setFileStorageStatus(1);
            baseMapper.insert(t);
            fileStorageProtocolService.updateFor(t, CREATE);
            return true;
        });
    }
}
