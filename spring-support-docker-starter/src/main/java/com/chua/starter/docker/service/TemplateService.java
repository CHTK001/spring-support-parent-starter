package com.chua.starter.docker.service;

import com.chua.starter.docker.DockerCreateRequest;
import com.chua.starter.docker.model.DockerTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 容器模板服务 —— JSON 文件持久化 + CRUD + 创建请求回填。
 *
 * <p>持久化文件 {@code ~/.monitor/docker/templates.json}，写入规则与镜像库一致
 * （UTF-8 无 BOM、临时文件 + move、文件锁串行化）。模板只保存受控的 run 参数，
 * 不允许保存任意 shell 片段。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Service
public class TemplateService {

    /**
     * JSON 读写器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 模板持久化
     */
    private final JsonFileStore store;

    /**
     * 构造服务
     */
    public TemplateService() {
        this.store = new JsonFileStore(Paths.get(System.getProperty("user.home"),
                ".monitor", "docker", "templates.json"), objectMapper);
    }

    /**
     * 模板列表（按更新时间倒序）
     *
     * @return 模板列表
     */
    public List<DockerTemplate> list() {
        List<DockerTemplate> templates = store.readList(DockerTemplate.class);
        templates.sort(Comparator.comparing((DockerTemplate t) -> t.getUpdatedAt() == null ? "" : t.getUpdatedAt())
                .reversed());
        return templates;
    }

    /**
     * 新增或更新模板。
     *
     * @param template 模板（id 为空表示新增）
     * @return 保存后的模板
     */
    public DockerTemplate save(DockerTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("模板不能为空");
        }
        if (template.getImage() == null || template.getImage().isBlank()) {
            throw new IllegalArgumentException("模板镜像不能为空");
        }
        store.lock.lock();
        try {
            List<DockerTemplate> templates = store.readList(DockerTemplate.class);
            String now = Instant.now().toString();
            DockerTemplate target = null;
            if (template.getId() != null && !template.getId().isBlank()) {
                for (DockerTemplate item : templates) {
                    if (template.getId().equals(item.getId())) {
                        target = item;
                        break;
                    }
                }
            }
            if (target == null) {
                target = new DockerTemplate();
                target.setId(template.getId() == null || template.getId().isBlank()
                        ? UUID.randomUUID().toString() : template.getId());
                target.setCreatedAt(now);
                templates.add(target);
            }
            if (template.getName() == null || template.getName().isBlank()) {
                template.setName(template.getImage().trim());
            }
            target.setName(template.getName().trim());
            target.setDescription(template.getDescription());
            target.setImage(template.getImage().trim());
            target.setRegistryId(template.getRegistryId());
            target.setCommand(template.getCommand());
            target.setPorts(template.getPorts());
            target.setEnv(template.getEnv());
            target.setVolumes(template.getVolumes());
            target.setNetwork(template.getNetwork());
            target.setRestartPolicy(template.getRestartPolicy());
            target.setPull(template.getPull());
            target.setUpdatedAt(now);
            store.write(templates);
            return target;
        } finally {
            store.lock.unlock();
        }
    }

    /**
     * 删除模板
     *
     * @param id 模板标识
     * @return 是否删除
     */
    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("模板标识不能为空");
        }
        store.lock.lock();
        try {
            List<DockerTemplate> templates = store.readList(DockerTemplate.class);
            boolean removed = templates.removeIf(item -> id.equals(item.getId()));
            if (removed) {
                store.write(templates);
            }
            return removed;
        } finally {
            store.lock.unlock();
        }
    }

    /**
     * 按标识取模板
     *
     * @param id 模板标识
     * @return 模板，不存在返回 null
     */
    public DockerTemplate get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (DockerTemplate template : store.readList(DockerTemplate.class)) {
            if (id.equals(template.getId())) {
                return template;
            }
        }
        return null;
    }

    /**
     * 用模板回填创建请求里未传的字段（显式传入的字段优先）。
     *
     * @param request 创建请求（必须带 templateId）
     */
    public void fill(DockerCreateRequest request) {
        if (request == null || request.getTemplateId() == null || request.getTemplateId().isBlank()) {
            return;
        }
        DockerTemplate template = get(request.getTemplateId().trim());
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + request.getTemplateId());
        }
        if (isBlank(request.getImage())) {
            request.setImage(template.getImage());
        }
        if (isBlank(request.getRegistryId())) {
            request.setRegistryId(template.getRegistryId());
        }
        if (isBlank(request.getCommand())) {
            request.setCommand(template.getCommand());
        }
        if (isEmpty(request.getPorts())) {
            request.setPorts(copy(template.getPorts()));
        }
        if (isEmpty(request.getEnv())) {
            request.setEnv(copy(template.getEnv()));
        }
        if (isEmpty(request.getVolumes())) {
            request.setVolumes(copy(template.getVolumes()));
        }
        if (isBlank(request.getNetwork())) {
            request.setNetwork(template.getNetwork());
        }
        if (isBlank(request.getRestartPolicy())) {
            request.setRestartPolicy(template.getRestartPolicy());
        }
        if (request.getPull() == null) {
            request.setPull(template.getPull());
        }
        log.debug("[docker] 已应用模板 {} 回填创建请求", template.getId());
    }

    /**
     * 列表判空
     *
     * @param value 列表
     * @return 是否为空
     */
    private boolean isEmpty(List<String> value) {
        return value == null || value.isEmpty();
    }

    /**
     * 复制列表（避免模板与请求共享可变引用）
     *
     * @param value 原列表
     * @return 副本
     */
    private List<String> copy(List<String> value) {
        return value == null ? null : new ArrayList<>(value);
    }

    /**
     * 空白判断
     *
     * @param value 原值
     * @return 是否空白
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
