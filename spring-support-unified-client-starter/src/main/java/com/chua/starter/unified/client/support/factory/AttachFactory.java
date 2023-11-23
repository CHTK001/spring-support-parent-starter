package com.chua.starter.unified.client.support.factory;

import com.chua.common.support.constant.Projects;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.configuration.UnifiedClientConfiguration;
import com.chua.starter.unified.client.support.options.TransPointConfig;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static com.chua.common.support.http.HttpConstant.HTTP;

/**
 * 连接工厂
 *
 * @author CH
 */
@Slf4j
public class AttachFactory implements InitializingAware {
    private final TransPointConfig transPointConfig;
    private final String appName;
    private final String port;

    public AttachFactory(TransPointConfig transPointConfig, String appName, String port) {
        this.transPointConfig = transPointConfig;
        this.appName = appName;
        this.port = port;
    }

    @Override
    public void afterPropertiesSet() {
        try (InputStream inputStream = getAgent(transPointConfig)){
            byte[] bytes = IoUtils.toByteArray(inputStream);
            String newMd5 = DigestUtils.md5Hex(bytes);
            File file = new File(Projects.userHome(), getFileName(transPointConfig));
            checkAttach(file, newMd5);
            injectAttach(file, bytes, transPointConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void injectAttach(File file, byte[] bytes, TransPointConfig transPointConfig) {
        if(!file.exists()) {
            try {
                FileUtils.write(bytes, file);
            } catch (IOException ignored) {
            }
        }

        String pid = Projects.getPid();
        VirtualMachine virtualMachine = null;
        try {
            virtualMachine = VirtualMachine.attach(pid);
            transPointConfig.setAppName(appName);
            transPointConfig.setPort(port);
            virtualMachine.loadAgent(transPointConfig.getPath(), Json.toJson(transPointConfig));
        } catch (AttachNotSupportedException | IOException | AgentLoadException | AgentInitializationException e) {
            throw new RuntimeException(e);
        } finally {
            if(null != virtualMachine) {
                try {
                    virtualMachine.detach();
                } catch (IOException ignored) {
                }
            }
        }
    }


    private void checkAttach(File file, String newMd5) {
        if(!file.exists()) {
            return;
        }
        String oldMds = getMd5Hex(file);
        if(oldMds.equals(newMd5)) {
            return;
        }
        try {
            FileUtils.delete(file);
            log.info("开始升级Attach组件");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getMd5Hex(File file) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            return DigestUtils.md5Hex(fileInputStream);
        } catch (IOException e) {
            e.printStackTrace();;
        }

        return "";
    }

    /**
     * 获取文件名
     *
     * @param transPointConfig 转换点配置
     * @return {@link String}
     */
    private String getFileName(TransPointConfig transPointConfig) {
        if(null == transPointConfig || StringUtils.isBlank(transPointConfig.getPath())) {
            return "utils-support-attach-starter.jar";
        }

        return FileUtils.getName(transPointConfig.getPath());
    }

    /**
     * 获取代理
     *
     * @param transPointConfig 转换点配置
     * @return {@link InputStream}
     * @throws IOException IOException
     */
    private InputStream getAgent(TransPointConfig transPointConfig) throws IOException {
        if(null == transPointConfig || StringUtils.isBlank(transPointConfig.getPath())) {
            return UnifiedClientConfiguration.class.getResourceAsStream("/agent/utils-support-attach-starter.jar");
        }

        String path = transPointConfig.getPath();
        if(path.startsWith(HTTP)) {
            return new URL(path).openStream();
        }

        FileSystemResource fileSystemResource = new FileSystemResource(path);
        return fileSystemResource.getInputStream();
    }
}
