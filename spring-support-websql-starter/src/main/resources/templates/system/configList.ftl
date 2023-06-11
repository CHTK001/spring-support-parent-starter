<html>
<head>
<meta http-equiv="Expires" content="0">
<meta http-equiv="Pragma" content="no-cache">
<meta http-equiv="Cache-control" content="no-cache">
<meta http-equiv="Cache" content="no-cache">
<title></title>
    <#include "../include/easyui.ftl"/>

</head>
<body>
 <div id="tb3" style="padding:5px;height:auto">
     <div>
	    <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-add" plain="true" id="addRowButton"  onclick="addConfigForm()"> 添加 </a>
	    <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-remove" plain="true" id="delButton"   onclick="deleteConfig()">删除</a>
    	<span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-edit" plain="true" id="editRowButton" onclick="editConfigForm()">修改</a>
        <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-standard-arrow-refresh" plain="true" onclick="refresh()">刷新</a>
        <span class="toolbar-item dialog-tool-separator"></span>
        <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-tip" plain="true" title="可以同时配置多种数据库类型"></a>
     </div> 
  </div>
  <div id="dlgg" ></div>  
 <table id="dg3"></table>
 <script type="text/javascript" src="/static/js/configForm.js" ></script>
     <script type="text/javascript" src="/static/js/configList.js">

</script>
</body>
</html>