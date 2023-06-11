<html>
<head>
    <title>TreeSoft数据库管理系统</title>
    <meta name="Keywords" content="Treesoft数据库管理系统">
    <meta name="Description" content="Treesoft数据库管理系统">
    <#include "../include/easyui.ftl"/>
    <#include "../include/codemirror.ftl"/>
    <#--    <%@ include file="/WEB-INF/views/include/easyui.jsp"%>-->
    <#--    <%@ include file="/WEB-INF/views/include/codemirror.jsp"%>-->
    <script src="/static/plugins/My97DatePicker/WdatePicker.js" type="text/javascript"></script>
    <script src="/static/plugins/easyui/jeasyui-extensions/jeasyui.extensions.datatime.js"
            type="text/javascript"></script>

    <!--导入首页启动时需要的相应资源文件(首页相应功能的 js 库、css样式以及渲染首页界面的 js 文件)-->
    <script src="/static/plugins/easyui/common/index.js" type="text/javascript"></script>
    <script src="/static/plugins/easyui/common/indexSearch.js" type="text/javascript"></script>
    <link href="/static/plugins/easyui/common/index.css" rel="stylesheet"/>
    <script src="/static/plugins/easyui/common/index-startup.js"></script>
    <script src="/static/plugins/sql/sql-formatter.min.js"></script>

    <link type="text/css" rel="stylesheet" href="/static/css/eclipse.css">
    <link type="text/css" rel="stylesheet" href="/static/css/codemirror.css"/>
    <link type="text/css" rel="stylesheet" href="/static/css/show-hint.css"/>
    <link rel="icon" href="/favicon.ico" mce_href="/favicon.ico" type="image/x-icon">
    <link rel="shortcut icon" href="/favicon.ico" mce_href="/favicon.ico" type="image/x-icon">

    <script type="text/javascript" src="/static/js/codemirror.js"></script>
    <script type="text/javascript" src="/static/js/sql.js"></script>
    <script type="text/javascript" src="/static/js/show-hint.js"></script>
    <script type="text/javascript" src="/static/js/sql-hint.js"></script>
    <style>
        .CodeMirror {
            border: 1px solid #cccccc;
            height: 98%;
        }
    </style>

</head>
<body>

<!-- 容器遮罩 -->
<div id="maskContainer">
    <div class="datagrid-mask" style="display: block;"></div>
    <div class="datagrid-mask-msg" style="display: block; left: 50%; margin-left: -52.5px;">
        正在加载...
    </div>
</div>

<div id="mainLayout" class="easyui-layout hidden" data-options="fit: true">

    <div data-options="region: 'west', title: '数据库选择', iconCls: 'icon-standard-map', split: true, minWidth: 200, maxWidth: 400"
         style="width: 220px; padding: 1px;">

        <div id="eastLayout" class="easyui-layout" data-options="fit: true">
            <div data-options="region: 'north', split: false, border: false" style="height: 34px;">
                <select class="combobox-f combo-f" style="width:200px;margin:5px; " id="databaseSelect"> </select>
            </div>

            <div data-options="region: 'center', border: false, title: '数据库', iconCls: 'icon-hamburg-database', tools: [{ iconCls: 'icon-hamburg-refresh', handler: function () {  dg.treegrid('reload'); } }]">
                <input id="pid" name="pid"/>
            </div>
        </div>

    </div>

    <div data-options="region: 'center'">
        <div id="mainTabs_tools" class="tabs-tool">
            <table>
                <tr>
                    <td><a id="mainTabs_jumpHome" class="easyui-linkbutton easyui-tooltip" title="跳转至主页选项卡"
                           data-options="plain: true, iconCls: 'icon-hamburg-home'"></a></td>
                    <td>
                        <div class="datagrid-btn-separator"></div>
                    </td>
                    <td><a id="mainTabs_toggleAll" class="easyui-linkbutton easyui-tooltip"
                           title="展开/折叠面板使选项卡最大化"
                           data-options="plain: true, iconCls: 'icon-standard-arrow-out'"></a></td>
                    <td>
                        <div class="datagrid-btn-separator"></div>
                    </td>
                    <td><a id="mainTabs_refTab" class="easyui-linkbutton easyui-tooltip" title="刷新当前选中的选项卡"
                           data-options="plain: true, iconCls: 'icon-standard-arrow-refresh'"></a></td>
                    <td>
                        <div class="datagrid-btn-separator"></div>
                    </td>
                    <td><a id="mainTabs_closeTab" class="easyui-linkbutton easyui-tooltip" title="关闭当前选中的选项卡"
                           data-options="plain: true, iconCls: 'icon-standard-application-form-delete'"></a></td>
                </tr>
            </table>
        </div>

        <div id="mainTabs" class="easyui-tabs"
             data-options="fit: true, border: false, showOption: true, enableNewTabMenu: true, tools: '#mainTabs_tools', enableJumpTabMenu: true">
            <div id="homePanel" data-options="title: '运行及展示', iconCls: 'icon-hamburg-home'">


                <div id="eastLayout" class="easyui-layout" data-options="fit: true">

                    <div data-options="region: 'north',split: true, border: false" style="height:280px">
                        <div id="operator" class="panel-header panel-header-noborder  " style="padding:5px;height:auto">
                            <div>
                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-run" plain="true"
                                   onclick="run();">执行(F8)</a>
                                <span class="toolbar-item dialog-tool-separator"></span>

                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-redo" plain="true"
                                   onclick="clearSQL()">清空(F7)</a>
                                <span class="toolbar-item dialog-tool-separator"></span>

                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-save" plain="true"
                                   onclick="saveSearchDialog()">SQL保存</a>
                                <span class="toolbar-item dialog-tool-separator"></span>

