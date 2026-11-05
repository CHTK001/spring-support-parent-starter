// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.support.util;

import com.chua.starter.eduai.model.EduPoem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * 古诗文趣味游戏题目生成器
 *
 * <p>围绕 edu_poem 现有字段（标题 / 作者 / 朝代 / 正文 / 标签）设计七种**互不重复**的玩法，
 * 与既有的「成语消消乐」（配对消除）、「古诗补全」（选下句）机制区分开：</p>
 *
 * <ul>
 *   <li>{@link #order} 诗句排序：整首打乱，还原正确句序</li>
 *   <li>{@link #feihualing} 飞花令：给定关键字，从候选句里挑出含该字的</li>
 *   <li>{@link #title} 看句猜题：给句子选诗题</li>
 *   <li>{@link #author} 诗归其主：给句子选作者（干扰项取同朝代，更有迷惑性）</li>
 *   <li>{@link #dynasty} 朝代溯源：给句子判断朝代</li>
 *   <li>{@link #theme} 题材归类：给句子判断题材（取 poem_tags 经白名单过滤后的题材词）</li>
 *   <li>{@link #fill} 炼字填空：挖掉一处字词四选一复原</li>
 * </ul>
 *
 * <p>所有方法都是纯函数：入参是已经按学段筛好的诗列表，不碰数据库，便于单测与复用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class PoemGameGenerator {

    /**
     * 飞花令常用关键字（高频且诗句覆盖广）
     */
    public static final List<String> FEIHUALING_KEYWORDS =
            List.of("月", "花", "春", "风", "雪", "山", "水", "江", "云", "秋", "日", "夜");

    /**
     * 题材白名单：只从标签里挑这些「主题/情感」类词出题。
     *
     * <p>数据源（chinese-gushiwen）的 type 字段混有来源类标记（唐诗三百首、乐府、
     * 初中古诗…），拿它们出题没有意义；这里限定为真正的题材词。</p>
     */
    public static final List<String> THEME_TAGS = List.of(
            "思乡", "送别", "边塞", "山水", "田园", "咏物", "咏史", "爱国",
            "写景", "抒情", "言志", "哲理", "节令", "闺怨", "讽喻", "叙事",
            "豪放", "婉约", "战争", "友情", "母爱", "月亮", "春天", "秋天");

    /**
     * 选择题干扰项个数（含正确答案共 4 项）
     */
    private static final int OPTION_SIZE = 4;

    /**
     * 朝代兜底候选：诗库某朝无数据时用于补足选项
     */
    private static final List<String> FALLBACK_DYNASTIES =
            List.of("唐代", "宋代", "明代", "清代", "汉代", "魏晋");

    private PoemGameGenerator() {
    }

    /**
     * 诗句排序：把整首诗的句子打乱，玩家按正确顺序还原。
     *
     * @param poems 候选诗
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表
     */
    public static List<Map<String, Object>> order(List<EduPoem> poems, int count, Random random) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (EduPoem poem : shuffled(poems, random)) {
            if (items.size() >= count) {
                break;
            }
            List<String> answer = PoemTextUtils.splitSentences(poem.getPoemContent());
            if (answer.size() < 2) {
                continue;
            }
            List<String> shuffledSentences = new ArrayList<>(answer);
            Collections.shuffle(shuffledSentences, random);
            // 打乱后恰好等于原序的极端情况：再洗一次，避免"题目自带答案"
            if (shuffledSentences.equals(answer)) {
                Collections.shuffle(shuffledSentences, random);
            }
            items.add(base(poem, "order", Map.of(
                    "sentences", shuffledSentences,
                    "answer", answer
            )));
        }
        return items;
    }

    /**
     * 飞花令：给定关键字，从候选句里挑出所有含该字的句子。
     *
     * @param poems 候选诗
     * @param keyword 关键字（单字）
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表
     */
    public static List<Map<String, Object>> feihualing(List<EduPoem> poems, String keyword,
                                                       int count, Random random) {
        String key = keyword == null || keyword.isEmpty()
                ? FEIHUALING_KEYWORDS.get(random.nextInt(FEIHUALING_KEYWORDS.size()))
                : keyword.trim();
        List<String> hits = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        for (EduPoem poem : poems) {
            for (String sentence : PoemTextUtils.splitSentences(poem.getPoemContent())) {
                if (sentence.contains(key)) {
                    if (!hits.contains(sentence)) {
                        hits.add(sentence);
                    }
                } else if (!misses.contains(sentence)) {
                    misses.add(sentence);
                }
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < count; i += 1) {
            Collections.shuffle(hits, random);
            Collections.shuffle(misses, random);
            int wantHit = Math.min(3, hits.size());
            int wantMiss = Math.min(OPTION_SIZE - wantHit, misses.size());
            if (wantHit + wantMiss < OPTION_SIZE) {
                break;
            }
            List<Map<String, Object>> options = new ArrayList<>();
            for (int h = 0; h < wantHit; h += 1) {
                options.add(Map.of("text", hits.get(h), "hit", true));
            }
            for (int m = 0; m < wantMiss; m += 1) {
                options.add(Map.of("text", misses.get(m), "hit", false));
            }
            Collections.shuffle(options, random);
            items.add(Map.of(
                    "mode", "feihualing",
                    "keyword", key,
                    "options", options,
                    "hitCount", wantHit
            ));
        }
        return items;
    }

    /**
     * 看句猜题：给出一句诗，从四个诗题里选出它所属的那首。
     *
     * @param poems 候选诗
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表
     */
    public static List<Map<String, Object>> title(List<EduPoem> poems, int count, Random random) {
        return choiceGame(poems, count, random, "title",
                EduPoem::getPoemTitle,
                poem -> {
                    List<String> sentences = PoemTextUtils.splitSentences(poem.getPoemContent());
                    return sentences.isEmpty() ? null : sentences.get(random.nextInt(sentences.size()));
                });
    }

    /**
     * 诗归其主：给出诗句，从四位作者里选出作者；干扰项优先取同朝代作者。
     *
     * @param poems 候选诗
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表
     */
    public static List<Map<String, Object>> author(List<EduPoem> poems, int count, Random random) {
        return choiceGame(poems, count, random, "author",
                EduPoem::getPoemAuthor,
                poem -> {
                    List<String> sentences = PoemTextUtils.splitSentences(poem.getPoemContent());
                    return sentences.isEmpty() ? null : sentences.get(random.nextInt(sentences.size()));
                },
                EduPoem::getPoemDynasty);
    }

    /**
     * 朝代溯源：给出诗句，从四个朝代里选出它所属的朝代。
     *
     * @param poems 候选诗
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表
     */
    public static List<Map<String, Object>> dynasty(List<EduPoem> poems, int count, Random random) {
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> dynasties = new LinkedHashSet<>();
        for (EduPoem poem : poems) {
            if (poem.getPoemDynasty() != null && !poem.getPoemDynasty().isBlank()) {
                dynasties.add(poem.getPoemDynasty().trim());
            }
        }
        if (dynasties.size() < 2) {
            // 只有一个朝代时游戏无意义（如当前库全是唐代），由调用方决定是否提示
            return items;
        }
        for (EduPoem poem : shuffled(poems, random)) {
            if (items.size() >= count) {
                break;
            }
            String answer = poem.getPoemDynasty() == null ? null : poem.getPoemDynasty().trim();
            List<String> sentences = PoemTextUtils.splitSentences(poem.getPoemContent());
            if (answer == null || sentences.isEmpty()) {
                continue;
            }
            List<String> pool = new ArrayList<>(dynasties);
            pool.remove(answer);
            Collections.shuffle(pool, random);
            List<String> options = new ArrayList<>();
            options.add(answer);
            for (String d : pool) {
                if (options.size() >= OPTION_SIZE) {
                    break;
                }
                options.add(d);
            }
            for (String d : FALLBACK_DYNASTIES) {
                if (options.size() >= OPTION_SIZE) {
                    break;
                }
                if (!options.contains(d)) {
                    options.add(d);
                }
            }
            Collections.shuffle(options, random);
            Map<String, Object> item = base(poem, "dynasty", Map.of(
                    "sentence", sentences.get(random.nextInt(sentences.size())),
                    "options", options,
                    "answerIndex", options.indexOf(answer)
            ));
            items.add(item);
        }
        return items;
    }

    /**
     * 炼字填空：从一句诗里挖掉连续两个字，四选一复原。
     *
     * @param poems 候选诗
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表
     */
    public static List<Map<String, Object>> fill(List<EduPoem> poems, int count, Random random) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (EduPoem poem : shuffled(poems, random)) {
            if (items.size() >= count) {
                break;
            }
            List<String> sentences = PoemTextUtils.splitSentences(poem.getPoemContent());
            if (sentences.isEmpty()) {
                continue;
            }
            String sentence = sentences.get(random.nextInt(sentences.size()));
            // 只在汉字序列上挖空，避开标点
            List<Integer> hanPositions = new ArrayList<>();
            for (int i = 0; i < sentence.length(); i += 1) {
                if (isHan(sentence.charAt(i))) {
                    hanPositions.add(i);
                }
            }
            if (hanPositions.size() < 6) {
                continue;
            }
            int start = hanPositions.get(random.nextInt(hanPositions.size() - 2));
            int end = Math.min(start + 2, sentence.length());
            String answer = sentence.substring(start, end);
            String blanked = sentence.substring(0, start) + "＿＿" + sentence.substring(end);
            Set<String> options = new LinkedHashSet<>();
            options.add(answer);
            List<String> otherWords = new ArrayList<>();
            for (EduPoem other : poems) {
                for (String s : PoemTextUtils.splitSentences(other.getPoemContent())) {
                    if (s.length() >= end && !s.equals(sentence)) {
                        otherWords.add(s.substring(start, end));
                    }
                }
                if (otherWords.size() > 60) {
                    break;
                }
            }
            Collections.shuffle(otherWords, random);
            for (String word : otherWords) {
                if (options.size() >= OPTION_SIZE) {
                    break;
                }
                if (word.length() == answer.length() && !word.equals(answer)) {
                    options.add(word);
                }
            }
            if (options.size() < OPTION_SIZE) {
                continue;
            }
            List<String> optionList = new ArrayList<>(options);
            Collections.shuffle(optionList, random);
            items.add(base(poem, "fill", Map.of(
                    "blanked", blanked,
                    "answer", answer,
                    "options", optionList,
                    "answerIndex", optionList.indexOf(answer)
            )));
        }
        return items;
    }

    /**
     * 题材归类：给出诗句，从四个题材里选出它所属的主题（思乡/边塞/田园…）。
     *
     * <p>数据来源是 edu_poem.poem_tags —— 采集/导入时由数据源给出，并经
     * {@link #THEME_TAGS} 白名单过滤掉来源类标记（唐诗三百首、乐府、初中古诗…），
     * 只保留真正的题材词，因此本玩法与「看句猜题/诗归其主/朝代溯源」的取数维度不同。</p>
     *
     * @param poems 候选诗
     * @param count 题目数量
     * @param random 随机源
     * @return 题目列表；标签里没有题材词的诗会被跳过
     */
    public static List<Map<String, Object>> theme(List<EduPoem> poems, int count, Random random) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (EduPoem poem : shuffled(poems, random)) {
            if (items.size() >= count) {
                break;
            }
            List<String> themes = themesOf(poem);
            if (themes.isEmpty()) {
                continue;
            }
            String answer = themes.get(random.nextInt(themes.size()));
            List<String> sentences = PoemTextUtils.splitSentences(poem.getPoemContent());
            if (sentences.isEmpty()) {
                continue;
            }
            // 干扰项从题材白名单里取，且避开该诗自带的其它题材（否则会出现两个正确答案）
            List<String> pool = new ArrayList<>(THEME_TAGS);
            pool.remove(answer);
            pool.removeAll(themes);
            Collections.shuffle(pool, random);
            List<String> options = new ArrayList<>();
            options.add(answer);
            for (String t : pool) {
                if (options.size() >= OPTION_SIZE) {
                    break;
                }
                options.add(t);
            }
            if (options.size() < OPTION_SIZE) {
                continue;
            }
            Collections.shuffle(options, random);
            items.add(base(poem, "theme", Map.of(
                    "question", sentences.get(random.nextInt(sentences.size())),
                    "options", options,
                    "answer", answer,
                    "answerIndex", options.indexOf(answer)
            )));
        }
        return items;
    }

    /**
     * 取一首诗的题材词（标签 ∩ 白名单）。
     *
     * @param poem 诗
     * @return 题材词列表，可能为空
     */
    private static List<String> themesOf(EduPoem poem) {
        List<String> result = new ArrayList<>();
        String tags = poem.getPoemTags();
        if (tags == null || tags.isBlank()) {
            return result;
        }
        for (String raw : tags.split("[,，]")) {
            String tag = raw.trim();
            if (THEME_TAGS.contains(tag) && !result.contains(tag)) {
                result.add(tag);
            }
        }
        return result;
    }

    /**
     * 通用「四选一」题目生成（诗题 / 作者 / 朝代同构：一处取值 + 若干干扰项）。
     *
     * @param poems        候选诗
     * @param count        题目数量
     * @param random       随机源
     * @param mode         模式标识
     * @param answerPicker 从诗中取正确答案
     * @param questionPicker 从诗中取题干（一句诗）
     * @return 题目列表
     */
    private static List<Map<String, Object>> choiceGame(List<EduPoem> poems, int count, Random random,
                                                        String mode,
                                                        Function<EduPoem, String> answerPicker,
                                                        Function<EduPoem, String> questionPicker) {
        return choiceGame(poems, count, random, mode, answerPicker, questionPicker, null);
    }

    /**
     * 通用「四选一」题目生成（可指定干扰项优先来源）。
     *
     * @param poems           候选诗
     * @param count           题目数量
     * @param random          随机源
     * @param mode            模式标识
     * @param answerPicker    正确答案取值
     * @param questionPicker  题干取值
     * @param preferSameGroup 干扰项优先与之相同的分组键（如作者题的朝代），可为 null
     * @return 题目列表
     */
    private static List<Map<String, Object>> choiceGame(List<EduPoem> poems, int count, Random random,
                                                        String mode,
                                                        Function<EduPoem, String> answerPicker,
                                                        Function<EduPoem, String> questionPicker,
                                                        Function<EduPoem, String> preferSameGroup) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (EduPoem poem : shuffled(poems, random)) {
            if (items.size() >= count) {
                break;
            }
            String answer = answerPicker.apply(poem);
            String question = questionPicker.apply(poem);
            if (answer == null || answer.isBlank() || question == null) {
                continue;
            }
            Set<String> options = new LinkedHashSet<>();
            options.add(answer);
            // 优先同分组的干扰项（作者题取同朝代，更难蒙对）
            if (preferSameGroup != null) {
                String group = preferSameGroup.apply(poem);
                for (EduPoem other : shuffled(poems, random)) {
                    if (options.size() >= OPTION_SIZE) {
                        break;
                    }
                    if (group != null && group.equals(preferSameGroup.apply(other))) {
                        String value = answerPicker.apply(other);
                        if (value != null && !value.isBlank()) {
                            options.add(value);
                        }
                    }
                }
            }
            for (EduPoem other : shuffled(poems, random)) {
                if (options.size() >= OPTION_SIZE) {
                    break;
                }
                String value = answerPicker.apply(other);
                if (value != null && !value.isBlank()) {
                    options.add(value);
                }
            }
            if (options.size() < OPTION_SIZE) {
                continue;
            }
            List<String> optionList = new ArrayList<>(options);
            Collections.shuffle(optionList, random);
            items.add(base(poem, mode, Map.of(
                    "question", question,
                    "options", optionList,
                    "answer", answer,
                    "answerIndex", optionList.indexOf(answer)
            )));
        }
        return items;
    }

    /**
     * 组装题目公共字段（题面来源信息），再并入模式特有字段。
     *
     * @param poem  来源诗
     * @param mode  模式标识
     * @param extra 模式特有字段
     * @return 题目
     */
    private static Map<String, Object> base(EduPoem poem, String mode, Map<String, Object> extra) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("mode", mode);
        item.put("poemId", poem.getPoemId());
        item.put("title", poem.getPoemTitle());
        item.put("author", poem.getPoemAuthor());
        item.put("dynasty", poem.getPoemDynasty());
        item.putAll(extra);
        return item;
    }

    /**
     * 随机打乱副本（不改动入参列表）。
     *
     * @param source 原始列表
     * @param random 随机源
     * @return 打乱后的新列表
     */
    private static <T> List<T> shuffled(List<T> source, Random random) {
        List<T> copy = new ArrayList<>(source);
        Collections.shuffle(copy, random);
        return copy;
    }

    /**
     * 是否汉字（用于炼字填空定位）。
     *
     * @param c 字符
     * @return 是汉字返回 true
     */
    private static boolean isHan(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }
}