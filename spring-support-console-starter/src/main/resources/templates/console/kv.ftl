<!DOCTYPE html>
<html lang="zh">
<head>
    <meta charset="UTF-8">
    <title>键值设置 - 控制台</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 24px; color: #333; }
        table { border-collapse: collapse; width: 100%; margin: 16px 0; }
        th, td { border: 1px solid #ddd; padding: 8px 10px; text-align: left; font-size: 14px; }
        th { background: #f5f5f5; }
        form.inline { display: inline; }
        input { padding: 4px 6px; margin: 2px; }
        nav a { margin-right: 16px; }
    </style>
</head>
<body>
<h1>管理控制台 - 通用键值运行设置</h1>
<nav>
    <a href="/console/features">功能开关</a>
    <a href="/console/grays">灰度规则</a>
    <a href="/console/kv">键值设置</a>
</nav>

<h3>新增 / 修改</h3>
<form action="/console/api/kv" method="post">
    <input type="text" name="key" placeholder="键" required/>
    <input type="text" name="value" placeholder="值" required style="width:300px"/>
    <button type="submit">保存</button>
</form>

<table>
    <tr><th>键</th><th>值</th><th>操作</th></tr>
    <#list kv as key, value>
    <tr>
        <td>${key}</td>
        <td>${value}</td>
        <td>
            <form class="inline" action="/console/api/kv/delete" method="post">
                <input type="hidden" name="key" value="${key}"/>
                <button type="submit">删除</button>
            </form>
        </td>
    </tr>
    </#list>
</table>

<p>说明：键值为用户数据，注解指纹重置不影响键值表。</p>
</body>
</html>
