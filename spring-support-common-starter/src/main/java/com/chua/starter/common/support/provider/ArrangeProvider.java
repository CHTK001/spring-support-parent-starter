package com.chua.starter.common.support.provider;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.database.AutoMetadata;
import com.chua.common.support.database.orm.conditions.SqlWrappers;
import com.chua.common.support.database.repository.Repository;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.lang.arrange.Arrange;
import com.chua.common.support.lang.arrange.ArrangeHandler;
import com.chua.common.support.lang.arrange.DelegateArrangeFactory;
import com.chua.common.support.lang.page.Page;
import com.chua.common.support.spi.Option;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.common.support.pojo.*;
import com.chua.starter.common.support.result.Result;
import org.springframework.beans.BeansException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.sql.DataSource;
import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.chua.starter.common.support.configuration.CacheConfiguration.DEFAULT_CACHE_MANAGER;
import static com.chua.starter.common.support.constant.Constant.DEFAULT_EXECUTOR;

/**
 * 编排接口
 * @author CH
 */
@RestController
@RequestMapping("${plugin.arrange.context-path:/arrange}")
public class ArrangeProvider implements ApplicationContextAware {

    private Repository<SysArrange> repository;
    private Repository<SysArrangeNode> nodeRepository;
    private Repository<SysArrangeEdge> edgeRepository;
    private Repository<SysArrangeLogger> loggerRepository;

    @Resource(name = DEFAULT_EXECUTOR)
    private Executor executor;
    /**
     * 所有任务
     * @return 所有任务
     */
    @GetMapping("/option")
    public Result<List<Option<String>>> option() {
        return Result.success(ServiceProvider.of(ArrangeHandler.class).options());
    }
    /**
     * 分页查询
     * @param page 页码
     * @return 结果
     */
    @GetMapping("/page")
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'arrange'")
    public Result<Page<SysArrange>> page(Page<SysArrange> page) {
        return Result.success(repository.page(page));
    }
    /**
     * 保存/更新
     * @param json 保存
     * @return 结果
     */
    @PostMapping("/saveOrUpdateNode")
    @Transactional
    public Result<Integer> saveOrUpdateNode(@RequestBody JSONObject json) {
        String arrangeId = json.getString("arrangeId");
        if(!repository.exist(SqlWrappers.<SysArrange>lambdaQuery().eq(SysArrange::getArrangeId, arrangeId))) {
            return Result.failed("任务名称不存在");
        }
        List<SysArrangeNode> nodes = BeanUtils.copyPropertiesList(json.getJSONArray("nodes"), SysArrangeNode.class);
        nodeRepository.delete(SqlWrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, arrangeId));
        nodeRepository.saveBatch(nodes);

        List<SysArrangeEdge> edges = BeanUtils.copyPropertiesList(json.getJSONArray("edges"), SysArrangeEdge.class);
        edgeRepository.delete(SqlWrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, arrangeId));
        edgeRepository.saveBatch(edges);

        return Result.success();
    }

    /**
     * 运行
     *
     * @param arrangeId arrangeId
     * @return 结果
     */
    @GetMapping("/run")
    @Transactional
    public Result<String> run(@RequestParam("arrangeId") String arrangeId) {
        String tid = IdUtils.createTid();
        DelegateArrangeFactory arrangeFactory = DelegateArrangeFactory.create(new SysArrangeListener(tid, arrangeId, loggerRepository));
        List<SysArrangeNode> nodes = nodeRepository.list(SqlWrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, arrangeId));
        List<SysArrangeEdge> edges = edgeRepository.list(SqlWrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, arrangeId));
        doRegister(arrangeFactory, edges, nodes);
        return Result.success();
    }

    private void doRegister(DelegateArrangeFactory arrangeFactory, List<SysArrangeEdge> edges, List<SysArrangeNode> nodes) {
        Map<String, List<String>> depends = new LinkedHashMap<>();
        for (SysArrangeEdge arrangeEdge : edges) {
            depends.computeIfAbsent(arrangeEdge.getTargetNode(), it -> new LinkedList<>()).add(arrangeEdge.getSourceNode());
        }
        List<Arrange> aDefault = nodes.stream().map(it -> {
            Arrange arrange = new Arrange();
            arrange.setArrangeName(it.getId());
            arrange.setArrangeType("default");
            arrange.setArrangeDepends(Joiner.on(",").withPrefix("default:").join(depends.get(it.getId())));
            arrange.setHandler(ServiceProvider.of(ArrangeHandler.class).getNewExtension(it.getRealId()));
            return arrange;
        }).collect(Collectors.toList());
        for (Arrange arrange : aDefault) {
            arrangeFactory.register(arrange);
        }

        executor.execute(() -> {
            arrangeFactory.run(Collections.emptyMap());
        });
    }

    /**
     * 保存/更新
     *
     * @param arrangeId arrangeId
     * @return 结果
     */
    @GetMapping("/nodeAndEdge")
    @Transactional
    public Result<JSONObject> nodeAndEdge(@RequestParam("arrangeId") String arrangeId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("nodes", nodeRepository.list(SqlWrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, arrangeId)));
        jsonObject.put("edges", edgeRepository.list(SqlWrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, arrangeId)));
        return Result.success(jsonObject);
    }

    /**
     * 保存/更新
     *
     * @param sysArrange 保存
     * @return 结果
     */
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'arrange'")
    @PostMapping("/saveOrUpdate")
    public Result<Integer> saveOrUpdate(@RequestBody SysArrange sysArrange) {
        if (repository.exist(SqlWrappers.<SysArrange>lambdaQuery()
                .eq(SysArrange::getArrangeName, sysArrange.getArrangeName())
                .ne(null != sysArrange.getArrangeId(), SysArrange::getArrangeId, sysArrange.getArrangeId()))
        ) {
            return Result.failed("任务名称不能重复");
        }
        return Result.success(repository.saveOrUpdate(sysArrange));
    }

    /**
     * 删除
     * @param id id
     * @return 结果
     */
    @GetMapping("/delete")
    @Transactional
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'arrange'")
    public Result<Boolean> delete(@RequestParam("id") String id) {
        if (repository.deleteById(id) > 0) {
            edgeRepository.delete(SqlWrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, id));
            nodeRepository.delete(SqlWrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, id));
        }
        return Result.success(true);
    }
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        AutoMetadata autoMetadata = AutoMetadata.builder().build();
        this.repository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrange.class);
        this.nodeRepository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrangeNode.class);
        this.edgeRepository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrangeEdge.class);
        this.loggerRepository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrangeLogger.class);
    }
}
