package com.chua.starter.mybatis.endpoint;

import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.reloader.FileInfo;
import com.chua.starter.mybatis.reloader.Reload;
import org.apache.ibatis.session.Configuration;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis扩展端点
 * 提供热重载、文件列表等功能
 *
 * @author CH
 */
@WebEndpoint(id = "mybatis-extension")
public class MybatisEndpoint {

    private final Reload reload;
    private final Configuration configuration;
    private final SupportInjector supportInjector;

    public MybatisEndpoint(Reload reload, Configuration configuration, SupportInjector supportInjector) {
        this.reload = reload;
        this.configuration = configuration;
        this.supportInjector = supportInjector;
    }

    /**
     * 热重载指定的Mapper文件
     *
     * @param name Mapper XML文件名或路径
     * @param type 类型: name（Mapper方法名）, mapper（Mapper XML文件名）
     * @return 重载结果信息
     */
    @WriteOperation
    public String reload(@Selector String name, @Selector String type) {
        if (reload == null) {
            return "加载器不存在";
        }

        if ("name".equalsIgnoreCase(type)) {
            if (supportInjector == null) {
                return "mapper注入器不存在";
            }
            return this.supportInjector.refresh(name);
        }
        
        // 支持mapper类型，直接重载文件
        return this.reload.reload(name);
    }

    /**
     * 获取MyBatis扩展端点信息（根路径）
     * 包含MappedStatement列表和可重载文件列表
     *
     * @return 端点信息
     */
    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        
        // MappedStatement信息
        Collection<String> statements = configuration.getMappedStatementNames();
        result.put("statements", statements);
        result.put("statementCount", statements.size());
        
        // 文件信息
        if (reload != null) {
            List<FileInfo> files = reload.listFiles();
            result.put("files", files);
            result.put("fileCount", files.size());
            result.put("watchableCount", files.stream().mapToLong(f -> f.isWatchable() ? 1 : 0).sum());
        } else {
            result.put("files", List.of());
            result.put("fileCount", 0);
            result.put("watchableCount", 0);
            result.put("reloadEnabled", false);
        }
        
        return result;
    }
}
