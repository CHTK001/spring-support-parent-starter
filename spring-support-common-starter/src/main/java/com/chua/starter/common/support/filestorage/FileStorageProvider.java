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
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文件存储提供程序
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/file")
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageProvider implements ApplicationContextAware {
    private FileStorageProperties fileStorageProperties;

    private FileStorageLoggerService fileStorageLoggerService;

    @Resource
    private FileStorageService fileStorageService;


    /**
     * 预览
     *
     * @param bucket 水桶
     * @param url    url
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("{bucket}/preview/{url}")
    public ResponseEntity<byte[]> preview(@PathVariable("bucket") String bucket, @PathVariable("url")String url) {
        if(StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }
        FileStorage fileStorage = fileStorageService.get(bucket);
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
                    .body(getResult.getBytes());
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
    public ResponseEntity<byte[]> download(@PathVariable("bucket") String bucket, @PathVariable("fileId")String url) {
        if(StringUtils.isEmpty(bucket) || fileStorageService.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }
        FileStorage fileStorage = fileStorageService.get(bucket);
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
    public ReturnResult<PutResult> upload(@RequestParam("file") MultipartFile multipartFile, @PathVariable("bucket") String bucket) {
        if(StringUtils.isEmpty(bucket) || fileStorageService.containsKey(bucket)) {
            return ReturnResult.illegal("bucket不存在");
        }

        FileStorage fileStorage = fileStorageService.get(bucket);
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

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.fileStorageProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(FileStorageProperties.PRE, FileStorageProperties.class);
        Map<String, FileStorageLoggerService> beansOfType = applicationContext.getBeansOfType(FileStorageLoggerService.class);
        if(!MapUtils.isEmpty(beansOfType)) {
            this.fileStorageLoggerService = MapUtils.getFirstValue(beansOfType);
        }
        List<FileStorageProperties.FileStorageConfig> config = fileStorageProperties.getConfig();
        for (FileStorageProperties.FileStorageConfig fileStorageConfig : config) {
            FileStorageOption fileStorageOption = FileStorageOption.builder().build();
            BeanUtils.copyProperties(fileStorageConfig, fileStorageOption);

            FileStorage fileStorage = ServiceProvider.of(FileStorage.class).getNewExtension(fileStorageConfig.getImpl(), fileStorageOption);
            if(null == fileStorage) {
                continue;
            }
            fileStorageService.register(fileStorageConfig.getBucket(), fileStorage);
        }
    }
}
