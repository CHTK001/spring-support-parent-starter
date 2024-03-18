package com.chua.starter.common.support.filestorage;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.file.meta.PathMetadata;
import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.oss.entity.GetResult;
import com.chua.common.support.oss.entity.PutResult;
import com.chua.common.support.oss.options.FileStorageOption;
import com.chua.common.support.oss.view.EmptyViewer;
import com.chua.common.support.oss.view.ViewResult;
import com.chua.common.support.oss.view.Viewer;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
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

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
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
    public ReturnPageResult<PathMetadata> preview(@PathVariable("bucket") String bucket,
                                                  @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                  @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
                                                  @RequestParam(value = "path", required = false) String path,
                                                  HttpServletRequest request) {
        if (StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            throw new RuntimeException("bucket不存在");
        }

        if(StringUtils.isNotBlank(path)) {
            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        FileStorage fileStorage = fileStorageService.get(bucket);
        return fileStorage.list(path, page, pageSize);
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
        File file = null;
        GetResult getResult1 = GetResult.builder().name(url).build();
        String type = getResult1.getMediaType().type();
        ViewResult viewResult = ServiceProvider.of(Viewer.class).getNewExtension(type).resolve(getResult1);

        if(null != viewResult.getContent()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(viewResult.getMediaType().toString()))
                    .body(viewResult.getContent());
        }
        try {
            GetResult getResult = fileStorage.getObject(url);
            if(null == getResult) {
                log.error("文件解析失败");
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(EmptyViewer.getInstance().resolve(getResult1).getContent());
            }

            if(StringUtils.isNotBlank(getResult.getMessage())) {
                throw new RuntimeException(getResult.getMessage());
            }

            type = getResult.getMediaType().type();
            getResult = fileStorageService.format(format, getResult);
            viewResult = ServiceProvider.of(Viewer.class).getNewExtension(type).resolve(getResult);
            MediaType mediaType = MediaType.valueOf(viewResult.getMediaType().toString());
            if(type.contains("*")) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode( getResult.getName(), "UTF-8") + "\"")
                        .contentType(mediaType)
                        .body(viewResult.getContent());
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(viewResult.getContent());
        } catch (Exception e) {
            log.error("文件解析失败", e);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(EmptyViewer.getInstance().resolve(getResult1).getContent());
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
            GetResult getResult = fileStorage.getObject(url);
            if(null == getResult) {
                throw new RuntimeException("文件下载失败");
            }

            if(StringUtils.isNotBlank(getResult.getMessage())) {
                throw new RuntimeException(getResult.getMessage());
            }

            getResult = fileStorageService.format(format, getResult);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +URLEncoder.encode( getResult.getName(), "UTF-8")+ "\"")
                    .body(getResult.getBytes());
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
    public ReturnResult<PutResult> upload(@RequestParam("file") MultipartFile multipartFile,
                                          @PathVariable("bucket") String bucket,
                                          @RequestParam(value = "path", required = false) String path) {
        if(StringUtils.isEmpty(bucket) || !fileStorageService.containsKey(bucket)) {
            return ReturnResult.illegal("bucket不存在");
        }

        if(StringUtils.isNotBlank(path)) {
            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
        }

        FileStorage fileStorage = fileStorageService.get(bucket);
        File file = null;
        try {
            file = MultipartFileUtils.toFile(multipartFile);
            PutResult putResult = fileStorage.putObject(path, file);
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
        if(CollectionUtils.isEmpty(config)) {
            return;
        }
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
