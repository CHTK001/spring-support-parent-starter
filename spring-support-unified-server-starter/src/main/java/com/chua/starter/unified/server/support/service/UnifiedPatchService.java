package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author CH
 */
public interface UnifiedPatchService extends IService<UnifiedPatch> {


    /**
     * 删除修补程序
     *
     * @param id id
     * @return {@link Boolean}
     */
    Boolean removePatch(String id);

    /**
     * 上传补丁
     *
     * @param t             t
     * @param multipartFile 多部件文件
     * @return {@link Boolean}
     */
    Boolean uploadPatch(UnifiedPatch t, MultipartFile multipartFile);

    /**
     * 卸载修补程序
     *
     * @param t t
     * @return {@link Boolean}
     */
    Boolean unloadPatch(UnifiedPatch t);

    /**
     * 上传
     *
     * @param t t
     * @return {@link ErrorResult}
     */
    ErrorResult upload(UnifiedPatch t);
}
