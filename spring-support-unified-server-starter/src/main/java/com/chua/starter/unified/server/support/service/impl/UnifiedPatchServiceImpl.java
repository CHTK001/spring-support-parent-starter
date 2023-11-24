package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import com.chua.starter.unified.server.support.entity.UnifiedPatchItem;
import com.chua.starter.unified.server.support.mapper.UnifiedPatchMapper;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.chua.starter.unified.server.support.service.UnifiedPatchItemService;
import com.chua.starter.unified.server.support.service.UnifiedPatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;


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
    public ErrorResult uploadPatch(UnifiedPatch t, MultipartFile multipartFile) {
        UnifiedPatch byId = getById(t.getUnifiedPatchId());
        File file = getPatchFile(byId);
        try {
            FileUtils.forceDeleteDirectory(file);
            FileUtils.forceMkdir(file);
        } catch (IOException ignored) {
        }
        File patchFile = new File(file, t.getUnifiedPatchName() + "-" + t.getUnifiedPatchVersion() + "." + FileUtils.getExtension(multipartFile.getOriginalFilename()));
        try {
            MultipartFileUtils.transferTo(multipartFile, patchFile);
            if(patchFile.exists()) {
                t.setUnifiedPatchPack(patchFile.getName());
                baseMapper.updateById(t);
                return ErrorResult.empty();
            }
            return ErrorResult.of("上传失败");
        } catch (IOException ignored) {
        }
        return ErrorResult.of("数据包传输失败");
    }

    @Override
    public Boolean unloadPatch(UnifiedPatch t) {
        if(null == t) {
            return false;
        }
        UnifiedPatch byId = getById(t.getUnifiedPatchId());
        File file = getPatchFile(byId);
        try {
            if(null != file) {
                FileUtils.forceDeleteDirectory(file.getParentFile());
            }
            t.setUnifiedPatchPack(null);
            baseMapper.updateById(t);
            return true;
        } catch (IOException ignored) {
        }
        return false;
    }

    @Override
    public ErrorResult upload(UnifiedPatch t) {
       return unifiedPatchItemService.upload(t);
    }

    @Override
    public IPage<UnifiedPatch> pageItems(Page<UnifiedPatch> page, UnifiedPatch entity) {
        Page<UnifiedPatch> page1 = baseMapper.selectPage(page, Wrappers.lambdaQuery(entity));
        List<UnifiedPatch> records = page1.getRecords();
        if(CollectionUtils.isNotEmpty(records)) {
            List<Integer> item  = records.stream().map(UnifiedPatch::getUnifiedPatchId).collect(Collectors.toList());
            List<UnifiedPatchItem> list = unifiedPatchItemService.list(Wrappers.<UnifiedPatchItem>lambdaQuery().in(UnifiedPatchItem::getUnifiedPatchId, item));
            Map<Integer, List<Integer>> rs = new HashMap<>(list.size());
            for (UnifiedPatchItem patchItem : list) {
                rs.computeIfAbsent(patchItem.getUnifiedPatchId(), it -> new LinkedList<>()).add(patchItem.getUnifiedPatchId());
            }

            for (UnifiedPatch unifiedPatch : records) {
                unifiedPatch.setExecutorIds(rs.get(unifiedPatch.getUnifiedPatchId()));
            }
        }


        return page1;
    }

    /**
     * 获取修补程序文件
     *
     * @param unifiedPatch 统一补丁
     * @return {@link File}
     */
    @Override
    public File getPatchFile(UnifiedPatch unifiedPatch) {
        String patchPath = StringUtils.defaultString(unifiedServerProperties.getEndpoint().getPatch(), ".");
        File file = new File(patchPath, UnifiedServerProperties.EndpointOption.PRE + unifiedPatch.getUnifiedPatchId() + "");
        String unifiedPatchPack = unifiedPatch.getUnifiedPatchPack();
        if(StringUtils.isBlank(unifiedPatchPack)) {
            return file;
        }
        File rs = new File(file, unifiedPatchPack);
        return rs.exists() ? rs : null;
    }
}
