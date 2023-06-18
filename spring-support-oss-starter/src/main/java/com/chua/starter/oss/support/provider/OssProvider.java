package com.chua.starter.oss.support.provider;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.function.strategy.name.OssNamedStrategy;
import com.chua.common.support.function.strategy.name.RejectStrategy;
import com.chua.common.support.image.filter.ImageFilter;
import com.chua.common.support.oss.adaptor.OssResolver;
import com.chua.common.support.pojo.Mode;
import com.chua.common.support.spi.Option;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.common.support.view.ResponseHandler;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.oss.support.pojo.OssSystem;
import com.chua.starter.oss.support.service.OssSystemService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

import static com.chua.starter.common.support.result.ReturnCode.PARAM_ERROR;

/**
 * oss
 *
 * @author CH
 */
@Controller
@RequestMapping("/oss")
public class OssProvider {

    @Resource
    private OssSystemService ossSystemService;
    private static final String DOWNLOAD = "download";

    /**
     * 文件上传
     *
     * @param ossBucket bucket
     * @param files     文件
     * @return -1: bucket不存在
     */
    @ResponseBody
    @PostMapping("/upload")
    public Result<Boolean> saveOss(String ossBucket, @RequestParam(value = "parentPath", defaultValue = "") String parentPath, MultipartFile[] files) {
        if (null == ossBucket) {
            return Result.failed("bucket不存在");
        }
        OssSystem ossSystem = ossSystemService.getSystemByBucket(ossBucket);
        if (null == ossSystem) {
            return Result.failed("bucket不存在");
        }
        OssResolver ossResolver = ServiceProvider.of(OssResolver.class).getNewExtension(ossSystem.getOssType());
        if (null == ossResolver) {
            return Result.failed("bucket不存在");
        }
        try {
            for (MultipartFile file : files) {
                try (InputStream inputStream = file.getInputStream()) {
                    ossResolver.storage(parentPath, inputStream,
                            BeanUtils.copyProperties(ossSystem, com.chua.common.support.pojo.OssSystem.class),
                            file.getOriginalFilename());
                }
            }
            return Result.success();
        } catch (Exception e) {
            return Result.failed(e.getLocalizedMessage());
        }
    }

    /**
     * 文件预览
     *
     * @param path    文件路径
     * @param mode    预览模式
     * @param request 响应
     */
    @GetMapping(value = "/preview")
    public ResponseEntity<byte[]> preview(@RequestParam("bucket") String bucket,
                                          @RequestParam("path") String path,
                                          @RequestParam(value = "mode", required = false) String mode,
                                          HttpServletRequest request) throws IOException {
        return preview(bucket, path, mode, request);
    }

    /**
     * 预览
     *
     * @return 预览
     */
    @GetMapping(value = "/preview/{bucket}/{path}/**")
    public ResponseEntity<byte[]> preview(@PathVariable("bucket") String bucket,
                                          @PathVariable("path") String path,
                                          @RequestParam(value = "mode", required = false, defaultValue = "PREVIEW") Mode mode,
                                          HttpServletRequest request) {
        if (null == mode && DOWNLOAD.equalsIgnoreCase(request.getQueryString())) {
            mode = Mode.DOWNLOAD;
        }

        String uri = request.getRequestURI();
        path = uri.substring(uri.indexOf(bucket) + bucket.length());
        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        OssSystem ossSystem = ossSystemService.getSystemByBucket(bucket);
        if (null == ossSystem) {
            try {
                return ResponseEntity.ok()
                        .contentType(new MediaType("image", "webp"))
                        .body(IoUtils.toByteArray(OssProvider.class.getResource("/404.webp")));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        OssResolver ossResolver = ServiceProvider.of(OssResolver.class).getNewExtension(ossSystem.getOssType());
        if (null == ossResolver) {
            return ResponseEntity.notFound().build();
        }

        ResponseHandler<byte[]> handler;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ossResolver.preview(BeanUtils.copyProperties(ossSystem, com.chua.common.support.pojo.OssSystem.class), path, mode, null, outputStream);

            handler = ResponseHandler.ok();
            if (mode == Mode.DOWNLOAD) {
                try {
                    handler.header("Content-Disposition", "attachment;filename=" + URLEncoder.encode(path, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(path).orElse(MediaType.ALL))
                    .body(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @ResponseBody
    @GetMapping("options")
    public Result<List<Option<String>>> option(@RequestParam(value = "type", defaultValue = "0") Integer type) {
        if (0 == type) {
            //实现方式
            return Result.success(ServiceProvider.of(OssResolver.class).options());
        }
        if (1 == type) {
            //命名策略
            return Result.success(ServiceProvider.of(OssNamedStrategy.class).options());
        }
        if (2 == type) {
            //拒绝策略
            return Result.success(ServiceProvider.of(RejectStrategy.class).options());
        }

        if (3 == type) {
            //插件
            return Result.success(ServiceProvider.of(ImageFilter.class).options());
        }

        return Result.failed();
    }

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    @ResponseBody
    public ResultData<Page<OssSystem>> page(DelegatePage<OssSystem> page, @Valid OssSystem entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(ossSystemService.page(page.createPage(), Wrappers.lambdaQuery(entity)));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @GetMapping("delete/{id}")
    @CacheEvict(cacheNames = "oss", allEntries = true)
    public ResultData<Boolean> delete(@PathVariable("id") String id) {
        if (null == id) {
            return ResultData.failure(PARAM_ERROR, "主键不能为空");
        }
        return ResultData.success(ossSystemService.removeById(id));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    @ResponseBody
    @CacheEvict(cacheNames = "oss", allEntries = true)
    public ResultData<Boolean> updateById(@Valid @RequestBody OssSystem t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        if (ossSystemService.count(Wrappers.<OssSystem>lambdaQuery()
                .eq(OssSystem::getOssBucket, t.getOssBucket())
                .ne(OssSystem::getOssId, t.getOssId())
        ) > 0L) {
            return ResultData.failure(PARAM_ERROR, "bucket已存在");
        }
        return ResultData.success(ossSystemService.updateById(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    @ResponseBody
    @CacheEvict(cacheNames = "oss", allEntries = true)
    public ResultData<Boolean> save(@Valid @RequestBody OssSystem t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if (t.getOssId() != null) {
            if (ossSystemService.count(Wrappers.<OssSystem>lambdaQuery()
                    .eq(OssSystem::getOssBucket, t.getOssBucket())
                    .ne(OssSystem::getOssId, t.getOssId())
            ) > 0L) {
                return ResultData.failure(PARAM_ERROR, "bucket已存在");
            }
        } else {
            if (ossSystemService.count(Wrappers.<OssSystem>lambdaQuery().eq(OssSystem::getOssBucket, t.getOssBucket())) > 0L) {
                return ResultData.failure(PARAM_ERROR, "bucket已存在");
            }
        }

        return ResultData.success(ossSystemService.saveOrUpdate(t));
    }
}
