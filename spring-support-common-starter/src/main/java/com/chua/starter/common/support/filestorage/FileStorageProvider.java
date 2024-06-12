package com.chua.starter.common.support.filestorage;

import com.chua.common.support.binary.ByteSourceFile;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.oss.metadata.Metadata;
import com.chua.common.support.oss.request.GetObjectRequest;
import com.chua.common.support.oss.request.ListObjectRequest;
import com.chua.common.support.oss.request.PutObjectRequest;
import com.chua.common.support.oss.result.GetObjectResult;
import com.chua.common.support.oss.result.PutObjectResult;
import com.chua.common.support.oss.setting.BucketSetting;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 文件存储提供程序
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/file")
@EnableConfigurationProperties(FileStorageProperties.class)
@Slf4j
public class FileStorageProvider implements ApplicationContextAware {
    private FileStorageProperties fileStorageProperties;

    private FileStorageLoggerService fileStorageLoggerService;

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 列表
     *
     * @param bucket 水桶
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("{bucket}/list")
    public ReturnPageResult<Metadata> preview(@PathVariable("bucket") String bucket,
                                              @RequestParam(value = "marker", required = false, defaultValue = "1") String marker,
                                              @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
                                              @RequestParam(value = "path", required = false) String path,
                                              HttpServletRequest request) {
        if (StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }

        if(StringUtils.isNotBlank(path)) {
            path = URLDecoder.decode(path, UTF_8);
        }
        FileStorage fileStorage = fileStorageService.get(bucket);
        return ReturnPageResult.ok(fileStorage.listObject(ListObjectRequest.builder().filePath(path).marker(marker).limit(pageSize).build()));
    }
    /**
     * 预览
     *
     * @param bucket 水桶
     * @param url    url
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("{bucket}/preview/**")
    public ResponseEntity<byte[]> preview(@PathVariable("bucket") String bucket,
                                          @PathVariable(required = false)String url,
                                          @RequestParam(value = "format", required = false) String format,
                                          HttpServletRequest request) {
        url =  StringUtils.isEmpty(url) ?  StringUtils.after(request.getRequestURI(), "preview", 1): url;
        if(StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }
        FileStorage fileStorage = fileStorageService.get(bucket);

        if(null == fileStorage) {
            throw new RuntimeException("bucket不存在");
        }

        try {
            GetObjectResult getObjectResult = fileStorage.getObject(GetObjectRequest.builder()
                    .filePath(FileUtils.getPath(url))
                    .fileName(FileUtils.getName(url))
                    .build());
            if(null == getObjectResult) {
                log.error("文件解析失败");
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body("文件不存在".getBytes(UTF_8));
            }

            if(StringUtils.isNotBlank(getObjectResult.getMessage())) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(getObjectResult.getMessage().getBytes(UTF_8));
            }

            com.chua.common.support.media.MediaType mediaType1 = getObjectResult.getMediaType();
            String type = mediaType1.type();
            MediaType mediaType = MediaType.valueOf(mediaType1.toString());
            if(type.contains("*")) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode( getObjectResult.getMetadata().getFilename(), UTF_8) + "\"")
                        .contentType(mediaType)
                        .body(IoUtils.toByteArray(getObjectResult.getInputStream()));
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(IoUtils.toByteArray(getObjectResult.getInputStream()));
        } catch (Exception e) {
            log.error("文件解析失败", e);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body("文件不存在".getBytes(UTF_8));
        }
    }
    /**
     * 下载
     *
     * @param bucket 水桶
     * @param url    url
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("{bucket}/download/**")
    public ResponseEntity<byte[]> download(@PathVariable("bucket") String bucket,
                                           @PathVariable(required = false)String url,
                                           @RequestParam(value = "format", required = false) String format,
                                           HttpServletRequest request) {
        url =  StringUtils.isEmpty(url) ?  StringUtils.after(request.getRequestURI(), "download", 1): url;
        if(StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }
        FileStorage fileStorage = fileStorageService.get(bucket);
        File file = null;
        try {
            GetObjectResult getObjectResult = fileStorage.getObject(GetObjectRequest.builder()
                    .filePath(FileUtils.getPath(url))
                    .fileName(FileUtils.getName(url))
                    .build());
            if(null == getObjectResult) {
                throw new RuntimeException("文件下载失败");
            }

            if(StringUtils.isNotBlank(getObjectResult.getMessage())) {
                throw new RuntimeException(getObjectResult.getMessage());
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +URLEncoder.encode( getObjectResult.getMetadata().getFilename(), UTF_8)+ "\"")
                    .body(IoUtils.toByteArray(getObjectResult.getInputStream()));
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
    public ReturnResult<PutObjectResult> upload(@RequestParam("file") MultipartFile multipartFile,
                                                @PathVariable("bucket") String bucket,
                                                @RequestParam(value = "path", required = false) String path) {
        if(StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            return ReturnResult.illegal("bucket不存在");
        }

        if(StringUtils.isNotBlank(path)) {
            path = URLDecoder.decode(path, UTF_8);
        }

        FileStorage fileStorage = fileStorageService.get(bucket);
        File file = null;
        try {
            file = MultipartFileUtils.toFile(multipartFile);
            PutObjectResult putResult = fileStorage.putObject(PutObjectRequest.builder()
                            .byteSource(new ByteSourceFile(file))
                            .filePath(path)
                    .build()
            );
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

        Map<String, FileStorage> beansOfType1 = applicationContext.getBeansOfType(FileStorage.class);
        for (Map.Entry<String, FileStorage> entry : beansOfType1.entrySet()) {
            fileStorageService.register(entry.getKey(), entry.getValue());
        }

        List<FileStorageProperties.FileStorageConfig> config = fileStorageProperties.getConfig();
        if(CollectionUtils.isEmpty(config)) {
            return;
        }
        for (FileStorageProperties.FileStorageConfig fileStorageConfig : config) {
            FileStorage fileStorage = FileStorage.createStorage(fileStorageConfig.getImpl(), BucketSetting.builder().build());
            if(null == fileStorage) {
                continue;
            }
            fileStorageService.register(fileStorageConfig.getBucket(), fileStorage);
        }
    }
}
