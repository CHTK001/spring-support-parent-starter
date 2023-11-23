package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import com.chua.starter.unified.server.support.entity.UnifiedPatchItem;
import com.chua.starter.unified.server.support.mapper.UnifiedPatchMapper;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedPatchItemService;
import com.chua.starter.unified.server.support.service.UnifiedPatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 *    
 * @author CH
 */     
@Service
public class UnifiedPatchServiceImpl extends ServiceImpl<UnifiedPatchMapper, UnifiedPatch> implements UnifiedPatchService{


    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private UnifiedPatchItemService unifiedPatchItemService;

    @Resource
    private UnifiedServerProperties unifiedServerProperties;

    @Override
    public Boolean removePatch(String id) {
        return transactionTemplate.execute(status -> {
            unifiedPatchItemService.remove(Wrappers.<UnifiedPatchItem>lambdaQuery().eq(UnifiedPatchItem::getUnifiedPatchId, id));
            baseMapper.deleteById(id);
            unloadPatch(getById(id));
            return true;
        });
    }

    @Override
    public Boolean uploadPatch(UnifiedPatch t, MultipartFile multipartFile) {
        String patchPath = StringUtils.defaultString(unifiedServerProperties.getEndpoint().getPatch(), ".");
        File file = new File(patchPath, t.getUnifiedPatchId() + "");
        try {
            FileUtils.forceMkdir(file);
        } catch (IOException ignored) {
        }
        File patchFile = new File(file, t.getUnifiedPatchName() + "-" + t.getUnifiedPatchVersion() + "." + FileUtils.getExtension(multipartFile.getOriginalFilename()));
        try {
            multipartFile.transferTo(patchFile);
            t.setUnifiedPatchPack(patchFile.getName());
        } catch (IOException ignored) {
        }
        return null;
    }

    @Override
    public Boolean unloadPatch(UnifiedPatch t) {
        String patchPath = StringUtils.defaultString(unifiedServerProperties.getEndpoint().getPatch(), ".");
        File file = new File(patchPath, t.getUnifiedPatchId() + "");
        try {
            FileUtils.forceDeleteDirectory(file);
            return true;
        } catch (IOException ignored) {
        }
        return false;
    }

    @Override
    public ErrorResult upload(UnifiedPatch t) {
        List<UnifiedPatchItem> list = unifiedPatchItemService.list(Wrappers.<UnifiedPatchItem>lambdaQuery().eq(UnifiedPatchItem::getUnifiedPatchId, t.getUnifiedPatchId()));
        if(CollectionUtils.isEmpty(list)) {
            return ErrorResult.empty();
        }

        return null;
    }
}
