package com.chua.starter.common.support.filestorage;

import com.chua.common.support.lang.file.transfer.FileConverterFactory;
import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.oss.entity.GetResult;
import com.chua.common.support.spi.Option;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 文件存储服务
 *
 * @author CH
 */
public interface FileStorageService {
    /**
     * 包含密钥
     *
     * @param bucket 水桶
     * @return boolean
     */
    boolean containsKey(String bucket);

    /**
     * 收到
     *
     * @param bucket 水桶
     * @return {@link FileStorage}
     */
    FileStorage get(String bucket);

    /**
     * 注册
     *
     * @param bucket      水桶
     * @param fileStorage 文件存储器
     */
    void register(String bucket, FileStorage fileStorage);

    /**
     * 注销
     *
     * @param bucket 水桶
     */
    void unregister(String bucket);

    /**
     * 获取类型
     *
     * @return {@link Set}<{@link String}>
     */
    List<Option<String>> getType();

    /**
     * 格式化
     *
     * @param format    总体安排
     * @param getResult 获取结果
     * @return {@link GetResult}
     */
    default GetResult format(String format, GetResult getResult) {
        if(StringUtils.isEmpty(format)) {
            return getResult;
        }
        String name = getResult.getName();
        String simpleExtension = FileUtils.getSimpleExtension(name);
        String baseName = FileUtils.getBaseName(name);
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(getResult.getBytes());
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()
        ) {
            FileConverterFactory.transform(simpleExtension, byteArrayInputStream, format, byteArrayOutputStream);
            return GetResult.builder()
                    .name(baseName + "." + format)
                    .requestId(getResult.getRequestId())
                    .message(getResult.getMessage())
                    .fileCode(getResult.getFileCode())
                    .bytes(byteArrayOutputStream.toByteArray())
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
            return getResult;
        }
    }
}
