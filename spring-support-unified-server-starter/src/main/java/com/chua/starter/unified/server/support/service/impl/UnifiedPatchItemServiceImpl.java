package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.common.support.utils.FileUtils;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import com.chua.starter.unified.server.support.entity.UnifiedPatchItem;
import com.chua.starter.unified.server.support.mapper.UnifiedPatchItemMapper;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.chua.starter.unified.server.support.service.UnifiedPatchItemService;
import com.chua.starter.unified.server.support.service.UnifiedPatchService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *    
 * @author CH
 */     
@Service
public class UnifiedPatchItemServiceImpl extends NotifyServiceImpl<UnifiedPatchItemMapper, UnifiedPatchItem> implements UnifiedPatchItemService{

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private UnifiedPatchService unifiedPatchService;
    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;
    @Resource
    private UnifiedPatchItemService unifiedPatchItemService;

    public UnifiedPatchItemServiceImpl() {
        setGetProfile(it -> "dev");
        setGetAppName(UnifiedPatchItem::getUnifiedAppname);
        setResponseConsumer(it -> {});
        setModuleType(ModuleType.PATCH);
    }

    @Override
    public ErrorResult upload(UnifiedPatch t) {
        return transactionTemplate.execute(status -> {
            UnifiedPatch unifiedPatch = unifiedPatchService.getById(t.getUnifiedPatchId());
            File file = unifiedPatchService.getPatchFile(unifiedPatch);
            if(null == file) {
                return ErrorResult.of("补丁包不存在");
            }

            String base64 = FileUtils.toBase64(file);
//            unifiedPatch.setPatchFile();
            List<Integer> executorIds = t.getExecutorIds();
            List<UnifiedExecuterItem> list = unifiedExecuterItemService.list(new MPJLambdaWrapper<UnifiedExecuterItem>()
                    .selectAll(UnifiedExecuterItem.class)
                    .selectAs(UnifiedExecuter::getUnifiedAppname, UnifiedExecuterItem::getUnifiedAppname)
                    .innerJoin(UnifiedExecuter.class, UnifiedExecuter::getUnifiedExecuterId, UnifiedExecuterItem::getUnifiedExecuterId)
                    .in(UnifiedExecuter::getUnifiedExecuterId, executorIds));

            List<UnifiedPatchItem> items = new ArrayList<>(list.size());
            for (UnifiedExecuterItem unifiedExecuterItem : list) {
                UnifiedPatchItem item = new UnifiedPatchItem();
                item.setUnifiedPatchId(t.getUnifiedPatchId());
                item.setCreateTime(new Date());
                item.setUnifiedExecuterId(unifiedExecuterItem.getUnifiedExecuterId());
                item.setUnifiedAppname(unifiedExecuterItem.getUnifiedAppname());
                item.setPatchFile(base64);

                notifyClient(unifiedExecuterItem,  item);

                items.add(item);
            }

            unifiedPatchItemService.remove(Wrappers.<UnifiedPatchItem>lambdaQuery().eq(UnifiedPatchItem::getUnifiedPatchId, t.getUnifiedPatchId()));
            unifiedPatchItemService.saveBatch(items);
            return ErrorResult.empty();
        });
    }
}
