package com.chua.starter.eduai.collect.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.starter.eduai.mapper.EduIdiomGradeMapper;
import com.chua.starter.eduai.mapper.EduIdiomMapper;
import com.chua.starter.eduai.model.EduIdiom;
import com.chua.starter.eduai.model.EduIdiomGrade;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 成语导入支持：采集 SPI 由 common-starter 的 ServiceProvider 实例化（非 Spring Bean），
 * 故这里用 Spring 组件把 Mapper 注入到静态引用，供采集 Provider 落库使用。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdiomImportSupport {

    /**
     * 成语 Mapper（静态引用，供 SPI Provider 使用）
     */
    private static EduIdiomMapper idiomMapper;

    /**
     * 成语学段映射 Mapper（静态引用，供 SPI Provider 使用）
     */
    private static EduIdiomGradeMapper gradeMapper;

    private final EduIdiomMapper idiomMapperBean;

    private final EduIdiomGradeMapper gradeMapperBean;

    /**
     * 注入 Mapper 到静态引用。
     */
    @PostConstruct
    void init() {
        idiomMapper = idiomMapperBean;
        gradeMapper = gradeMapperBean;
    }

    /**
     * 批量导入成语（按内容去重），并按学段/年级建立映射。
     *
     * @param idioms      成语列表
     * @param gradeLevel  学段（如 小学）
     * @param grade       年级（如 一年级，可为空）
     * @param ownerUserId 归属用户
     * @return 实际写入条数
     */
    public static int importIdioms(List<EduIdiom> idioms, String gradeLevel, String grade, String ownerUserId) {
        if (idiomMapper == null || idioms == null || idioms.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (EduIdiom idiom : idioms) {
            if (idiom == null || !StringUtils.hasText(idiom.getIdiomContent())) {
                continue;
            }
            String content = idiom.getIdiomContent().trim();
            if (exists(content)) {
                continue;
            }
            idiom.setIdiomContent(content);
            idiom.setOwnerUserId(ownerUserId);
            idiomMapper.insert(idiom);
            bindGrade(idiom.getIdiomId(), gradeLevel, grade, ownerUserId);
            saved++;
        }
        return saved;
    }

    /**
     * 统计当前已入库成语数量（供采集器「自动续采」推算起始下标）。
     *
     * @return 成语条数
     */
    public static long countIdioms() {
        if (idiomMapper == null) {
            return 0;
        }
        Long count = idiomMapper.selectCount(null);
        return count == null ? 0 : count;
    }

    /**
     * 判断成语是否已存在（按内容去重）。
     *
     * @param content 成语内容
     * @return 已存在返回 true
     */
    private static boolean exists(String content) {
        Long count = idiomMapper.selectCount(new QueryWrapper<EduIdiom>().eq("idiom_content", content));
        return count != null && count > 0;
    }

    /**
     * 建立成语与学段/年级的映射。
     *
     * @param idiomId    成语标识
     * @param gradeLevel 学段
     * @param grade      年级（可为空）
     * @param owner      归属用户
     */
    private static void bindGrade(Long idiomId, String gradeLevel, String grade, String owner) {
        if (idiomId == null || !StringUtils.hasText(gradeLevel) || gradeMapper == null) {
            return;
        }
        EduIdiomGrade row = new EduIdiomGrade();
        row.setIdiomId(idiomId);
        row.setGradeLevel(gradeLevel.trim());
        row.setGrade(StringUtils.hasText(grade) ? grade.trim() : null);
        row.setOwnerUserId(owner);
        gradeMapper.insert(row);
    }
}
