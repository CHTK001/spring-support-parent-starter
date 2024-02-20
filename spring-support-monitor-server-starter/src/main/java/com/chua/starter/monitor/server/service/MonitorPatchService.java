package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.starter.monitor.server.entity.MonitorPatch;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface MonitorPatchService extends IService<MonitorPatch>{

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
    ErrorResult uploadPatch(MonitorPatch t, MultipartFile multipartFile);

    /**
     * 卸载修补程序
     *
     * @param t t
     * @return {@link Boolean}
     */
    Boolean unloadPatch(MonitorPatch t);


    /**
     * 获取修补程序文件
     *
     * @param unifiedPatch 统一补丁
     * @return {@link File}
     */
    File getPatchFile(MonitorPatch unifiedPatch);

    /**
     * 下载补丁
     *
     * @param unifiedPatch 统一补丁
     * @return {@link byte[]}
     */
    byte[] downloadPatch(MonitorPatch unifiedPatch);

}
