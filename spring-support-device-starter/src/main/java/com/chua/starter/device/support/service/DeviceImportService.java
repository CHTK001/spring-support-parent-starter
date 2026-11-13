package com.chua.starter.device.support.service;

import com.chua.starter.device.support.model.DeviceImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 设备台账 Excel 导入服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface DeviceImportService {

    /**
     * 导入设备台账。
     *
     * @param file   Excel 文件（xlsx / xls）
     * @param mode   导入模式：create / update / upsert
     * @param dryRun true 只校验不写库，false 校验并写库
     * @return 逐行导入结果
     * @throws IOException 读取文件失败
     */
    DeviceImportResult importDevices(MultipartFile file, String mode, boolean dryRun) throws IOException;
}
