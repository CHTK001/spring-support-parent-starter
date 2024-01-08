package com.chua.starter.common.support.filestorage;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.oss.entity.GetResult;
import com.chua.common.support.oss.entity.PutResult;
import com.chua.common.support.oss.options.FileStorageOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储提供程序
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/file")
@EnableConfigurationProperties(FileStorageProperties.class)
@AllArgsConstructor
public class FileStorageProvider implements InitializingBean {
    private final FileStorageProperties fileStorageProperties;

    private final FileStorageLoggerService fileStorageLoggerService;
    private static final Map<String, FileStorage> STORAGE_MAP = new ConcurrentHashMap<>();


    @Override
    public void afterPropertiesSet() throws Exception {
        List<FileStorageProperties.FileStorageConfig> config = fileStorageProperties.getConfig();
        for (FileStorageProperties.FileStorageConfig fileStorageConfig : config) {
            FileStorage fileStorage = ServiceProvider.of(FileStorage.class)
                    .getNewExtension(fileStorageConfig.getImpl(), BeanUtils.copyProperties(fileStorageConfig, FileStorageOption.class));
            if(null == fileStorage) {
                continue;
            }
            STORAGE_MAP.put(fileStorageConfig.getBucket(), fileStorage);
        }
    }
    /**
     * 预览
     *
     * @param bucket 水桶
     * @param url    url
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("{bucket}/preview/{url}")
    public ResponseEntity<byte[]> preview(@RequestPart("bucket") String bucket, @RequestPart("fileId")String url) {
        if(StringUtils.isEmpty(bucket) || STORAGE_MAP.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }
        FileStorage fileStorage = STORAGE_MAP.get(bucket);
        File file = null;
        try {
            GetResult getResult = fileStorage.getObject(url);
            if(null == getResult) {
                throw new RuntimeException("文件下载失败");
            }

            if(StringUtils.isNotBlank(getResult.getMessage())) {
                throw new RuntimeException(getResult.getMessage());
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + getResult.getName()+ "\"")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败");
        }
    }
    /**
     * 下载
     *
     * @param bucket 水桶
     * @param url    url
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("{bucket}/download/{url}")
    public ResponseEntity<byte[]> download(@RequestPart("bucket") String bucket, @RequestPart("fileId")String url) {
        if(StringUtils.isEmpty(bucket) || STORAGE_MAP.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }
        FileStorage fileStorage = STORAGE_MAP.get(bucket);
        File file = null;
        try {
            GetResult getResult = fileStorage.getObject(url);
            if(null == getResult) {
                throw new RuntimeException("文件下载失败");
            }

            if(StringUtils.isNotBlank(getResult.getMessage())) {
                throw new RuntimeException(getResult.getMessage());
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + getResult.getName()+ "\"")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败");
        }
    }
    /**
     * 上载
     *
     * @param multipartFile 多部件文件
     * @param bucket        水桶
     * @return {@link ErrorResult}
     */
    @PostMapping("{bucket}/upload")
    public ReturnResult<PutResult> upload(MultipartFile multipartFile, @RequestPart("bucket") String bucket) {
        if(StringUtils.isEmpty(bucket) || STORAGE_MAP.containsKey(bucket)) {
            return ReturnResult.illegal("bucket不存在");
        }

        FileStorage fileStorage = STORAGE_MAP.get(bucket);
        File file = null;
        try {
            file = MultipartFileUtils.toFile(multipartFile);
            PutResult putResult = fileStorage.putObject(file);
            if(null == putResult) {
                return ReturnResult.illegal("上传文件失败");
            }

            if(StringUtils.isNotBlank(putResult.getMessage())) {
                return ReturnResult.illegal(putResult.getMessage());
            }

            return ReturnResult.ok(putResult);
        } catch (Exception e) {
            return ReturnResult.illegal("上传文件失败");
        } finally {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ignored) {
            }
        }
    }

}
