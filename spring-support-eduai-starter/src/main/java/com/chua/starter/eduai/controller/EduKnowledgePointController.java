package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.KnowledgeFileBindDTO;
import com.chua.starter.eduai.model.dto.KnowledgePointSaveDTO;
import com.chua.starter.eduai.model.vo.KnowledgeCardVO;
import com.chua.starter.eduai.model.vo.KnowledgeFileVO;
import com.chua.starter.eduai.model.vo.KnowledgePointVO;
import com.chua.starter.eduai.service.IEduKnowledgePointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 知识点管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/knowledge-point")
@RequiredArgsConstructor
@Tag(name = "知识点管理")
public class EduKnowledgePointController {

    /** 允许上传的附件扩展名（白名单，防任意类型落盘） */
    private static final Set<String> ALLOWED_FILE_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp",
            "pdf", "docx", "doc");

    /** 附件落盘根目录（相对工作目录；数据库已是外部 MySQL/MariaDB，此目录仅存附件文件） */
    private static final Path FILE_ROOT = Paths.get("data", "knowledge-files");

    /**
     * Knowledgepoint服务
     */
    private final IEduKnowledgePointService knowledgePointService;

    /**
     * 查询知识点树
     *
     * @param subject    学科
     * @param gradeLevel 学段
     * @param grade      年级
     * @return 知识点树
     * @param false false
     */
    @Operation(summary = "查询知识点树")
    @GetMapping("/tree")
    public ReturnResult<List<KnowledgePointVO>> tree(@RequestParam(required = false) String subject,
                                                     @RequestParam(required = false) String gradeLevel,
                                                     @RequestParam(required = false) String grade) {
        return ReturnResult.ok(knowledgePointService.listTree(subject, gradeLevel, grade));
    }

    /**
     * 关键字扁平检索（下拉远程过滤）
     *
     * @param keyword 名称/编码模糊关键字
     * @param subject 学科
     * @param limit   最大条数
     * @return 知识点列表
     */
    @Operation(summary = "关键字检索知识点")
    @GetMapping("/list")
    public ReturnResult<List<KnowledgePointVO>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String subject,
                                                     @RequestParam(required = false) Integer limit) {
        return ReturnResult.ok(knowledgePointService.listByKeyword(keyword, subject, limit));
    }

    /**
     * 知识卡片扁平列表（带有效文本/附件计数）
     *
     * @param keyword    名称/编码模糊关键字
     * @param subject    学科
     * @param gradeLevel 学段
     * @param grade      年级
     * @return 卡片列表
     */
    @Operation(summary = "知识卡片列表（含文本/附件计数）")
    @GetMapping("/cards")
    public ReturnResult<List<KnowledgeCardVO>> cards(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String subject,
                                                     @RequestParam(required = false) String gradeLevel,
                                                     @RequestParam(required = false) String grade) {
        return ReturnResult.ok(knowledgePointService.listCards(keyword, subject, gradeLevel, grade));
    }

    /**
     * 卡片详情（含富文本正文）
     *
     * @param id 卡片标识
     * @return 知识点详情
     */
    @Operation(summary = "卡片详情（含正文）")
    @GetMapping("/{id}")
    public ReturnResult<KnowledgePointVO> detail(@PathVariable Long id) {
        return ReturnResult.ok(knowledgePointService.getDetail(id));
    }

    /**
     * 卡片下附件条目列表
     *
     * @param id 卡片标识
     * @return 附件条目列表
     */
    @Operation(summary = "卡片附件条目列表")
    @GetMapping("/{id}/files")
    public ReturnResult<List<KnowledgeFileVO>> listFiles(@PathVariable Long id) {
        return ReturnResult.ok(knowledgePointService.listFiles(id));
    }

    /**
     * 把已上传附件绑定到卡片
     *
     * @param dto 附件绑定数据
     * @return 附件条目标识
     */
    @Operation(summary = "绑定卡片附件")
    @PostMapping("/file/bind")
    public ReturnResult<Long> bindFile(@RequestBody KnowledgeFileBindDTO dto) {
        return ReturnResult.ok(knowledgePointService.bindFile(dto));
    }

    /**
     * 删除卡片附件条目（逻辑删除，不删磁盘文件）
     *
     * @param fileId 附件条目标识
     * @return 是否成功
     */
    @Operation(summary = "删除卡片附件条目")
    @DeleteMapping("/file/{fileId}")
    public ReturnResult<Boolean> deleteFile(@PathVariable Long fileId) {
        return ReturnResult.ok(knowledgePointService.deleteFile(fileId));
    }

    /**
     * 新增或更新知识点
     *
     * @param dto 知识点数据
     * @return 知识点ID
     */
    @Operation(summary = "新增或更新知识点")
    @PostMapping("/save")
    public ReturnResult<Long> save(@RequestBody KnowledgePointSaveDTO dto) {
        return ReturnResult.ok(knowledgePointService.saveKnowledgePoint(dto));
    }

    /**
     * 上传知识点附件（图片 / PDF / DOCX），返回相对路径供 save 绑定
     *
     * @param file 附件
     * @return 相对路径、文件名、扩展名、大小
     */
    @Operation(summary = "上传知识点附件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) {
            ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_FILE_EXT.contains(ext)) {
            return ReturnResult.error("仅支持图片、PDF、DOCX 附件");
        }
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = FILE_ROOT.resolve(day);
        Files.createDirectories(dir);
        Path target = dir.resolve(stored);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String relative = day + "/" + stored;
        Map<String, Object> body = new HashMap<>(4);
        body.put("filePath", relative);
        body.put("fileName", original);
        body.put("fileType", ext);
        body.put("fileSize", Files.size(target));
        log.info("知识点附件上传成功: {}", relative);
        return ReturnResult.ok(body);
    }

    /**
     * 按相对路径读取附件字节流（iframe / img 直链预览）
     *
     * @param path data/knowledge-files 下的相对路径
     * @param response 响应
     */
    @Operation(summary = "预览知识点附件")
    @GetMapping("/file")
    public void file(@RequestParam String path, HttpServletResponse response) throws IOException {
        Path normalized = Paths.get(path).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "非法路径");
            return;
        }
        Path full = FILE_ROOT.resolve(normalized).normalize();
        if (!full.startsWith(FILE_ROOT.normalize()) || !Files.isRegularFile(full)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        String name = full.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        response.setContentType(switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            default -> "application/octet-stream";
        });
        response.setHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
        try (InputStream in = Files.newInputStream(full); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /**
     * 删除知识点（含子节点）
     *
     * @param id 知识点标识
     * @return 是否成功
     */
    @Operation(summary = "删除知识点")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(knowledgePointService.deleteKnowledgePoint(id));
    }
}
