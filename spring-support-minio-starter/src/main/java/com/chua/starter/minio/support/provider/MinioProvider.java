package com.chua.starter.minio.support.provider;

import com.chua.starter.minio.support.dto.MultipartFileParam;
import com.chua.starter.minio.support.template.MinioTemplate;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * restful
 *
 * @author CH
 */
@RestController
@RequestMapping("/minio")
public class MinioProvider {
    private final MinioTemplate minioTemplate;

    @Resource
    private ApplicationContext applicationContext;
    public MinioProvider(MinioTemplate minioTemplate) {
        this.minioTemplate = minioTemplate;
    }
    /**
     * 创建任务
     *
     * @return 结果
     */
    @PostMapping("/createTaskId/{type}")
    public String createTaskId(@PathVariable(value = "type", required = false) String type, HttpServletRequest request, HttpServletResponse response) {
        String newType = applicationContext.getBeansOfType()
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if(!isMultipart) {
            System.out.println("不支持的表单格式");
            response.setStatus(404);
            return "不支持的表单格式";
        }

        return "";

    }

    /**
     * 上传文件
     *
     * @return 结果
     */
    @PostMapping("/upload")
    public String upload(MultipartFileParam param, HttpServletRequest request, HttpServletResponse response) {
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if(!isMultipart) {
            System.out.println("不支持的表单格式");
            response.setStatus(404);
            return "不支持的表单格式";
        }
        return "";
    }
}
