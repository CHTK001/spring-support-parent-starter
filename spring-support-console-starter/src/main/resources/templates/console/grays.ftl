<!DOCTYPE html>
<html lang="zh">
<head>
    <meta charset="UTF-8">
    <title>灰度规则管理 - 控制台</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 24px; color: #333; }
        table { border-collapse: collapse; width: 100%; margin: 16px 0; }
        th, td { border: 1px solid #ddd; padding: 8px 10px; text-align: left; font-size: 14px; }
        th { background: #f5f5f5; }
        form.inline { display: inline; }
        input { padding: 4px 6px; margin: 2px; width: 80px; }
        input.wide { width: 160px; }
        nav a { margin-right: 16px; }
        .override { color: #1565c0; font-weight: bold; }
    </style>
</head>
<body>
<h1>管理控制台 - 灰度规则（@ApiGray）</h1>
<nav>
    <a href="/console/features">功能开关</a>
    <a href="/console/grays">灰度规则</a>
    <a href="/console/kv">键值设置</a>
</nav>

<table>
    <tr>
        <th>灰度标识</th><th>注解比例</th><th>覆盖比例</th><th>角色</th><th>用户</th><th>请求头</th><th>SpEL</th><th>映射路径</th><th>操作</th>
    </tr>
    <#list grays as g>
    <tr>
        <td>${g.pattern}</td>
        <td>${g.percentage}</td>
        <td class="override">${g.overridePercentage!'—'}</td>
        <td>${g.roles!''}</td>
        <td>${g.users!''}</td>
        <td>${g.headers!''}</td>
        <td>${g.rule!''}</td>
        <td>${g.patterns!''}</td>
        <td>
            <#if g.hasOverride>
            <form class="inline" action="/console/api/grays/delete" method="post">
                <input type="hidden" name="pattern" value="${g.pattern}"/>
                <button type="submit">移除覆盖</button>
            </form>
            <#else>
            <form class="inline" action="/console/api/grays" method="post">
                <input type="hidden" name="pattern" value="${g.pattern}"/>
                <input type="number" name="percentage" placeholder="比例"/>
                <input class="wide" type="text" name="roles" placeholder="角色(逗号分隔)"/>
                <button type="submit">覆盖</button>
            </form>
            </#if>
        </td>
    </tr>
    </#list>
</table>

<p>说明：比例 0-100，空则沿用注解；覆盖写入 SQLite，注解指纹变化时启动重置。</p>
</body>
</html>
