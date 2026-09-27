package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.model.EduInsect;
import com.chua.starter.eduai.model.dto.InsectGuideSaveDTO;
import com.chua.starter.eduai.service.IEduInsectGuideService;
import com.chua.starter.eduai.service.IEduInsectService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 昆虫图鉴控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/insect")
@RequiredArgsConstructor
@Tag(name = "昆虫图鉴")
public class EduInsectController {

    private final IEduInsectService insectService;

    private final IEduInsectGuideService guideService;

    /**
     * 图鉴名录：全量轻量列表，供前端左栏一次拉全后本地按目/变态类型筛选
     *
     * @return 轻量物种列表
     */
    @Operation(summary = "获取昆虫图鉴名录")
    @GetMapping("/all")
    public ReturnResult<List<Map<String, Object>>> all() {
        return ReturnResult.ok(insectService.listLibrary());
    }

    /**
     * 展台视图：物种详情（JSON 已解析）+ 深度讲解与测验
     *
     * <p>前端切换物种时只需这一个请求，即可同时喂饱 3D 展台与讲解面板。</p>
     *
     * @param insectCode 物种英文标识（slug），如 rhinoceros-beetle
     * @return 含 insect 与 guide 两个键
     */
    @Operation(summary = "获取物种展台视图（详情+讲解+测验）")
    @GetMapping("/stage")
    public ReturnResult<Map<String, Object>> stage(@RequestParam String insectCode) {
        return ReturnResult.ok(insectService.getStageView(insectCode));
    }

    /**
     * 获取单个物种的深度讲解与测验
     *
     * @param insectCode 物种英文标识（slug）
     * @return 讲解视图，无数据时为 null
     */
    @Operation(summary = "获取物种深度讲解与测验")
    @GetMapping("/guide/{insectCode}")
    public ReturnResult<Map<String, Object>> guide(@PathVariable String insectCode) {
        return ReturnResult.ok(guideService.getGuideView(insectCode));
    }

    /**
     * 保存物种的深度讲解与测验
     *
     * <p>按 insectCode 一对一覆盖：已存在则更新，不存在则新建。
     * 讲解为空数组是合法的 —— 新物种可以先只建物种信息，讲解后补。</p>
     *
     * @param dto 讲解内容，其中 insectCode 必填
     * @return 是否保存成功
     */
    @Operation(summary = "保存物种深度讲解与测验")
    @PostMapping("/guide/save")
    public ReturnResult<Boolean> saveGuide(@RequestBody InsectGuideSaveDTO dto) {
        if (dto == null || dto.getInsectCode() == null || dto.getInsectCode().isBlank()) {
            throw new BusinessException("insectCode 不能为空");
        }
        return ReturnResult.ok(guideService.saveGuide(dto));
    }

    /**
     * 获取昆虫列表
     *
     * @param order         所属目 slug，可为空
     * @param metamorphosis 变态类型，可为空
     * @param maxDifficulty 最大难度，可为空
     * @param count         数量
     * @return 昆虫列表
     */
    @Operation(summary = "获取昆虫列表")
    @GetMapping("/list")
    public ReturnResult<List<EduInsect>> list(
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String metamorphosis,
            @RequestParam(required = false) Integer maxDifficulty,
            @RequestParam(defaultValue = "20") int count) {
        return ReturnResult.ok(insectService.listByFilter(order, metamorphosis, maxDifficulty, count));
    }

    /**
     * 统计各目物种数
     *
     * @return 行数据，每行含 order 与 speciesCount
     */
    @Operation(summary = "统计各目物种数")
    @GetMapping("/orders")
    public ReturnResult<List<Map<String, Object>>> orders() {
        return ReturnResult.ok(insectService.countByOrder());
    }

    /**
     * 生成昆虫识图配对消除游戏数据
     *
     * @param order         所属目 slug，可为空
     * @param metamorphosis 变态类型，可为空
     * @param maxDifficulty 最大难度，可为空
     * @param count         物种数量
     * @return 游戏数据
     */
    @Operation(summary = "生成昆虫识图游戏数据")
    @GetMapping("/game/generate")
    public ReturnResult<Map<String, Object>> generateGame(
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String metamorphosis,
            @RequestParam(required = false) Integer maxDifficulty,
            @RequestParam(defaultValue = "10") int count) {
        return ReturnResult.ok(insectService.generateInsectGame(order, metamorphosis, maxDifficulty, count));
    }

    /**
     * 校验「中文名 - 学名」配对
     *
     * @param params 参数，不允许为 null
     * @return 是否匹配
     */
    @Operation(summary = "验证昆虫名与学名配对")
    @PostMapping("/game/validate")
    public ReturnResult<Boolean> validatePair(@RequestBody Map<String, Object> params) {
        Object rawId = params.get("insectId");
        if (rawId == null) {
            throw new BusinessException("insectId 不能为空");
        }
        Long insectId = Long.valueOf(rawId.toString());
        String latin = (String) params.get("latin");
        return ReturnResult.ok(insectService.validateInsectPair(insectId, latin));
    }

    /**
     * 获取昆虫详情
     *
     * <p>返回与 /stage 同一形态的详情视图：facts / hotspots / lifecycle / relatives
     * 都已解析成数组，调用方不必自己 JSON.parse。</p>
     *
     * @param insectId 昆虫 ID
     * @return 昆虫详情，不存在时为 null
     */
    @Operation(summary = "获取昆虫详情")
    @GetMapping("/detail/{insectId}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable Long insectId) {
        EduInsect insect = insectService.getById(insectId);
        if (insect == null) {
            return ReturnResult.ok(null);
        }
        EduUserContext.checkReadable(insect.getOwnerUserId());
        return ReturnResult.ok(insectService.toDetailView(insect));
    }

    /**
     * 新增或更新昆虫
     *
     * @param insect 昆虫实体
     * @return 是否成功
     */
    @Operation(summary = "保存昆虫")
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody EduInsect insect) {
        if (insect.getInsectId() == null) {
            insect.setOwnerUserId(EduUserContext.requireUserId());
        } else {
            EduInsect exists = insectService.getById(insect.getInsectId());
            if (exists == null) {
                throw new BusinessException("昆虫不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            insect.setOwnerUserId(exists.getOwnerUserId());
        }
        return ReturnResult.ok(insectService.saveOrUpdate(insect));
    }

    /**
     * 删除昆虫
     *
     * <p>连带删除该物种的深度讲解：讲解按 insect_code 关联，
     * 不一起删会留下悬挂记录，重建同名 slug 时还会把旧讲解带回来。</p>
     *
     * @param insectId 昆虫 ID
     * @return 是否成功
     */
    @Operation(summary = "删除昆虫")
    @DeleteMapping("/delete/{insectId}")
    public ReturnResult<Boolean> delete(@PathVariable Long insectId) {
        EduInsect exists = insectService.getById(insectId);
        if (exists == null) {
            return ReturnResult.ok(false);
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        guideService.removeByCode(exists.getInsectCode());
        return ReturnResult.ok(insectService.removeById(insectId));
    }
}
