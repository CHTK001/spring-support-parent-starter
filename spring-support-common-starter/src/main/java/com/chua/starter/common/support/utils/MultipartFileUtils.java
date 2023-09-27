package com.chua.starter.common.support.utils;

import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.result.ReturnResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

/**
 * 多部分文件实用程序
 *
 * @author CH
 */
public class MultipartFileUtils {
    /**
     * 转移到
     *
     * @param multipartFile gen驱动程序文件
     * @param parent        保存父目录
     * @param folder        保存目录
     * @return {@link ReturnResult}<{@link String}>
     */
    public static ReturnResult<String> transferTo(MultipartFile multipartFile, File parent, String folder) {
        try {
            FileUtils.forceMkdir(new File(parent, folder));
        } catch (IOException e) {
            return ReturnResult.illegal("保存驱动失败");
        }
        File driverFile = new File(parent, folder + "/" + multipartFile.getOriginalFilename());
        try (InputStream is = multipartFile.getInputStream();
             OutputStream os = new FileOutputStream(driverFile);
        ){
            IoUtils.copy(is, os);
        } catch (IOException e) {
            return ReturnResult.illegal("保存驱动失败");
        }

        return ReturnResult.ok(driverFile.getAbsolutePath());
    }
}
