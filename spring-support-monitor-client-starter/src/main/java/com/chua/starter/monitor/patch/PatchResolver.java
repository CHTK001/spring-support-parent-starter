package com.chua.starter.monitor.patch;

import com.chua.common.support.utils.FileUtils;
import com.chua.starter.monitor.factory.MonitorFactory;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

/**
 * 补丁解析器
 *
 * @author CH
 */
public class PatchResolver {
    private final String patchFileName;

    public PatchResolver(String patchFileName) {
        this.patchFileName = patchFileName;
    }

    public void resolve(String patchFile) {
        File newPatchPath = createPatchPath();
        byte[] decode = Base64.getDecoder().decode(patchFile);
        File file = new File(newPatchPath, patchFileName);
        try {
            FileUtils.writeToFile(file, decode);
        } catch (IOException ignored) {
        }
    }

    private File createPatchPath() {
        File file = new File(MonitorFactory.getInstance().getHotspotPath());
        FileUtils.mkdir(file);
        return file;
    }

}