<#--                                <a href="javascript:void(0)" class="easyui-linkbutton"-->
<#--                                       iconCls="icon-hamburg-drawings" plain="true"-->
<#--                                       onclick="selectTheme('eclipse')">样式一</a>-->
<#--                                <span class="toolbar-item dialog-tool-separator"></span>-->

<#--                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-hamburg-equalizer"-->
<#--                                   plain="true" onclick="selectTheme('ambiance')">样式二</a>-->
<#--                                <span class="toolbar-item dialog-tool-separator"></span>-->

<#--                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-hamburg-showreel"-->
<#--                                   plain="true" onclick="selectTheme('erlang-dark')">样式三</a>-->
<#--                                <span class="toolbar-item dialog-tool-separator"></span>-->
<#--                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-edit" plain="true"-->
<#--                                   onclick="format()">SQL输入提示</a>-->
<#--                                <span class="toolbar-item dialog-tool-separator"></span>-->
                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-edit" plain="true"
                                     onclick="format()">美化</a>
                                <span class="toolbar-item dialog-tool-separator"></span>
                                <a  iconCls="icon-hamburg-config" plain="true" class="easyui-linkbutton" onclick="javascript:ShowConfigPage()"  title="数据库配置">
                                    设置
                                </a>
                                <span class="toolbar-item dialog-tool-separator"></span>
                                <span class="toolbar-item dialog-tool-separator"></span>
                                <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-tip" plain="true"
                                   title="F8   执行SQL语句 &#13;F7   清空SQL语句 &#13;可选中部分SQL执行 &#13;注释请以;分号结束"></a>
                                &nbsp;当前数据库：<span id="currentDbTitle"> </span>
                            </div>
                        </div>
                        <div style="width:100%;height:85%; ">

                            <input type="hidden" id="searchHistoryId">
                            <input type="hidden" id="searchHistoryName">
                            <textarea id="sqltextarea"
                                      style="margin:10px; font-size:14px;font-family: '微软雅黑';width:97%;height:95%; "> </textarea>
                        </div>
                    </div>

                    <div id="searchHistoryPanel"
                         data-options="region: 'center',split: true, collapsed: false,   border: false, title: '运行结果', iconCls: 'icon-standard-application-view-icons'  ">

                        <div id="searchTabs" class="easyui-tabs"
                             data-options="fit: true, border: false, showOption: true, enableNewTabMenu: true, enableJumpTabMenu: true">
                            <div id="searcHomePanel" data-options="title: '消息', iconCls: 'icon-hamburg-issue'">

                                <textarea id="executeMessage"
                                          style="margin:10px; font-size:14px;font-family: '微软雅黑';width:97%;height:95%; "
                                          readonly>   </textarea>

                            </div>
                        </div>

                    </div>

                </div>

            </div>
        </div>
    </div>

    <div data-options="region: 'east', title: '常用SQL', iconCls: 'icon-standard-book', split: true,collapsed: true, minWidth: 160, maxWidth: 500"
         style="width: 220px;">
        <div id="eastLayout" class="easyui-layout" data-options="fit: true">

            <div data-options="region: 'north', split: true, border: false" style="height: 220px;">
                <input id="sqlStudyList"/>
            </div>

            <div id="searchHistoryPanel"
                 data-options="region: 'center', split: true,  border: false, title: '我的SQL', iconCls: 'icon-standard-book-key', tools: [{ iconCls: 'icon-hamburg-refresh', handler: function () {  searchBG.treegrid('reload'); } }]">
                <input id="searchHistoryList"/>
            </div>
        </div>
    </div>

    <div data-options="region: 'south', title: '关于...', iconCls: 'icon-standard-information', collapsed: true, border: false"
         style="height: 70px;">
        <div style="color: #4e5766; padding: 6px 0px 0px 0px; margin: 0px auto; text-align: center; font-size:12px; font-family:微软雅黑;">
            TreeSoft<sup>®</sup>&nbsp;CopyRight@2018 福州青格软件 版权所有 <a href="http://www.treesoft.cn"
                                                                              target="_blank"
                                                                              style="text-decoration:none;">
                www.treesoft.cn </a> &nbsp;。
            &nbsp;
        </div>

    </div>
