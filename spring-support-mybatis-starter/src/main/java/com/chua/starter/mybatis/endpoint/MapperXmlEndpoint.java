package com.chua.starter.mybatis.endpoint;

import com.chua.starter.mybatis.reloader.FileInfo;
import com.chua.starter.mybatis.reloader.Reload;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 映射器 XML 编辑器端点
 * <p>
 * 提供嵌入式 映射器 XML 查看、编辑、保存和热重载功能。
 * 仅在 映射器-reload Bean 存在时生效（即 plugin.MyBatis.打开-xml-reload=true）。
 * </p>
 * <p>
 * Actuator API 路径：<br/>
 * 获取  /actuator/MyBatis-xml/文件     — 列出所有 映射器 XML<br/>
 * 获取  /actuator/MyBatis-xml/内容/{名称} — 读取 XML 内容<br/>
 * POST /actuator/MyBatis-xml/保存/{名称}   — 保存并热重载<br/>
 * POST /actuator/MyBatis-xml/reload/{名称} — 仅触发热重载<br/>
 * <br/>
 * Web 编辑器路径：<br/>
 * 获取  /MyBatis/xml/editor            — 嵌入式编辑器页面
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@WebEndpoint(id = "mybatis-xml")
@ConditionalOnBean(name = "mapper-reload")
public class MapperXmlEndpoint {

    private final Reload reload; // reload

    /**
     * 映射器xml端点。
     * @param reload reload
     */
    public MapperXmlEndpoint(Reload reload) {
        this.reload = reload;
    }

    /**
     * 列出所有已加载的 映射器 XML 文件信息
     * @return 文件的结果
     */
    @ReadOperation
    public List<FileInfo> files() {
        return reload.listFiles();
    }

    /**
     * 读取指定 XML 文件的原始内容
     *
     * @param name 文件名（如 用户映射器.xml）
     * @return 内容的结果
     */
    @ReadOperation
    public ResponseEntity<Map<String, Object>> content(@Selector String name) {
        if (StringUtils.isBlank(name)) {
            return ResponseEntity.badRequest().body(Map.of("error", "name 不能为空"));
        }
        List<FileInfo> files = reload.listFiles();
        FileInfo target = files.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            String content = readFileContent(name);
            return ResponseEntity.ok(Map.of(
                    "name", name,
                    "path", target.path(),
                    "type", target.type(),
                    "watchable", target.watchable(),
                    "content", content
            ));
        } catch (IOException e) {
            log.error("读取 XML 内容失败: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 保存 XML 内容到本地文件并触发热重载
     * <p>仅对可写本地文件生效；JAR 内资源无法保存。</p>
     *
     * @param name 文件名
     */
    @WriteOperation
    public ResponseEntity<Map<String, Object>> save(@Selector String name,
                                                     @RequestBody(required = false) Map<String, String> body) {
        if (StringUtils.isBlank(name)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "name 不能为空"));
        }
        String content = body != null ? body.get("content") : null;
        if (StringUtils.isBlank(content)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "content 不能为空"));
        }

        List<FileInfo> files = reload.listFiles();
        FileInfo target = files.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"FILE".equals(target.type())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "message", "该文件不可写（非本地文件系统资源）：" + target.path()
            ));
        }

        try {
            Path path = Path.of(target.path());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            String result = reload.reload(name);
            boolean ok = result.contains("成功");
            return ResponseEntity.ok(Map.of(
                    "ok", ok,
                    "message", result,
                    "path", path.toAbsolutePath().toString()
            ));
        } catch (IOException e) {
            log.error("保存文件失败: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "保存失败: " + e.getMessage()));
        }
    }

    /**
     * 手动触发指定文件热重载（不修改内容）
     *
     * @param name 文件名
     * @return reload的结果
     */
    @WriteOperation
    public ResponseEntity<Map<String, Object>> reload(@Selector String name) {
        if (StringUtils.isBlank(name)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "name 不能为空"));
        }
        String result = reload.reload(name);
        boolean ok = result.contains("成功");
        return ResponseEntity.ok(Map.of("ok", ok, "message", result));
    }

    // -------------------------------------------------------------------------
 // 嵌入式编辑器页面（通过 /MyBatis/xml/editor 访问）
    // -------------------------------------------------------------------------

    /**
     * 返回嵌入式 XML 编辑器 HTML 页面
     * @return editorPage的结果
     */
    @GetMapping(path = "/mybatis/xml/editor", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> editorPage() {
        Resource resource = new ClassPathResource("static/mybatis/xml-editor.html");
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        try {
            String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(html);
        } catch (IOException e) {
            log.error("读取编辑器页面失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 读取文件内容。
     * @param filename 文件名
     * @return 读取文件内容的结果
     * @throws IOException 异常
    */
    private String readFileContent(String filename) throws IOException {
        Resource[] resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath*:**/" + filename);
        if (resources.length > 0 && resources[0].isReadable()) {
            /**
             * 字符串。
             * @param resources[0].getInputStream( resources[0].获取输入流(
             * @return 字符串的结果
             */
            return new String(resources[0].getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        /**
         * io异常。
         * @param filename 文件名
         * @return io异常的结果
         */
        throw new IOException("找不到资源: " + filename);
    }
}
