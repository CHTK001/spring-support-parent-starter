<!DOCTYPE html>
<html lang="zh">
<head>
    <meta charset="UTF-8">
    <title>功能开关管理 - 控制台</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 24px; color: #333; }
        table { border-collapse: collapse; width: 100%; margin: 16px 0; }
        th, td { border: 1px solid #ddd; padding: 8px 10px; text-align: left; font-size: 14px; }
        th { background: #f5f5f5; }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; }
        .on { background: #e6f7e6; color: #2e7d32; }
        .off { background: #fdecea; color: #c62828; }
        form.inline { display: inline; }
        input, select { padding: 4px 6px; margin: 2px; }
        nav a { margin-right: 16px; }
    </style>
</head>
<body>
<h1>管理控制台 - 功能开关（@ApiFeature）</h1>
<nav>
    <a href="/console/features">功能开关</a>
    <a href="/console/grays">灰度规则</a>
    <a href="/console/kv">键值设置</a>
</nav>

<table>
    <tr>
        <th>功能标识</th><th>默认</th><th>生效</th><th>禁用提示</th><th>禁用状态码</th><th>描述</th><th>映射路径</th><th>操作</th>
    </tr>
    <#list features as f>
    <tr>
        <td>${f.featureId}</td>
        <td><span class="badge ${f.defaultEnabled?string('on','off')}">${f.defaultEnabled?string('启用','禁用')}</span></td>
        <td><span class="badge ${f.effective?string('on','off')}">${f.effective?string('启用','禁用')}</span></td>
        <td>${f.disabledMessage!''}</td>
        <td>${f.disabledStatus}</td>
        <td>${f.description!''}</td>
        <td>${f.patterns!''}</td>
        <td>
            <#if f.hasOverride>
            <form class="inline" action="/console/api/features/delete" method="post">
                <input type="hidden" name="featureId" value="${f.featureId}"/>
                <button type="submit">移除覆盖</button>
            </form>
            <#else>
            <form class="inline" action="/console/api/features" method="post">
                <input type="hidden" name="featureId" value="${f.featureId}"/>
                <select name="enabled">
                    <option value="true">启用</option>
                    <option value="false">禁用</option>
                </select>
                <input type="text" name="disabledMessage" placeholder="禁用提示(可选)"/>
                <input type="number" name="disabledStatus" value="-1" style="width:60px"/>
                <button type="submit">覆盖</button>
            </form>
            </#if>
        </td>
    </tr>
    </#list>
</table>

<p>说明：覆盖写入 SQLite 持久化；注解指纹变化（@ApiFeature 更新）时启动重置覆盖库。</p>
</body>
</html>
