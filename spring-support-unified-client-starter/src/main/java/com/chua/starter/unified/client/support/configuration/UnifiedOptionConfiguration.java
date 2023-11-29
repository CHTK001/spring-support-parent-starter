package com.chua.starter.unified.client.support.configuration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.lang.code.ReturnResultCode;
import com.chua.common.support.lang.compile.Decompiler;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.ProtocolServer;
import com.chua.common.support.protocol.server.annotations.ServiceMapping;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.cfr.CfrDecompiler;
import com.chua.starter.unified.client.support.patch.PatchResolver;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * OSHI配置
 *
 * @author CH
 */
@Slf4j
public class UnifiedOptionConfiguration implements ApplicationContextAware {

    ProtocolServer protocolServer;

    @Resource
    private UnifiedClientProperties unifiedClientProperties;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            protocolServer = applicationContext.getBean(ProtocolServer.class);
            protocolServer.addMapping(this);
        } catch (BeansException ignored) {
        }
    }


    /**
     * 获取当前服务端配置
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("oshi")
    public BootResponse oshi(BootRequest request ) {
        return BootResponse.ok();
    }

    /**
     * 获取当前服务端配置
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("cfr")
    public BootResponse cfr(BootRequest request) {
        String content = request.getContent();
        if(StringUtils.isEmpty(content)) {
            return BootResponse.empty();
        }

        Class<?> aClass = ClassUtils.forName(content);
        if(null == aClass) {
            return BootResponse.empty();
        }

        log.info("检测到反编译类文件");
        URL location = aClass.getProtectionDomain().getCodeSource().getLocation();
        Decompiler decompiler = new CfrDecompiler();
        File file1 = new File("cfr", System.nanoTime() + ".java");
        try (InputStream is = new URL(location.toExternalForm() + aClass.getTypeName().replace(".", "/") + ".class").openStream()) {
            FileUtils.copyFile(is, file1);
            String decompile = decompiler.decompile(file1.getAbsolutePath(), null, false, true);
            return BootResponse.builder()
                    .code(ReturnResultCode.OK.getCode())
                        .data(BootResponse.DataDTO.builder().content( decompile.replace("Decompiled with CFR.", "反编译仅供参考")).build())
                    .build();
        } catch (IOException e) {
            try (InputStream is = new URL("jar:" + location.toExternalForm() + "!/" + aClass.getTypeName().replace(".", "/") + ".class").openStream()) {
                FileUtils.copyFile(is, file1);
                String decompile = decompiler.decompile(file1.getAbsolutePath(), null, false, true);
                return  BootResponse.builder()
                        .code(ReturnResultCode.OK.getCode())
                        .data(BootResponse.DataDTO.builder().content(decompile.replace("Decompiled with CFR.", "反编译仅供参考")).build())
                        .build();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                FileUtils.forceDelete(file1);
            } catch (IOException ignored) {
            }
        }
        return BootResponse.ok();
    }

    /**
     * 补丁
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("patch")
    public BootResponse patch(BootRequest request ) {
        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }
        JSONObject jsonObject = JSON.parseObject(content);
        String patchFile = jsonObject.getString("patchFile");
        String patchFileName = jsonObject.getString("patchFileName");
        if(StringUtils.isBlank(patchFile) || StringUtils.isBlank(patchFileName)) {
            return BootResponse.empty();
        }

        PatchResolver patchResolver = new PatchResolver(patchFileName, unifiedClientProperties);
        patchResolver.resolve(patchFile);
        return BootResponse.ok();
    }

}
