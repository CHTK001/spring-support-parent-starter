package com.chua.starter.common.support.utils;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.FileUtils;
import com.chua.common.support.core.utils.IoUtils;
import com.chua.common.support.core.utils.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
/**
 * 处理多部分文件（MultipartFile）工具类，提供文件上传和转换功能。
 */
/**
 *
 * @author CH
 */
public class MultipartFileUtils {
    /**
     * 将上传的多部分文件转移到指定的父目录下，并根据是否清除目录进行处理。
     *
     * @param multipartFile 上传的多部分文件
     * @param parent 父目录文件对象
     * @param folder 子目录名
     * @param clearFolder 是否清除子目录
     * @return 文件保存路径
     */
    public static ReturnResult<String> transferTo(MultipartFile multipartFile, File parent, String folder, boolean clearFolder) {
        try {
            return transferTo(multipartFile.getInputStream(), multipartFile.getOriginalFilename(), parent, folder, clearFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将上传的多部分文件转移到指定的父目录下。
     *
     * @param multipartFile 上传的多部分文件
     * @param parent 父目录文件对象
     * @param clearFolder 是否清除目录
     * @return 文件保存路径
     */
    public static ReturnResult<String> transferTo(MultipartFile multipartFile, File parent, boolean clearFolder) {
        try {
            return transferTo(multipartFile.getInputStream(), multipartFile.getOriginalFilename(), parent, clearFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将字节数组形式的文件转移到指定的父目录下，并根据是否清除目录进行处理。
     *
     * @param bytes 文件字节数组
     * @param name 文件名
     * @param parent 父目录文件对象
     * @param folder 子目录名
     * @param clearFolder 是否清除子目录
     * @return 文件保存路径
     */
    public static ReturnResult<String> transferTo(byte[] bytes,  String name, File parent, String folder, boolean clearFolder) {
        return transferTo(new ByteArrayInputStream(bytes), name, parent, folder, clearFolder);
    }

    /**
     * 从输入流中读取文件内容，并保存到指定的父目录下，根据是否清除目录进行处理。
     *
     * @param inputStream 文件输入流
     * @param name 文件名
     * @param parent 父目录文件对象
     * @param folder 子目录名
     * @param clearFolder 是否清除子目录
     * @return 文件保存路径
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
     * 将输入流中的文件内容保存到指定的父目录下，根据是否清除目录进行处理。
     *
     * @param inputStream 文件输入流
     * @param name 文件名
     * @param parent 父目录文件对象
     * @param clearFolder 是否清除目录
     * @return 文件保存路径
     */
    public static ReturnResult<String> transferTo(InputStream inputStream, String name, File parent, boolean clearFolder) {
        if(clearFolder) {
            try {
                FileUtils.forceDelete(parent);
            } catch (IOException e) {
//                return ReturnResult.illegal("保存驱动清除失败, " + StringUtils.subAfter(e.getCause().getMessage(), ":", true));

            }
        }
        FileUtils.forceMkdir(parent);


        File driverFile = new File(parent, name);
        try (InputStream is = inputStream;
             OutputStream os = new FileOutputStream(driverFile)
        ) {
            IoUtils.copy(is, os);
        } catch (IOException e) {
            return ReturnResult.illegal("保存驱动失败, " + StringUtils.subAfter(e.getCause().getMessage(), ":", true));
        }

        return ReturnResult.ok(driverFile.getAbsolutePath());
    }

    /**
     * 将多部分文件转换为File对象。
     *
     * @param file 多部分文件
     * @return 转换后的File对象，如果文件为null，则返回null
     * @throws Exception 如果转换过程中出现错误
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
     * 将多部分文件内容转移到指定的文件中。
     *
     * @param multipartFile 多部分文件
     * @param patchFile 目标文件
     * @throws IOException 如果转移过程中出现I/O错误
     */
    public static void transferTo(MultipartFile multipartFile, File patchFile) throws IOException{
        try (InputStream inputStream = multipartFile.getInputStream();
             FileOutputStream fileOutputStream = new FileOutputStream(patchFile)
        ) {
            IoUtils.copy(inputStream, fileOutputStream);
        }
    }
}

