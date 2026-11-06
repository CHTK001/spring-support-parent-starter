package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectText;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 开始节点：声明种子 URL。
 *
 * <p>把多行 URL 求值后写成集合变量 {@code url}（{@code ${url}} 即整条列表，
 * {@code ${url[0]}} 取首条、{@code ${url.size()}} 取条数），下游抓取节点用
 * {@code ${url}} 作为循环集合即可展开成多条采集分支；多源分叉时各支路分别写
 * {@code ${url[0]}}、{@code ${url[1]}} 即可，引擎原生支持多出边。</p>
 *
 * <p>不再有 {@code urlList}：一个变量名既满足「只留一个 url」的要求，
 * 又能承载多源（List）。同时按统一变量模型把产出写进「输出名」
 * （默认 {@code data}），所以 {@code ${data}} 同样指向这份种子列表。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class CollectStartExecutor implements CollectShapeExecutor {

    /**
     * 种子 URL 参数名。
     */
    public static final String URLS = "urls";

    /**
     * 集合变量名参数名（输出名）。
     */
    public static final String OUTPUT = "outputVariable";

    /**
     * 集合变量默认名。
     */
    public static final String DEFAULT_OUTPUT = "data";

    @Override
    public String supportShape() {
        return "start";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "开始", "来源", "play",
                "声明种子 URL，每行一条，支持 ${} 表达式");
        meta.setOutputVariable("写入 url（列表），${url} 取整表、${url[0]} 取首条、${url.size()} 取条数；输出名默认 data");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(URLS, "种子 URL", "textarea", "每行一个 URL，可用 ${变量} 拼接"),
                CollectShapeMetas.outputVariable("开始节点产出写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        List<Object> urls = new ArrayList<>();
        // 逐行切分前先归一「字面换行」：种子值若被写成一串字面 \n，切不开就会把
        // 上千条 URL 当成一条超长 URL 交给抓取节点（CollectText 里有现场记录）
        for (String line : CollectText.lines(node.param(URLS))) {
            String url = CollectExpression.evalText(line, variables).trim();
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        // url 固定写 List：${url} 即整条列表，${url[N]} 取第 N 条，${url.size()} 取条数
        variables.put("url", urls);
        String output = node.param(OUTPUT).isEmpty() ? DEFAULT_OUTPUT : node.param(OUTPUT).trim();
        variables.put(output, urls);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object output = variables.get("url");
        int size = output instanceof List ? ((List<?>) output).size() : 0;
        return "种子 " + size + " 条";
    }
}
