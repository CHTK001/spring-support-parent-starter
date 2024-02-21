package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.monitor.server.entity.MonitorPatch;
import com.chua.starter.monitor.server.mapper.MonitorPatchMapper;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.service.MonitorPatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Service
public class MonitorPatchServiceImpl extends ServiceImpl<MonitorPatchMapper, MonitorPatch> implements MonitorPatchService{
    @Resource
    private GenProperties genProperties;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Override
    public Boolean removePatch(String id) {
        return transactionTemplate.execute(status -> {
            unloadPatch(getById(id));
            baseMapper.deleteById(id);
            return true;
        });
    }

    @Override
    public ErrorResult uploadPatch(MonitorPatch t, MultipartFile multipartFile) {
        MonitorPatch byId = getById(t.getMonitorPatchId());
        File file = getPatchFile(byId);
        FileUtils.forceDeleteDirectory(file);
        FileUtils.forceMkdir(file);
        File patchFile = new File(file, t.getMonitorPatchName() + "-" + StringUtils.defaultString(t.getMonitorPatchVersion(), "1.0.0") + "." + FileUtils.getExtension(multipartFile.getOriginalFilename()));
        try {
            MultipartFileUtils.transferTo(multipartFile, patchFile);
            if(patchFile.exists()) {
                t.setMonitorPatchPack(patchFile.getName());
                baseMapper.updateById(t);
                return ErrorResult.empty();
            } else {
                baseMapper.updateById(t);
            }
            return ErrorResult.of("上传失败");
        } catch (IOException ignored) {
        }
        return ErrorResult.of("数据包传输失败");
    }

    @Override
    public Boolean unloadPatch(MonitorPatch byId) {
        if(null == byId) {
            return false;
        }
        File file = getPatchFile(byId);
        if(null != file) {
            FileUtils.forceDeleteDirectory(file.getParentFile());
        }
        baseMapper.update(byId, Wrappers.<MonitorPatch>lambdaUpdate()
                .set(MonitorPatch::getMonitorPatchPack, null)
                .eq(MonitorPatch::getMonitorPatchId, byId.getMonitorPatchId())
        );
        return true;
    }


    /**
     * 获取修补程序文件
     *
     * @param unifiedPatch 统一补丁
     * @return {@link File}
     */
    @Override
    public File getPatchFile(MonitorPatch unifiedPatch) {
        String patchPath = StringUtils.defaultString(genProperties.getTempPath(), ".");
        return new File(patchPath + "/../", "patch/" + unifiedPatch.getMonitorPatchId());
    }

    @Override
    public byte[] downloadPatch(MonitorPatch unifiedPatch) {
        File patchFile = getPatchFile(unifiedPatch);
        if(null == patchFile) {
            throw new RuntimeException("补丁不存在");
        }
        patchFile = new File(patchFile, unifiedPatch.getMonitorPatchPack());
        if(!patchFile.exists()) {
            throw new RuntimeException("补丁不存在");
        }

        try (FileInputStream fileInputStream = new FileInputStream(patchFile)) {
            return IoUtils.toByteArray(fileInputStream);
        } catch (IOException e) {
            throw new RuntimeException("补丁无法解析");
        }
    }
}
