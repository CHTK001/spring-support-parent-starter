package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ONNX 外部服务器模型目录服务。
 *
 * <p>ONNX 供应商与其余供应商同构，模型目录同样是一份「配置」。这份配置与其它供应商的差别只有一处：
 * 它的值不是远端地址，而是本机目录，因此必须由文件系统能力来选取与校验，不能让调用方随手填一个路径。</p>
 *
 * <p>本服务即 ONNX 侧与后台文件系统接口的对接层：</p>
 * <ul>
 *   <li>{@link #browse(String)} 复用 {@code /api/fs/browse} 的目录遍历契约，
 *       前端 ReFileSystem 目录选择器据此选取模型目录；</li>
 *   <li>{@link #add(String, String)} / {@link #remove(String)} 按同一套解析规则校验并落库，
 *       保证「选择器里能看到的目录」与「能被注册进来的目录」是同一个集合。</li>
 * </ul>
 *
 * <p>目录归一化、绝对化、存在性与「必须是目录」的判定口径，与 {@code ApiFsController#browse} 一致，
 * 避免出现选择器能打开、注册却被拒的不一致。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnnxExternalDirectoryService {

    /**
     * 持久化分组：ONNX 外部目录清单所在行。
     */
    public static final String SETTING_GROUP = "onnx";

    /**
     * 持久化字段：外部目录清单（JSON 数组）。
     */
    public static final String SETTING_FIELD = "external_directories";

    /**
     * 持久化字段：当前选定的模型根目录。
     *
     * <p>单根语义下「选中目录即模型根」，但登记清单里可能有多个目录，
     * 启动时无法从清单推断用户选的是哪一个，因此单独持久化。</p>
     */
    public static final String SETTING_FIELD_MODEL_ROOT = "model_root";

    /**
     * 禁入的目录名（小写比较，匹配任意层级）。
     *
     * <p>目录选择器与登记接口共用：这些目录既不允许浏览，也不允许被登记为模型目录。</p>
     */
    private static final Set<String> DENIED_DIR_NAMES = Set.of(
            "proc", "sys", "dev", "boot", "run", "etc", "bin", "sbin", "lib", "lib32", "lib64",
            "root", "lost+found", "snap", "srv", "media", "mnt");

    /**
     * 禁入的目录名（隐藏目录，含凭据）。
     */
    private static final Set<String> DENIED_HIDDEN_NAMES = Set.of(
            ".ssh", ".gnupg", ".aws", ".kube", ".docker", ".config", ".local", ".cache", ".nomifun");

    /**
     * 系统敏感根目录：位于这些目录下的路径一律拒绝，无论目录名是否在黑名单里。
     */
    private static final List<String> DENIED_ABSOLUTE_PREFIXES = List.of("/etc", "/proc", "/sys", "/dev", "/boot", "/run");

    /**
     * 全局设置表操作。
     */
    private final SysAiGlobalSettingMapper globalSettingMapper;

    /**
     * 事件发布：通知本地模型目录清单的缓存持有方失效
     */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * JSON 序列化。
     */
    private final ObjectMapper objectMapper;

    /**
     * 浏览服务器目录，供 ONNX 模型目录选择器使用。
     *
     * <p>返回结构与后台文件系统接口 {@code GET /api/fs/browse} 完全一致
     * （{@code items/currentPath/canGoUp/parentPath/isRoot}），
     * 前端 ReFileSystem 组件可直接消费同一套解析逻辑。</p>
     *
     * @param path      目录绝对路径；为空时以当前用户主目录为起点
     * @param showFiles 是否返回文件（默认只返回子目录，目录选择器语义）
     * @return 目录条目与定位信息
     * @throws IOException 目录不存在或不可读
     */
    public Map<String, Object> browse(String path, boolean showFiles) throws IOException {
        Path root = modelRoot();
        Path dir = resolveBrowsable(path, root);
        List<Map<String, Object>> items = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> {
                        boolean isDir = Files.isDirectory(p);
                        if (isDir && isDenied(dir, p)) {
                            return;
                        }
                        if (!showFiles && !isDir) {
                            return;
                        }
                        Map<String, Object> item = new LinkedHashMap<>(6);
                        item.put("name", p.getFileName().toString());
                        item.put("path", p.toAbsolutePath().toString());
                        item.put("isDirectory", isDir);
                        item.put("isFile", !isDir);
                        items.add(item);
                    });
        }
        Path parent = dir.getParent();
        // 目录选择器钉在模型根下：到根即停，不允许再向上，否则会退化成整盘浏览
        boolean canGoUp = parent != null && !dir.equals(root) && dir.startsWith(root) && !isDenied(dir, parent);
        Map<String, Object> result = new LinkedHashMap<>(9);
        result.put("items", items);
        result.put("currentPath", dir.toAbsolutePath().toString());
        result.put("canGoUp", canGoUp);
        result.put("parentPath", canGoUp ? parent.toAbsolutePath().toString() : null);
        result.put("isRoot", dir.equals(root));
        result.put("modelRoot", root.toString());
        return result;
    }

    /**
     * 登记一个外部模型目录。
     *
     * @param rawDir    目录路径（选择器给出的绝对路径）
     * @param rawTemplate 模板模型标识，可为空表示按目录名自动归类
     * @return 登记结果：{@code dir} 为归一化后的目录，{@code model} 为注册表中的模型键
     * @throws IllegalArgumentException 目录不存在或模板模型未注册
     */
    public Map<String, Object> add(String rawDir, String rawTemplate) {
        ModelRegistry.discoverAll();
        String dir = requireBrowsableDirectory(rawDir);
        String template = rawTemplate == null ? "" : rawTemplate.trim();
        if (!template.isEmpty() && !ModelRegistry.isRegistered(template)) {
            throw new IllegalArgumentException("模板模型未注册: " + template);
        }
        String tpl = template.isEmpty() ? null : template;
        String key = ModelRegistry.registerExternalDirectory(dir, tpl);
        // 单根语义：模型根取「选中目录」或其父目录。若选中的是某模型的专属
        // 目录（每模型一目录布局 <根>/<modelId>/model.onnx），把该目录设为根
        // 会让其余模型全部按「<该目录>/<modelId>.onnx」解析而找不到文件
        // （实测 2026-10-11 cn-clip-rn50-image 等全库模型因此报「DJL 图像转换失败」），
        // 命中该形态时上溯一级取父目录。外部目录仍登记用户选中的目录本身。
        String modelRoot = resolveModelRoot(dir);
        ModelRegistry.setModelRootDir(modelRoot);
        writeModelRoot(modelRoot);
        publishDirsChanged();

        List<Map<String, String>> entries = readEntries();
        if (entries.stream().noneMatch(x -> dir.equals(x.get("dir")))) {
            Map<String, String> entry = new LinkedHashMap<>(2);
            entry.put("dir", dir);
            entry.put("template", tpl);
            entries.add(entry);
            writeEntries(entries);
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("model", key);
        result.put("dir", dir);
        result.put("template", tpl);
        return result;
    }

    /**
     * 推算选中目录对应的模型根。
     *
     * <p>「每模型一目录」布局下，用户在目录选择器里点中的往往是某个具体模型的
     * 专属目录（如 {@code .../onnx/arc-face}，内含 {@code model.onnx}）。该目录
     * 本身不是模型根——把它设为根会让其余模型全部按
     * {@code <该目录>/<modelId>.onnx} 解析而找不到文件。命中该形态时上溯一级取
     * 父目录作为模型根；否则模型根就是选中目录本身。</p>
     *
     * @param dir 选中的目录（已归一化绝对路径）
     * @return 模型根目录
     */
    private String resolveModelRoot(String dir) {
        Path p = Paths.get(dir).toAbsolutePath().normalize();
        String name = p.getFileName().toString();
        if (!name.isBlank() && ModelRegistry.isRegistered(name)
                && Files.exists(p.resolve("model.onnx"))) {
            Path parent = p.getParent();
            if (parent != null) {
                log.info("[ONNX] 选中目录是模型 '{}' 的专属目录，模型根上溯为父目录: {}",
                        name, parent);
                return parent.toString();
            }
        }
        return p.toString();
    }

    /**
     * 注销一个外部模型目录。
     *
     * @param rawDir 目录路径
     * @return 是否发生变更（注册表或持久化清单任一变化即为变更）
     */
    public boolean remove(String rawDir) {
        String dir = canonicalDir(rawDir);
        if (dir == null || dir.isBlank()) {
            return false;
        }
        boolean unregistered = ModelRegistry.unregisterExternal(dir);
        List<Map<String, String>> entries = readEntries();
        boolean changed = entries.removeIf(x -> dir.equals(x.get("dir")));
        if (changed) {
            writeEntries(entries);
        }
        if (unregistered || changed) {
            publishDirsChanged();
        }
        return unregistered || changed;
    }

    /**
     * 读取已登记的外部目录清单。
     *
     * @return 清单条目（每项含 {@code dir} 与 {@code template}）
     */
    public List<Map<String, String>> readEntries() {
        try {
            SysAiGlobalSetting row = findRow();
            String json = row == null ? null : row.getValue();
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            List<Map<String, String>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
            });
            if (list == null) {
                return new ArrayList<>();
            }
            for (Map<String, String> entry : list) {
                if (entry.get("dir") != null) {
                    entry.put("dir", canonicalDir(entry.get("dir")));
                }
            }
            return list;
        } catch (Exception ex) {
            log.warn("[ONNX] 读取外部目录清单失败: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 应用启动后把已登记的目录重新注册进模型注册表。
     *
     * @return 成功恢复的目录数
     */
    public int restoreAll() {
        try {
            ModelRegistry.discoverAll();
        } catch (Exception ex) {
            log.warn("[ONNX] 恢复外部目录失败（注册表发现异常）: {}", ex.getMessage());
            return 0;
        }
        int restored = 0;
        var restoredDirs = new ArrayList<String>();
        for (Map<String, String> entry : readEntries()) {
            String dir = entry.get("dir");
            String template = entry.get("template");
            boolean templateReady = template == null || template.isBlank() || ModelRegistry.isRegistered(template);
            if (ModelRegistry.isExistingDirectory(dir) && templateReady) {
                ModelRegistry.registerExternalDirectory(dir,
                        (template == null || template.isBlank()) ? null : template);
                restored++;
                restoredDirs.add(dir);
            } else {
                log.warn("[ONNX] 外部目录恢复跳过（目录不存在或模板未注册）: dir={}, template={}", dir, template);
            }
        }
        // 取持久化的模型根，而不是从清单里猜单个目录：清单通常有多个目录，
        // 猜第一个会把根指向某个具体模型目录，选择器随即只能看见那一个模型
        String configuredRoot = readModelRoot();
        if (configuredRoot.isBlank()) {
            configuredRoot = commonAncestor(restoredDirs);
            if (!configuredRoot.isBlank()) {
                writeModelRoot(configuredRoot);
            }
        }
        if (!configuredRoot.isBlank()) {
            ModelRegistry.setModelRootDir(configuredRoot);
        }
        // 必须回读打印：打本地变量无法证明赋值真的生效，
        // 此前就是因为只打变量，误以为根已切换而漏掉了被重置的问题
        log.info("[ONNX] 启动恢复外部目录 {} 个，期望模型根={} 实际模型根={}", restored,
                configuredRoot.isBlank() ? "<未配置>" : configuredRoot, ModelRegistry.getModelRootDir());
        publishDirsChanged();
        return restored;
    }

    /**
     * 发布外部目录变更事件，通知本地模型目录清单缓存失效。
     */
    private void publishDirsChanged() {
        try {
            eventPublisher.publishEvent(new OnnxModelDirsChangedEvent(readModelRoot()));
        } catch (Exception ex) {
            log.debug("[ONNX] 发布目录变更事件失败: {}", ex.getMessage());
        }
    }

    /**
     * 计算若干目录的最深公共祖先，用作未持久化时的模型根默认值。
     *
     * @param dirs 目录集合
     * @return 公共祖先；集合为空时返回空串
     */
    private String commonAncestor(List<String> dirs) {
        if (dirs == null || dirs.isEmpty()) {
            return "";
        }
        Path common = Paths.get(dirs.get(0)).toAbsolutePath().normalize();
        for (String dir : dirs) {
            Path other = Paths.get(dir).toAbsolutePath().normalize();
            while (common != null && !other.startsWith(common)) {
                common = common.getParent();
            }
            if (common == null) {
                return "";
            }
        }
        return common == null ? "" : common.toString();
    }

    /**
     * 把目录归一化为单分隔符的规范路径。
     *
     * @param raw 原始路径
     * @return 归一化路径；输入为空时返回原值
     */
    public static String canonicalDir(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            return Paths.get(raw.trim()).normalize().toString();
        } catch (Exception ex) {
            return raw.trim();
        }
    }

    /**
     * 把持久化的模型根推送到 {@link ModelRegistry}。
     *
     * <p>库侧的 {@code discoverAll()} 会把模型根重置为库默认值，因此凡是要按根解析
     * 相对模型路径的地方，用完 {@code discoverAll()} 都要重新推一次。
     * 未持久化模型根时不做任何事，保持库默认值。</p>
     */
    public void applyModelRoot() {
        String persisted = readModelRoot();
        if (persisted == null || persisted.isBlank()) {
            return;
        }
        try {
            if (!persisted.equals(ModelRegistry.getModelRootDir())) {
                ModelRegistry.setModelRootDir(persisted);
                log.debug("[ONNX] 模型根已推送到注册表: {}", persisted);
            }
        } catch (Exception ex) {
            log.debug("[ONNX] 推送模型根失败: {}", ex.getMessage());
        }
    }

    /**
     * 当前模型根目录：模型路径解析与目录选择器的共同边界。
     *
     * <p>以<b>持久化的 {@code model_root} 为准</b>，而不是直接读 {@code ModelRegistry.getModelRootDir()}：
     * 后者是一个可变全局量，启动后仍会被其它组件的 {@code discoverAll()} 重置回库默认值
     * （实测恢复阶段回读正确、请求阶段又变回默认值），用它做边界判断会时对时错。
     * 这里读到持久化值后顺带 best-effort 推给注册表，让库侧相对路径解析也用同一个根。</p>
     *
     * @return 模型根目录
     */
    private Path modelRoot() {
        String persisted = readModelRoot();
        String root = persisted == null || persisted.isBlank()
                ? ModelRegistry.getModelRootDir()
                : persisted;
        if (root == null || root.isBlank()) {
            return Paths.get(System.getProperty("user.home"));
        }
        if (persisted != null && !persisted.isBlank() && !persisted.equals(ModelRegistry.getModelRootDir())) {
            applyModelRoot();
        }
        return Paths.get(root).toAbsolutePath().normalize();
    }

    /**
     * 当前生效模型根的绝对路径字符串（管理面回显用）。
     *
     * <p>{@link #modelRoot()} 的对外只读视图：未持久化 model_root 时回落
     * {@code ModelRegistry.getModelRootDir()}（再退 {@code user.home}），
     * 与目录选择器和相对模型路径解析用的是<b>同一个口径</b>。
     * 页面显示这个值，「模型往哪放 / 文件在哪」才不会和真实解析位置对不上。</p>
     *
     * @return 绝对路径；解析异常返回空串
     */
    public String displayModelRoot() {
        try {
            return modelRoot().toString();
        } catch (Exception ex) {
            log.warn("[ONNX] 解析展示用模型根失败: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * 解析浏览目标：空值取模型根，非空必须落在模型根之内。
     *
     * @param path 请求路径
     * @param root 模型根
     * @return 归一化后的目录
     * @throws IllegalArgumentException 目录不存在、不在模型根内、或命中禁入规则
     */
    private Path resolveBrowsable(String path, Path root) {
        if (path == null || path.isBlank()) {
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("模型根目录不存在: " + root);
            }
            return root;
        }
        Path dir = Paths.get(path.trim()).toAbsolutePath().normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("目录超出模型根范围: " + dir + "（模型根 " + root + "）");
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在: " + dir);
        }
        if (deniedAbsolute(dir)) {
            throw new IllegalArgumentException("目录不允许用于模型: " + dir);
        }
        return dir;
    }

    /**
     * 判定绝对路径是否落在系统敏感目录下。
     *
     * @param dir 目录
     * @return 命中敏感根目录返回 {@code true}
     */
    private boolean deniedAbsolute(Path dir) {
        String text = dir.toString();
        for (String prefix : DENIED_ABSOLUTE_PREFIXES) {
            if (text.equals(prefix) || text.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定子目录是否因禁入规则不展示。
     *
     * @param parent 父目录
     * @param child  子目录
     * @return 禁入返回 {@code true}
     */
    private boolean isDenied(Path parent, Path child) {
        String name = child.getFileName().toString().toLowerCase();
        if (DENIED_HIDDEN_NAMES.contains(name)) {
            return true;
        }
        // 顶层系统目录与隐藏凭据目录禁入；/opt、/data 等模型常用落点不受影响
        return parent.getParent() == null && DENIED_DIR_NAMES.contains(name);
    }

    /**
     * 解析并校验目录：必须存在且确实是目录。
     *
     * @param path 目录路径，空值时取模型根目录
     * @return 归一化后的目录
     * @throws IllegalArgumentException 路径不是目录
     */
    private Path resolveDirectory(String path) {
        Path dir = (path == null || path.isBlank())
                ? modelRoot()
                : Paths.get(path.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在或不是绝对路径: " + path);
        }
        if (deniedAbsolute(dir)) {
            throw new IllegalArgumentException("目录不允许用于模型: " + dir);
        }
        return dir;
    }

    /**
     * 校验目录可被文件系统接口浏览，并返回其归一化绝对路径。
     *
     * @param rawDir 目录路径
     * @return 归一化绝对路径
     * @throws IllegalArgumentException 目录不存在或不是目录
     */
    private String requireBrowsableDirectory(String rawDir) {
        Path dir = resolveDirectory(rawDir);
        return dir.toAbsolutePath().toString();
    }

    /**
     * 查询外部目录清单所在行。
     *
     * @return 设置行，不存在时返回 null
     */
    private SysAiGlobalSetting findRow() {
        return findRow(SETTING_FIELD);
    }

    /**
     * 查询指定字段所在行。
     *
     * @param field 字段名
     * @return 设置行，不存在时返回 {@code null}
     */
    private SysAiGlobalSetting findRow(String field) {
        return globalSettingMapper.selectOne(
                new LambdaQueryWrapper<SysAiGlobalSetting>()
                        .eq(SysAiGlobalSetting::getSettingGroup, SETTING_GROUP)
                        .eq(SysAiGlobalSetting::getField, field)
                        .last("LIMIT 1"));
    }

    /**
     * 读取持久化的模型根目录。
     *
     * @return 模型根目录；未持久化时返回空串
     */
    public String readModelRoot() {
        try {
            SysAiGlobalSetting row = findRow(SETTING_FIELD_MODEL_ROOT);
            return row == null || row.getValue() == null ? "" : row.getValue().trim();
        } catch (Exception ex) {
            log.warn("[ONNX] 读取模型根失败: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * 持久化当前选定的模型根目录。
     *
     * @param dir 模型根目录
     */
    private void writeModelRoot(String dir) {
        try {
            SysAiGlobalSetting row = findRow(SETTING_FIELD_MODEL_ROOT);
            if (row == null) {
                row = new SysAiGlobalSetting();
                row.setSettingGroup(SETTING_GROUP);
                row.setField(SETTING_FIELD_MODEL_ROOT);
                row.setValueType("string");
                row.setDescription("ONNX 模型根目录（单根语义，等于目录选择器选中的目录）");
                row.setSort(0);
                row.setStatus(1);
                row.setValue(dir);
                globalSettingMapper.insert(row);
            } else if (!dir.equals(row.getValue())) {
                row.setValue(dir);
                globalSettingMapper.updateById(row);
            }
        } catch (Exception ex) {
            log.warn("[ONNX] 写入模型根失败: {}", ex.getMessage());
        }
    }

    /**
     * 写入外部目录清单（行不存在则创建）。
     *
     * @param entries 清单条目
     */
    private void writeEntries(List<Map<String, String>> entries) {
        try {
            String json = objectMapper.writeValueAsString(entries);
            SysAiGlobalSetting row = findRow();
            if (row == null) {
                row = new SysAiGlobalSetting();
                row.setSettingGroup(SETTING_GROUP);
                row.setField(SETTING_FIELD);
                row.setValueType("string");
                row.setDescription("ONNX 外部服务器模型目录清单（系统自动维护，请勿手工修改）");
                row.setSort(0);
                row.setStatus(1);
                row.setValue(json);
                globalSettingMapper.insert(row);
            } else {
                row.setValue(json);
                int affected = globalSettingMapper.updateById(row);
                if (affected == 0) {
                    log.warn("[ONNX] 更新外部目录清单未影响任何行（id={}）", row.getId());
                }
            }
        } catch (Exception ex) {
            log.warn("[ONNX] 写入外部目录清单失败: {}", ex.getMessage());
        }
    }
}
