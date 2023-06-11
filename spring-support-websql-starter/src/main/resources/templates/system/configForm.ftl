<html>
<head>
<title>config</title>
	<#include "../include/easyui.ftl"/>

</head>
<body>
	<div>
		<form id="mainform" action="/system/permission/i/configUpdate" method="post">
			<input id="id" name="id" type="hidden" value="${config.id!'' }" />
			<table class="formTable">
				<tr>
					<td>名称：</td>
					<td>
						<input id="name" name="name" type="text" value="${config.name!'' }" class="easyui-validatebox" data-options="width: 150" />
					</td>
				</tr>
				<tr>
					<td>数据库类型：</td>
					<td>
						<select id="databaseType" name="databaseType" class="esayui-combobox" style="width: 150px">
							<option value="MySql" >MySql</option>
							<option value="MariaDB">MariaDB</option>
							<option value="Oracle" >Oracle</option>
							<option value="PostgreSQL" >PostgreSQL</option>
							<option value="MSSQL" >SQL Server</option>
						</select>
					</td>
				</tr>
				<tr>
					<td>默认数据库：</td>
					<td>
						<input id="databaseName" name="databaseName" type="text" value="${config.databaseName!'' }" class="easyui-validatebox"
							data-options="width: 150,required:'required'" />
						<a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-tip" plain="true" title="Oracle请填写实例名"></a>
					</td>
				</tr>
				<tr>
					<td>IP地址：</td>
					<td>
						<input id="ip" name="ip" type="text" value="${config.ip!'' }" class="easyui-validatebox"
							data-options="width: 150,required:'required',validType:'length[3,80]'" />
					</td>
				</tr>
				<tr>
					<td>端口：</td>
					<td>
						<input id="port" name="port" type="text" value="${config.port!'' }" class="easyui-validatebox" data-options="width: 150,required:'required'" />
						<a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-tip" plain="true"
							title=" MySql 默认端口为:3306 &#13;MariaDB默认端口为:3306 &#13;Oracle默认端口为:1521 &#13;PostgreSQL默认端口:5432 &#13;MSSQL默认端口为:1433"></a>
					</td>
				</tr>
				<tr>
					<td>用户名：</td>
					<td>
						<input id="userName" name="userName" type="text" value="${config.userName!'' }" class="easyui-validatebox" data-options="width:150" />
					</td>
				</tr>
				<tr>
					<td>密 码：</td>
					<td>
						<input id="password" name="password" type="password" value="${config.password!'' }" class="easyui-validatebox" data-options="width:150" />
						<a href="javascript:void(0)" class="easyui-linkbutton" iconCls="icon-tip" plain="true" title="此处为空时，不更新原始密码"></a>
					</td>
				</tr>
				<tr>
					<td>是否默认：</td>
					<td>
						<select id="isdefault" name="isdefault" class="esayui-combobox" style="width: 150px">
							<option value="0" >否</option>
							<option value="1" >是</option>
						</select>
					</td>
				</tr>
				<tr>
					<td></td>
					<td>
						<span id="mess2"> </span>
					</td>
				</tr>
			</table>
		</form>
	</div>
</body>
<script type="text/javascript" src="/static/js/configForm.js" >

</script>
</html>