</div>

<div id='tb3' style='padding:5px;height:auto'>
    <div>
        <a href='#' class='easyui-linkbutton' iconCls='icon-add' plain='true'></a>
        <a href='#' class='easyui-linkbutton' iconCls='icon-edit' plain='true'></a>
    </div>
</div>

<div id="dlgg"></div>
<div id="addRow"></div>
<input type="hidden" id="currentTableName">

<div id="databaseMenu" class="easyui-menu" style="width:120px;">
    <div onclick="backupDatabase()" data-options="iconCls:'icon-table-save'">备份数据库</div>
    <div onclick="dropDatabase()" data-options="iconCls:'icon-table-delete'">删除数据库</div>
    <div class="menu-sep"></div>
    <div onclick="" data-options="iconCls:'icon-table-gear'">数据库属性</div>
</div>


<div id="tableMenu" class="easyui-menu" style="width:120px;">
    <div onclick="clickTable(tableName )" data-options="iconCls:'icon-table-edit'">打开表</div>
    <div onclick="designTable()" data-options="iconCls:'icon-table-gear'">设计表</div>
    <div onclick="addNewTable()" data-options="iconCls:'icon-table-add'">新增表</div>
    <div onclick="exportTable()" data-options="iconCls:'icon-table-go'">导出表</div>
    <div onclick="copyTable()" data-options="iconCls:'icon-table-lightning'">复制表</div>
    <div onclick="renameTable()" data-options="iconCls:'icon-table-relationship'">重命名</div>
    <div class="menu-sep"></div>
    <div onclick="dropTable()" data-options="iconCls:'icon-table-delete'">删除表</div>
    <div onclick="clearTable()" data-options="iconCls:'icon-table-row-delete'">清空表</div>
    <div onclick="tableMess()" data-options="iconCls:'icon-table-gear'">表信息</div>
</div>

<div id="viewMenu" class="easyui-menu" style="width:120px;">
    <div onclick="openView(tableName)" data-options="iconCls:'icon-search'">打开视图</div>
    <div onclick="showViewSQL(databaseName,tableName)" data-options="iconCls:'icon-edit'">设计视图</div>
    <div class="menu-sep"></div>
    <div onclick="" data-options="iconCls:'icon-tip'">视图信息</div>
</div>

<iframe id="exeframe" name="exeframe" style="display:none"></iframe>
<form id="form1" method="post" target="exeframe" action="/system/permission/i/exportExcel" accept-charset="utf-8"
      onsubmit="document.charset='utf-8'">
    <input type="hidden" id="sContent" name="sContent" value=""/>
</form>

<form id="form3" method="post" action="/system/permission/i/exportDataToSQLFromSQL" style="display:none">
    <input type="hidden" id="databaseConfigId" name="databaseConfigId">
    <input type="hidden" id="databaseName" name="databaseName">
    <input type="hidden" id="sql" name="sql">
    <input type="hidden" id="exportType" name="exportType">

</form>

</body>
<script type="text/javascript" src="/static/js/index.js"></script>
</html>
