package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduHanziMapper;
import com.chua.starter.eduai.mapper.EduWordMapper;
import com.chua.starter.eduai.model.EduHanzi;
import com.chua.starter.eduai.model.EduWord;
import com.chua.starter.eduai.service.IEduWordService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.GradeLevels;
import com.chua.starter.eduai.support.util.PageBounds;
import com.chua.starter.eduai.support.util.Pinyins;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 词语库服务实现。
 *
 * <p>归属口径与其它 eduai 资源一致：管理员可见全部，普通用户可见「本人 + 系统预置（归属为空）」。
 * 唯一键是整词内容，因此「按词查重后更新」保证重复提交不会撞唯一键。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class EduWordServiceImpl extends ServiceImpl<EduWordMapper, EduWord> implements IEduWordService {

    /**
     * 关联检索最多返回的条数（右键菜单是速查，不做无限列表）。
     */
    private static final int RELATED_LIMIT = 20;

    /**
     * 右键菜单的六种检索口径 → 中文标题。
     */
    private static final Map<String, String> MODE_LABELS = Map.of(
            "similar", "相似词语",
            "antonym", "反向词语",
            "homophone", "同音词",
            "pinyin", "同音字词",
            "sentence", "造句",
            "char", "含相同字"
    );

    /**
     * 汉字库 Mapper：同音字检索要跨到汉字表（词语只存整词拼音，拆不出每个字）。
     */
    private final EduHanziMapper hanziMapper;

    @Override
    public PageResult<EduWord> pageQuery(String keyword, String gradeLevel, String kind,
                                         Integer pageNum, Integer pageSize) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        Page<EduWord> page = new Page<>(PageBounds.pageNum(pageNum), PageBounds.pageSize(pageSize));
        QueryWrapper<EduWord> wrapper = new QueryWrapper<>();
        if (scope.ownerPredicate() != null) {
            wrapper.and(w -> w.isNull("word_owner_user_id").or().eq("word_owner_user_id", scope.ownerPredicate()));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 无声调检索键：用户敲 meili 要能命中「美丽 / měi lì」
            String plain = Pinyins.plain(kw);
            wrapper.and(w -> {
                w.like("word_content", kw)
                        .or().like("word_pinyin", kw)
                        .or().like("word_meaning", kw)
                        .or().like("word_synonyms", kw)
                        .or().like("word_antonyms", kw)
                        .or().like("word_homophones", kw);
                if (plain != null) {
                    w.or().like("word_pinyin_plain", plain);
                }
            });
        }
        if (StringUtils.hasText(kind)) {
            wrapper.eq("word_kind", kind.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (StringUtils.hasText(gradeLevel)) {
            // 前端下拉传 PRIMARY/JUNIOR 码值，库里存中文名，不归一化筛选恒为 0 条
            wrapper.eq("word_grade_level", GradeLevels.normalize(gradeLevel));
        }
        wrapper.orderByDesc("word_id");
        IPage<EduWord> result = baseMapper.selectPage(page, wrapper);
        return PageResult.<EduWord>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    public Long saveWord(EduWord entity) {
        if (entity == null || !StringUtils.hasText(entity.getWordContent())) {
            throw new BusinessException("词语不能为空");
        }
        String word = entity.getWordContent().trim();
        entity.setWordContent(word);
        // 检索键由服务端派生：客户端只填读音，归一化口径必须唯一
        entity.setWordPinyinPlain(Pinyins.plain(entity.getWordPinyin()));
        if (!StringUtils.hasText(entity.getWordKind())) {
            entity.setWordKind("WORD");
        }
        if (entity.getWordStatus() == null) {
            entity.setWordStatus(1);
        }
        if (entity.getWordId() != null && entity.getWordId() > 0) {
            EduWord exists = getById(entity.getWordId());
            if (exists == null) {
                throw new BusinessException("词语不存在");
            }
            return updateExisting(exists, entity);
        }
        // 按词查重：唯一键保证一个词一条，重复提交同一个词则更新
        EduWord same = findByContent(word);
        if (same != null) {
            return updateExisting(same, entity);
        }
        entity.setOwnerUserId(EduUserContext.requireUserId());
        try {
            save(entity);
            return entity.getWordId();
        } catch (RuntimeException ex) {
            // 并发竞态：两人同时判定「库里没有这个词」，后落库的撞唯一键。
            // 回查一次，先落库者赢，另一个转更新（与汉字库同一处理，且同样不能套事务）。
            EduWord raced = findByContent(word);
            if (raced == null) {
                throw ex;
            }
            return updateExisting(raced, entity);
        }
    }

    /**
     * 按整词查已有记录（唯一键保证最多一条）。
     *
     * @param content 词语内容
     * @return 记录；不存在返回 null
     */
    private EduWord findByContent(String content) {
        return getOne(new QueryWrapper<EduWord>().eq("word_content", content).last("LIMIT 1"), false);
    }

    /**
     * 用本次提交覆盖库中已有记录（先做归属写权限校验）。
     *
     * @param target 库中已有记录
     * @param entity 本次提交
     * @return 词语标识
     */
    private Long updateExisting(EduWord target, EduWord entity) {
        EduUserContext.checkWritable(target.getOwnerUserId());
        entity.setWordId(target.getWordId());
        entity.setOwnerUserId(target.getOwnerUserId());
        updateById(entity);
        return entity.getWordId();
    }

    @Override
    public boolean deleteWord(Long id) {
        if (id == null) {
            return false;
        }
        EduWord exists = getById(id);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        return removeById(id);
    }

    @Override
    public Map<String, Object> related(Long wordId, String mode) {
        if (wordId == null) {
            throw new BusinessException("wordId 不能为空");
        }
        EduWord word = getById(wordId);
        if (word == null) {
            throw new BusinessException("词语不存在");
        }
        EduUserContext.checkReadable(word.getOwnerUserId());
        String key = StringUtils.hasText(mode) ? mode.trim().toLowerCase(java.util.Locale.ROOT) : "similar";
        if (!MODE_LABELS.containsKey(key)) {
            throw new BusinessException("不支持的检索口径：" + mode);
        }

        List<Map<String, Object>> items;
        String source;
        if ("similar".equals(key)) {
            items = fromField(word.getWordSynonyms(), word);
            source = items.isEmpty() ? "按共同用字自动匹配" : "词条维护的近义词";
            if (items.isEmpty()) {
                items = bySharedChar(word);
            }
        } else if ("antonym".equals(key)) {
            items = fromField(word.getWordAntonyms(), word);
            source = items.isEmpty() ? "词条未维护反义词，也没有可自动推导的对应词" : "词条维护的反义词";
        } else if ("homophone".equals(key)) {
            items = fromField(word.getWordHomophones(), word);
            source = items.isEmpty() ? "按读音自动匹配" : "词条维护的同音词";
            if (items.isEmpty()) {
                items = bySamePinyin(word);
            }
        } else if ("pinyin".equals(key)) {
            // 同音字词 = 同音词（整词）+ 同音字（汉字库），两类合在一起才有「字词」的意思
            items = new ArrayList<>(bySamePinyin(word));
            items.addAll(hanziByPinyin(word));
            source = "同音词 + 汉字库同音字";
        } else if ("char".equals(key)) {
            items = bySharedChar(word);
            source = "含相同汉字的词";
        } else {
            items = sentences(word);
            source = "词条例句与造句模板";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", key);
        result.put("label", MODE_LABELS.get(key));
        result.put("source", source);
        result.put("wordContent", word.getWordContent());
        result.put("items", items.size() > RELATED_LIMIT ? items.subList(0, RELATED_LIMIT) : items);
        return result;
    }

    @Override
    public List<EduWord> suggest(String keyword, int count) {
        // [P3C A20 豁免]
        int size = Math.max(1, Math.min(count, 50));
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String kw = keyword.trim();
        String plain = Pinyins.plain(kw);
        QueryWrapper<EduWord> wrapper = new QueryWrapper<>();
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        if (scope.ownerPredicate() != null) {
            wrapper.and(w -> w.isNull("word_owner_user_id").or().eq("word_owner_user_id", scope.ownerPredicate()));
        }
        wrapper.and(w -> {
            w.like("word_content", kw).or().like("word_pinyin", kw);
            if (plain != null) {
                w.or().like("word_pinyin_plain", plain);
            }
        });
        wrapper.orderByDesc("word_id").last("LIMIT " + size);
        return baseMapper.selectList(wrapper);
    }

    // ==================== 关联检索的各类口径 ====================

    /**
     * 把词条里「逗号分隔的近义 / 反义 / 同音词」展开成结果项。
     *
     * <p>每项都回查词语库补拼音与释义；库里没有的词也照样返回，
     * 只是 {@code inBank=false}，前端据此提示「该词尚未入库」。</p>
     *
     * @param field 逗号分隔的词语串
     * @param self  当前词（不返回自己）
     * @return 结果项
     */
    private List<Map<String, Object>> fromField(String field, EduWord self) {
        if (!StringUtils.hasText(field)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : field.split("[,，;；、/|]+")) {
            String content = raw.trim();
            if (content.isEmpty() || content.equals(self.getWordContent()) || !seen.add(content)) {
                continue;
            }
            EduWord hit = findByContent(content);
            items.add(item(content, hit, hit == null ? "该词尚未入库" : null));
        }
        return items;
    }

    /**
     * 按「读音相同」自动匹配同音词（排除自己）。
     *
     * @param self 当前词
     * @return 结果项
     */
    private List<Map<String, Object>> bySamePinyin(EduWord self) {
        if (!StringUtils.hasText(self.getWordPinyinPlain())) {
            return List.of();
        }
        QueryWrapper<EduWord> wrapper = new QueryWrapper<EduWord>()
                .eq("word_pinyin_plain", self.getWordPinyinPlain())
                .ne("word_id", self.getWordId())
                .last("LIMIT " + RELATED_LIMIT);
        List<Map<String, Object>> items = new ArrayList<>();
        for (EduWord word : baseMapper.selectList(wrapper)) {
            items.add(item(word.getWordContent(), word, null));
        }
        return items;
    }

    /**
     * 按「含相同汉字」匹配词语（排除自己）。
     *
     * <p>逐字 LIKE 再合并去重：词长通常 2~4 字，最多 4 次小查询，
     * 比在 SQL 里拆字符简单且不受方言正则差异影响。</p>
     *
     * @param self 当前词
     * @return 结果项
     */
    private List<Map<String, Object>> bySharedChar(EduWord self) {
        List<Map<String, Object>> items = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (char c : self.getWordContent().toCharArray()) {
            QueryWrapper<EduWord> wrapper = new QueryWrapper<EduWord>()
                    .like("word_content", String.valueOf(c))
                    .ne("word_id", self.getWordId())
                    .last("LIMIT " + RELATED_LIMIT);
            for (EduWord word : baseMapper.selectList(wrapper)) {
                if (seen.add(word.getWordId())) {
                    items.add(item(word.getWordContent(), word, null));
                }
            }
            if (items.size() >= RELATED_LIMIT) {
                break;
            }
        }
        return items;
    }

    /**
     * 从汉字库找同音字（词语只存整词拼音，拆不出逐字读音，故按**词首字**对齐）。
     *
     * <p>做法：取词首字 → 到汉字库拿它的真实读音 → 归一化（去声调、小写、去分隔）
     * → 在汉字库里找归一化读音相同的其它字。</p>
     *
     * <p><b>为什么候选池不能直接 LIKE 拼音前缀</b>：库里的读音是带声调写法
     * （{@code měi}），而检索键是无声调（{@code mei}），{@code LIKE 'me%'} 一条都匹配不上。
     * 汉字库只有 3500 行，这里直接取有拼音的行到内存里归一化比较 ——
     * 只在用户点右键时执行一次，代价可忽略，换来的是「同音」判定精确到整音节。</p>
     *
     * <p>结果明确标注来源为「与词首字同音」，不假装是逐字同音。</p>
     *
     * @param self 当前词
     * @return 结果项
     */
    private List<Map<String, Object>> hanziByPinyin(EduWord self) {
        String content = self.getWordContent();
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String firstChar = content.substring(0, 1);
        // 词首字自己的记录：拿它的真实读音（整词拼音可能是空格分隔的，不能直接切）
        EduHanzi head = hanziMapper.selectOne(
                new QueryWrapper<EduHanzi>().eq("hanzi_char", firstChar).last("LIMIT 1"));
        String key = head == null ? null : Pinyins.plain(head.getHanziPinyin());
        if (!StringUtils.hasText(key)) {
            // 词首字没入库或没读音 → 退回整词拼音的第一个音节
            List<String> syllables = Pinyins.split(self.getWordPinyin());
            key = syllables.isEmpty() ? null : Pinyins.plain(syllables.get(0));
        }
        if (!StringUtils.hasText(key)) {
            return List.of();
        }
        // 候选池：有拼音的汉字（3500 行量级），逐个归一化后按整音节比对
        List<EduHanzi> candidates = hanziMapper.selectList(
                new QueryWrapper<EduHanzi>().isNotNull("hanzi_pinyin").last("LIMIT 5000"));
        List<Map<String, Object>> items = new ArrayList<>();
        String finalKey = key;
        for (EduHanzi hanzi : candidates) {
            String hanziPlain = Pinyins.plain(hanzi.getHanziPinyin());
            // 精确到「归一化读音相同」：只按前两个字母模糊匹配会把 mei 和 me 混在一起
            if (hanziPlain == null || !hanziPlain.equals(finalKey)) {
                continue;
            }
            if (content.indexOf(hanzi.getHanziChar()) >= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("content", hanzi.getHanziChar());
            row.put("pinyin", hanzi.getHanziPinyin());
            row.put("meaning", hanzi.getHanziMeaning());
            row.put("inBank", Boolean.TRUE);
            row.put("detail", "汉字库 · 与词首字「" + firstChar + "」同音");
            items.add(row);
            if (items.size() >= RELATED_LIMIT) {
                break;
            }
        }
        return items;
    }

    /**
     * 造句：返回词条自带的例句 + 三个可套用的造句模板。
     *
     * <p>库里绝大多数词没有例句（采集源不给），所以「造句」不能只回一条空列表 ——
     * 老师点右键是为了拿到可用的句子。模板按词性/结构给不同句式，并明确标注「模板」。</p>
     *
     * @param self 当前词
     * @return 结果项
     */
    private List<Map<String, Object>> sentences(EduWord self) {
        List<Map<String, Object>> items = new ArrayList<>();
        String word = self.getWordContent();
        if (StringUtils.hasText(self.getWordExample())) {
            Map<String, Object> own = new LinkedHashMap<>();
            own.put("content", self.getWordExample().trim());
            own.put("detail", "词条维护的例句");
            own.put("inBank", Boolean.TRUE);
            items.add(own);
        }
        String[] templates = {
                "他在课堂上用「" + word + "」造了一个句子。",
                "请你用「" + word + "」写一句话，说明它的意思。",
                "读完课文后，我用「" + word + "」写了下面这句话：",
        };
        for (String sentence : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("content", sentence);
            row.put("detail", "造句模板");
            row.put("inBank", Boolean.TRUE);
            items.add(row);
        }
        return items;
    }

    /**
     * 组装单个结果项。
     *
     * @param content 词语内容
     * @param hit     库中记录，可为 null
     * @param detail  附加说明，可为 null
     * @return 结果项
     */
    private Map<String, Object> item(String content, EduWord hit, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("content", hit != null ? hit.getWordContent() : content);
        row.put("pinyin", hit == null ? null : hit.getWordPinyin());
        row.put("meaning", hit == null ? null : hit.getWordMeaning());
        row.put("inBank", hit != null);
        if (detail != null) {
            row.put("detail", detail);
        }
        return row;
    }
}
