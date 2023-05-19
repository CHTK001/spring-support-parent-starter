<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>统一认证中心</title>

    <#import "../common/common.macro.ftl" as netCommon>
    <@netCommon.commonStyle />
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/toastr/toastr.min.css">
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/plugins/iCheck/square/blue.css">
    <style>
        input {
            height: 34px !important;
        }
    </style>
</head>
<body class="hold-transition login-page">
<input id="msg" value="${msg!''}" hidden/>
<input id="rq" value="${request.contextPath}" hidden/>
<div class="login-box">
    <div class="login-logo">
        <a><b></b>SSO</a>
    </div>
    <form method="post" action="${request.contextPath}/doLogin" onsubmit="enc()">
        <div class="login-box-body">
            <p class="login-box-msg">统一认证中心</p>
            <div class="form-group has-feedback">
                <input type="text" name="username" class="form-control" placeholder="Please input username."
                       value="user" maxlength="50">
                <span class="glyphicon glyphicon-envelope form-control-feedback"></span>
            </div>
            <div class="form-group has-feedback">
                <input type="password" name="passwd" class="form-control" placeholder="Please input password."
                       value="123456" maxlength="50">
                <span class="glyphicon glyphicon-lock form-control-feedback"></span>
            </div>

            <div class="form-group has-feedback hidden">
                <input type="text" name="type" class="form-control" value="web" maxlength="50">
            </div>

            <div class="form-group has-feedback hidden">
                <input type="text" name="data" class="form-control" value="web" maxlength="50">
            </div>

            <div class="form-group" style="padding-bottom: 2px;">
                <input type="text" name="code" id="code" class="form-control" style="float: left; width: 50%"
                       placeholder="请输入验证码.">
                <img style="float: left; width: calc(50% - 2px); height: 34px; margin-left: 2px"
                     src="${request.contextPath}/captcha.jpg" onclick="refreshCode()"/>
                <p style="clear: both"></p>
            </div>

            <#if errorMsg?exists>
                <p style="color: red;">${errorMsg}</p>
            </#if>

            <div class="row">
                <div class="col-xs-8">
                    <div class="checkbox icheck" style="padding-left: 20px">
                        <label>
                            <input type="checkbox" name="ifRemember">记住密码
                        </label>
                    </div>
                </div><!-- /.col -->
                <div class="col-xs-4">
                    <input type="hidden" name="redirect_url" value="${redirect_url!''}"/>
                    <button type="submit" class="btn btn-primary btn-block btn-flat">登录</button>
                </div>
            </div>
        </div>
    </form>
</div>

</body>
<@netCommon.commonScript />
<script src="${request.contextPath}/static/plugins/toastr/toastr.min.js"></script>
<script src="${request.contextPath}/static/adminlte/plugins/iCheck/icheck.min.js"></script>
<script src="${request.contextPath}/static/oauth/md5.js"></script>
<script src="${request.contextPath}/static/oauth/sha512.js"></script>
<script src="${request.contextPath}/static/oauth/login.1.js"></script>
</html>