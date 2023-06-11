<html>
<head>
<title></title>
    <#include "../include/easyui.ftl"/>
 
</head>
<body>

 <div id="tb3" style="padding:5px;height:auto">
                         <div>
	       		           
	                       <a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-standard-arrow-refresh" plain="true" onclick="refresh()">刷新</a>
	                       <span class="toolbar-item dialog-tool-separator"></span>                        
                           
                         </div> 
                       
  </div>
 <input type="hidden" id="databaseConfigId" value="${databaseConfigId}" >
 <input type="hidden" id="databaseName" value="${databaseName}" >
 <input type="hidden" id="tableName"  value="${tableName}">
<table id="dg3"></table> 
 
 
<script type="text/javascript" src="/static/js/showTableMess.js" >

</script>

</body>
</html>