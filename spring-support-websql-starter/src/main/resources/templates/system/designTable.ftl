<html>
<head>
    <title></title>
    <#include "../include/easyui.ftl"/>
    <script src="/static/plugins/My97DatePicker/WdatePicker.js" type="text/javascript"></script>

</head>
<body>

<div id="tb3" style="padding:5px;height:auto">
    <div>

        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-table-row-insert" plain="true"
           id="addRowButton" onclick="addRow2()"> 添加</a>
        <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-table-row-delete" plain="true"
           id="delButton" onclick="deleteTableColumn()">删除</a>
        <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-table-edit" plain="true" id="editRowButton"
           onclick="editRow2()">修改</a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-hamburg-up" plain="true" id="editRowButton"
           onclick="MoveUp()">上移</a>
        <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-hamburg-down" plain="true"
           id="editRowButton" onclick="MoveDown()">下移</a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-standard-arrow-refresh" plain="true"
           onclick="refresh()">刷新</a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a href="javascript:void(0)" class="easyui-linkbutton" plain="true">&nbsp;</a>
        <span class="toolbar-item dialog-tool-separator"></span>

        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-ok" plain="true" id="editRowButton"
           onclick="saveRow()">保存</a>
        <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-cancel" plain="true" id="editRowButton"
           onclick="cancelChange()">取消</a>
        <span class="toolbar-item dialog-tool-separator"></span>

    </div>

</div>

<input type="hidden" id="databaseName" value="${databaseName}">
<input type="hidden" id="tableName" value="${tableName}">
<input type="hidden" id="databaseConfigId" value="${databaseConfigId}">
<table id="dg3"></table>
<script type="text/javascript" src="/static/js/designTable.js"></script>
</body>
</html>