package com.chua.starter.vuesql.support.channel;

import com.chua.common.support.utils.FileUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;

/**
 * 表
 *
 * @author CH
 */
@Component("sqlite")
public class SqliteTableChannel extends MysqlTableChannel {

    @Resource
    private ChannelFactory channelFactory;
    private static final String SQLITE_PATH;

    static {
        SQLITE_PATH = TableChannel.create("/sqlite");

    }

    @Override
    public String createUrl(WebsqlConfig config) {
        return "jdbc:sqlite:" + config.getConfigUrl();
    }

    @Override
    public String check(WebsqlConfig websqlConfig, MultipartFile file) {
        if(null == file) {
            return "sqlite当前支支持文件上传,且文件不能为空";
        }
        File file1 = new File(SQLITE_PATH, websqlConfig.getConfigName() + ".db");
        try {
            FileUtils.copyFile(file.getInputStream(), file1);
            websqlConfig.setConfigUrl(file1.getAbsolutePath());
            websqlConfig.setConfigFile(file1.getAbsolutePath());
        } catch (IOException e) {
            return "sqlite数据库上传失败请重试";
        }

        return null;
    }
}
