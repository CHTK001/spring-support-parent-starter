package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import com.chua.starter.unified.server.support.entity.UnifiedPatchItem;

/**
 * @author CH
 */
public interface UnifiedPatchItemService extends IService<UnifiedPatchItem> {


    /**
     * 上载
     *
     * @param t t
     * @return {@link ErrorResult}
     */
    ErrorResult upload(UnifiedPatch t);
}
