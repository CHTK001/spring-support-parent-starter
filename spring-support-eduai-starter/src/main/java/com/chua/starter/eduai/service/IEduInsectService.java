package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.eduai.model.EduInsect;

import java.util.List;
import java.util.Map;

/**
 * 昆虫图鉴服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduInsectService extends IService<EduInsect> {

    /**
     * 按目 / 变态类型 / 难度上限获取昆虫列表
     *
     * @param order         所属目 slug，为空表示不限
     * @param metamorphosis 变态类型，为空表示不限
     * @param maxDifficulty 最大难度，为空表示不限
     * @param count         数量
     * @return 昆虫列表
     */
    List<EduInsect> listByFilter(String order, String metamorphosis, Integer maxDifficulty, int count);

    /**
     * 生成「昆虫识图」配对消除游戏数据
     *
     * <p>每条 gameItem 同时给出中文名与学名，前端把两者拆成两张牌面，
     * 玩家点中「名」与「学名」同属一条即消除，逻辑与成语消消乐一致。</p>
     *
     * @param order         所属目 slug，为空表示不限
     * @param metamorphosis 变态类型，为空表示不限
     * @param maxDifficulty 最大难度，为空表示不限
     * @param count         物种数量
     * @return 游戏数据：items / totalItems / order / metamorphosis / maxDifficulty
     */
    Map<String, Object> generateInsectGame(String order, String metamorphosis, Integer maxDifficulty, int count);

    /**
     * 校验「中文名 - 学名」是否属于同一物种
     *
     * @param insectId 昆虫 ID
     * @param latin    学名
     * @return 是否匹配
     */
    boolean validateInsectPair(Long insectId, String latin);

    /**
     * 统计各目物种数
     *
     * @return 行数据，每行含 order 与 speciesCount
     */
    List<Map<String, Object>> countByOrder();

    /**
     * 取图鉴名录用的轻量列表（全量，不随机）
     *
     * <p>只带名录与筛选需要的字段，不含 facts/hotspots 等大 JSON，
     * 供前端左栏一次拉全后本地筛选，避免每切一次筛选就请求后端。</p>
     *
     * <p>额外带一份 {@code anchors}（该物种全部标注点锚点的字符串列表），
     * 让前端能直接判断「哪些虫有同一个部位」，用于标注卡上的部位跳转，
     * 不必为 63 个物种各发一次详情请求。</p>
     *
     * @return 轻量物种列表
     */
    List<Map<String, Object>> listLibrary();

    /**
     * 取单个物种的完整讲解视图：物种详情（JSON 已解析）+ 深度讲解与测验
     *
     * <p>前端切换物种时只需这一个请求即可把 3D 展台与讲解面板一起喂饱。</p>
     *
     * @param insectCode 物种英文标识（slug）
     * @return 含 insect 与 guide 两个键；物种不存在时 insect 为 null
     */
    Map<String, Object> getStageView(String insectCode);

    /**
     * 把物种实体转成详情视图：四个 JSON 文本列解析成数组
     *
     * <p>详情接口与展台接口共用这一份转换，避免同一个「物种详情」出现两种形态
     * （一个是 JSON 字符串、一个是数组），前端要按调用来源分别处理。</p>
     *
     * @param insect 物种实体
     * @return 详情视图
     */
    Map<String, Object> toDetailView(EduInsect insect);
}
