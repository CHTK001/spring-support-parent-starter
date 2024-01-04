package com.chua.starter.unified.client.support.patch;

import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;

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
    private final UnifiedClientProperties unifiedClientProperties;

    public PatchResolver(String patchFileName, UnifiedClientProperties unifiedClientProperties) {
        this.patchFileName = patchFileName;
        this.unifiedClientProperties = unifiedClientProperties;
    }

    public void resolve(String patchFile) {
        String patchPath = getPath();
        File newPatchPath = createPatchPath(patchPath);
        byte[] decode = Base64.getDecoder().decode(patchFile);
        File file = new File(newPatchPath, patchFileName);
        try {
            FileUtils.writeToFile(file, decode);
        } catch (IOException ignored) {
        }
    }

    private File createPatchPath(String patchPath) {
        File file = new File(StringUtils.defaultString(patchPath, UnifiedClientProperties.EndpointOption.PRE));
        FileUtils.mkdir(file);
        return file;
    }

    private String getPath() {
        UnifiedClientProperties.EndpointOption enhance = unifiedClientProperties.getEnhance();
        String hotspot = enhance.getHotspot();
        return StringUtils.defaultString(hotspot, ".");
    }
}
