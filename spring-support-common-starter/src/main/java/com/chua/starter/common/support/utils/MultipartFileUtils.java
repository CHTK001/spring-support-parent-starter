package com.chua.starter.common.support.utils;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
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
     * @param clearFolder   清除文件夹
     * @return {@link ReturnResult}<{@link String}>
     */
    public static ReturnResult<String> transferTo(MultipartFile multipartFile, File parent, String folder, boolean clearFolder) {
        try {
            return transferTo(multipartFile.getInputStream(), multipartFile.getOriginalFilename(), parent, folder, clearFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 转移到
     *
     * @param bytes       gen驱动程序文件
     * @param parent      保存父目录
     * @param folder      保存目录
     * @param name        名称
     * @param clearFolder 清除文件夹
     * @return {@link ReturnResult}<{@link String}>
     */
    public static ReturnResult<String> transferTo(byte[] bytes,  String name, File parent, String folder, boolean clearFolder) {
        return transferTo(new ByteArrayInputStream(bytes), name, parent, folder, clearFolder);
    }

    /**
     * 转移到
     *
     * @param inputStream gen驱动程序文件
     * @param parent      保存父目录
     * @param folder      保存目录
     * @param name        名称
     * @param clearFolder 清除文件夹
     * @return {@link ReturnResult}<{@link String}>
     */
    public static ReturnResult<String> transferTo(InputStream inputStream, String name, File parent, String folder, boolean clearFolder) {
        if(clearFolder) {
            try {
                FileUtils.forceDelete(new File(parent, folder));
            } catch (IOException e) {
                return ReturnResult.illegal("保存驱动清除失败");
            }
        }
        FileUtils.forceMkdir(new File(parent, folder));


        File driverFile = new File(parent, folder + "/" + name);
        try (InputStream is = inputStream;
             OutputStream os = new FileOutputStream(driverFile)
        ) {
            IoUtils.copy(is, os);
        } catch (IOException e) {
            return ReturnResult.illegal("保存驱动失败");
        }

        return ReturnResult.ok(driverFile.getAbsolutePath());
    }

    /**
     * 到文件
     *
     * @param file 文件
     * @return {@link File}
     */
    public static File toFile(MultipartFile file) throws Exception{
        if(null == file) {
            return null;
        }
        File file1 = new File("./temp", file.getOriginalFilename());
        FileUtils.mkParentDirs(file1);
        file.transferTo(file1.toPath());
        return file1;
    }

    /**
     * 转移到
     *
     * @param multipartFile 多部件文件
     * @param patchFile     修补程序文件
     * @throws IOException IOException
     */
    public static void transferTo(MultipartFile multipartFile, File patchFile) throws IOException{
        try (InputStream inputStream = multipartFile.getInputStream();
             FileOutputStream fileOutputStream = new FileOutputStream(patchFile)
        ) {
            IoUtils.copy(inputStream, fileOutputStream);
        }
    }
}
