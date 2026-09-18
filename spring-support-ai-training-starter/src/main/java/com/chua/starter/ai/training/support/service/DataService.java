package com.chua.starter.ai.training.support.service;

import java.io.File;

/**
* 训练数据服务接口：压缩 文件上传与解压。
*
* @author CH
* @since 4.0.0.42
 */
public interface DataService {

    /**
    * 保存上传的 压缩 文件到磁盘。
    *
    * @param data  原始字节
    * @param name  原始文件名
    * @return 保存后的文件路径
    * @throws Exception 保存失败
    */
    String saveUploadedFile(byte[] data, String name) throws Exception;

    /**
    * 解压 压缩 到数据根目录（以 UUID 命名子目录）。
    *
    * @param zipFilePath 压缩 文件路径
    * @return 解压后的数据根目录路径
    * @throws Exception 解压失败
    */
    String unzip(String zipFilePath) throws Exception;

    /**
    * 判断文件是否为 压缩 格式。
    *
    * @param fileName 文件名
    * @return 是否 压缩
    */
    boolean isZipFile(String fileName);
}
