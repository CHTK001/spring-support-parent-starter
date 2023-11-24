package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.starter.unified.server.support.entity.UnifiedPatch;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

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
     * @return {@link ErrorResult}
     */
    ErrorResult uploadPatch(UnifiedPatch t, MultipartFile multipartFile);

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

    /**
     * 分页项目
     *
     * @param page   分页
     * @param entity 实体
     * @return {@link IPage}<{@link UnifiedPatch}>
     */
    IPage<UnifiedPatch> pageItems(Page<UnifiedPatch> page, UnifiedPatch entity);

    /**
     * 获取修补程序文件
     *
     * @param unifiedPatch 统一补丁
     * @return {@link File}
     */
    File getPatchFile(UnifiedPatch unifiedPatch);

    /**
     * 下载补丁
     *
     * @param unifiedPatch 统一补丁
     * @return {@link byte[]}
     */
    byte[] downloadPatch(UnifiedPatch unifiedPatch);
}
