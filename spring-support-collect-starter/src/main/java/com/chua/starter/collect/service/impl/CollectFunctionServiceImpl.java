// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.engine.CollectBuiltinInvoker;
import com.chua.starter.collect.engine.CollectFunctionRegistry;
import com.chua.starter.collect.engine.CollectFunctions;
import com.chua.starter.collect.engine.CollectGroovyRuntime;
import com.chua.starter.collect.engine.spi.CollectFunctionDef;
import com.chua.starter.collect.engine.spi.CollectFunctionProvider;
import com.chua.starter.collect.dto.CollectFunctionTestDTO;
import com.chua.starter.collect.entity.CollectFunction;
import com.chua.starter.collect.mapper.CollectFunctionMapper;
import com.chua.starter.collect.service.CollectFunctionService;
import com.chua.starter.collect.vo.CollectFunctionTestVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 采集自定义函数服务实现。
 *
 * <p>同时是引擎侧的 {@link CollectFunctionProvider}：注册表按名字来这儿取闭包源码，
 * 本类只负责把启用的行交出去。写入前一定先编译，编译不过不给存，避免把坏函数放进运行链。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect.function", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectFunctionServiceImpl extends ServiceImpl<CollectFunctionMapper, CollectFunction>
        implements CollectFunctionService, CollectFunctionProvider {

    /**
     * 合法函数名：小写字母开头的标识符，避开与类名和内置函数混淆。
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][A-Za-z0-9_]{0,63}$");

    /**
     * 启用。
     */
    private static final int ENABLED = 1;

    /**
     * "最近结果"列宽。
     */
    private static final int RESULT_LIMIT = 500;

    /**
     * 函数注册表，写入后让它立即重装载。
     */
    private final CollectFunctionRegistry registry;

    @Override
    public List<CollectFunction> listFunctions(String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        return list(Wrappers.<CollectFunction>lambdaQuery()
                .and(!text.isEmpty(), wrapper -> wrapper
                        .like(CollectFunction::getCollectFunctionName, text)
                        .or().like(CollectFunction::getCollectFunctionDescription, text))
                .orderByDesc(CollectFunction::getCollectFunctionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectFunction addFunction(CollectFunction function) {
        check(function);
        function.setCollectFunctionId(null);
        save(function);
        registry.invalidate();
        return function;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editFunction(CollectFunction function) {
        requireFunction(function.getCollectFunctionId());
        check(function);
        boolean changed = updateById(function);
        registry.invalidate();
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFunction(Integer functionId) {
        requireFunction(functionId);
        boolean changed = removeById(functionId);
        registry.invalidate();
        return changed;
    }

    @Override
    public CollectFunctionTestVO testFunction(CollectFunctionTestDTO dto) {
        if (dto == null || dto.collectFunctionId() == null) {
            throw new IllegalArgumentException("必须指定要试跑的函数");
        }
        CollectFunction function = requireFunction(dto.collectFunctionId());
        String functionName = function.getCollectFunctionName();
        Integer parameters = function.getCollectFunctionParameters();
        long begin = System.currentTimeMillis();
        try {
            List<Object> arguments = new ArrayList<>(
                    dto.arguments() == null ? List.of() : dto.arguments());
            CollectBuiltinInvoker.Outcome outcome =
                    registry.call(function.getCollectFunctionName(), arguments);
            if (!outcome.hit()) {
                CollectFunctionTestVO vo = new CollectFunctionTestVO(
                        false, functionName, parameters, null, null,
                        System.currentTimeMillis() - begin,
                        "函数未生效：可能已停用，或与内置函数同名被遮蔽");
                markResult(function, vo);
                return vo;
            }
            Object value = outcome.value();
            CollectFunctionTestVO vo = new CollectFunctionTestVO(
                    true, functionName, parameters,
                    CollectFunctions.stringify(value),
                    value == null ? "null" : value.getClass().getName(),
                    System.currentTimeMillis() - begin,
                    null);
            markResult(function, vo);
            return vo;
        } catch (Exception ex) {
            CollectFunctionTestVO vo = new CollectFunctionTestVO(
                    false, functionName, parameters, null, null,
                    System.currentTimeMillis() - begin,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            markResult(function, vo);
            return vo;
        }
    }

    @Override
    public List<CollectFunctionDef> runtimeFunctions() {
        List<CollectFunctionDef> defs = new ArrayList<>();
        for (CollectFunction function : list(Wrappers.<CollectFunction>lambdaQuery()
                .eq(CollectFunction::getCollectFunctionEnabled, ENABLED))) {
            String name = function.getCollectFunctionName();
            String script = function.getCollectFunctionScript();
            if (name == null || name.isBlank() || script == null || script.isBlank()) {
                continue;
            }
            Integer parameters = function.getCollectFunctionParameters();
            defs.add(new CollectFunctionDef(name.trim(), script, parameters == null ? 0 : parameters));
        }
        return defs;
    }

    @Override
    public List<CollectFunctionDef> loadEnabled() {
        return runtimeFunctions();
    }

    /**
     * 校验名称与函数体，并把推导出的形参个数回填。
     *
     * @param function 函数定义
     */
    private void check(CollectFunction function) {
        if (function == null) {
            throw new IllegalArgumentException("函数定义不能为空");
        }
        String name = function.getCollectFunctionName() == null
                ? "" : function.getCollectFunctionName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("函数名不能为空");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("函数名要从小写字母开头，只用字母数字下划线，不超过 64 位: " + name);
        }
        if (CollectBuiltinInvoker.isBuiltin(name)) {
            throw new IllegalArgumentException("函数名 " + name + " 与内置函数同名，请换一个名字");
        }
        function.setCollectFunctionName(name);
        if (count(Wrappers.<CollectFunction>lambdaQuery()
                .eq(CollectFunction::getCollectFunctionName, name)
                .ne(function.getCollectFunctionId() != null, CollectFunction::getCollectFunctionId,
                        function.getCollectFunctionId())) > 0) {
            throw new IllegalArgumentException("函数名 " + name + " 已经登记过了");
        }
        String script = function.getCollectFunctionScript() == null
                ? "" : function.getCollectFunctionScript().trim();
        if (script.isEmpty()) {
            throw new IllegalArgumentException("函数体不能为空");
        }
        String error = CollectGroovyRuntime.check(script);
        if (error != null) {
            throw new IllegalArgumentException("函数编译失败: " + error);
        }
        int parameters;
        try {
            parameters = CollectGroovyRuntime.closureParameters(script);
        } catch (Exception ex) {
            throw new IllegalArgumentException("函数编译失败: " + ex.getMessage(), ex);
        }
        if (parameters < 0) {
            throw new IllegalArgumentException("函数体必须是 { a, b -> ... } 形态的闭包");
        }
        function.setCollectFunctionScript(script);
        function.setCollectFunctionParameters(parameters);
        if (function.getCollectFunctionEnabled() == null) {
            function.setCollectFunctionEnabled(ENABLED);
        }
    }

    /**
     * 取函数行，不存在直接拒绝。
     *
     * @param functionId 函数ID
     * @return 函数行
     */
    private CollectFunction requireFunction(Integer functionId) {
        if (functionId == null) {
            throw new IllegalArgumentException("函数ID不能为空");
        }
        CollectFunction function = getById(functionId);
        if (function == null) {
            throw new IllegalArgumentException("函数不存在: " + functionId);
        }
        return function;
    }

    /**
     * 回写最近一次试跑结果。
     *
     * @param function 函数行
     * @param vo       试跑结果
     */
    private void markResult(CollectFunction function, CollectFunctionTestVO vo) {
        String text = Boolean.TRUE.equals(vo.success())
                ? "成功 " + vo.costMs() + "ms " + vo.result()
                : "失败: " + vo.errorMessage();
        CollectFunction update = new CollectFunction();
        update.setCollectFunctionId(function.getCollectFunctionId());
        update.setCollectFunctionLastResult(cut(text));
        update.setCollectFunctionLastTime(LocalDateTime.now());
        try {
            updateById(update);
        } catch (Exception ex) {
            log.warn("[collect] 函数试跑结果回写失败 function={} err={}",
                    function.getCollectFunctionName(), ex.getMessage());
        }
    }

    /**
     * 截断到列宽。
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private String cut(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= RESULT_LIMIT ? text : text.substring(0, RESULT_LIMIT - 1) + "…";
    }
}
