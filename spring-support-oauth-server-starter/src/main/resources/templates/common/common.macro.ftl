<#macro commonStyle>

<#-- favicon -->
    <link rel="icon" href="${request.contextPath}/static/favicon.ico"/>

    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <!-- Tell the browser to be responsive to screen width -->
    <meta content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" name="viewport">
    <!-- Bootstrap -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/bootstrap/css/bootstrap.min.css">
    <!-- Font Awesome -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/font-awesome/css/font-awesome.min.css">
    <!-- Ionicons -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/Ionicons/css/ionicons.min.css">
    <!-- AdminLTE Skins. Choose a skin from the css/skins folder instead of downloading all of them to reduce the load. -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/dist/css/skins/_all-skins.min.css">

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/toastr/toastr.min.css">
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/sweetalert2/sweetalert2.min.css">
    <link rel="stylesheet"
          href="${request.contextPath}/static/plugins/tempusdominus-bootstrap-4/css/tempusdominus-bootstrap-4.min.css">
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/icheck-bootstrap/icheck-bootstrap.min.css">
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/daterangepicker/daterangepicker.css">
    <!-- pace -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/PACE/themes/blue/pace-theme-flash.css">
    <!-- Theme style -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/dist/css/adminlte.css">
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/bootstrap-select/css/bootstrap-select.min.css">

    <style>
        /*- scrollbar -*/
        ::-webkit-scrollbar {
            width: 5px;
            height: 5px;
        }

        ::-webkit-scrollbar-thumb {
            background-color: #999;
            -webkit-border-radius: 5px;
            border-radius: 5px;
        }

        ::-webkit-scrollbar-thumb:vertical:hover {
            background-color: #666;
        }

        ::-webkit-scrollbar-thumb:vertical:active {
            background-color: #333;
        }

        ::-webkit-scrollbar-button {
            display: none;
        }

        ::-webkit-scrollbar-track {
            background-color: #f1f1f1;
        }
    </style>
</#macro>

<#macro commonScript>
    <!-- jQuery -->
    <script src="${request.contextPath}/static/adminlte/jquery/jquery.min.js"></script>
    <script src="${request.contextPath}/static/plugins/popper/popper.min.js"></script>
    <!-- Bootstrap -->
    <script src="${request.contextPath}/static/adminlte/bootstrap/js/bootstrap.min.js"></script>
    <script src="${request.contextPath}/static/adminlte/bootstrap/js/bootstrap.bundle.min.js"></script>
    <!-- FastClick -->
    <script src="${request.contextPath}/static/adminlte/fastclick/fastclick.js"></script>
    <!-- AdminLTE App -->
    <script src="${request.contextPath}/static/adminlte/dist/js/adminlte.min.js"></script>
    <!-- jquery.slimscroll -->
    <script src="${request.contextPath}/static/adminlte/jquery-slimscroll/jquery.slimscroll.min.js"></script>

    <!-- pace -->
    <script src="${request.contextPath}/static/adminlte/PACE/pace.min.js"></script>
<#-- jquery cookie -->
    <script src="${request.contextPath}/static/plugins/jquery/jquery.cookie.js"></script>
<#-- jquery.validate -->
    <script src="${request.contextPath}/static/plugins/jquery/jquery.validate.min.js"></script>

<#-- layer -->
    <script src="${request.contextPath}/static/plugins/layer/layer.js"></script>
    <script src="${request.contextPath}/static/plugins/sweetalert2/sweetalert2.all.min.js"></script>
    <script src="${request.contextPath}/static/plugins/toastr/toastr.min.js"></script>
    <script src="${request.contextPath}/static/adminlte/moment/moment.min.js"></script>
    <script src="${request.contextPath}/static/adminlte/moment/moment-with-locales.min.js"></script>
    <script src="${request.contextPath}/static/plugins/daterangepicker/daterangepicker.js"></script>
    <script src="${request.contextPath}/static/plugins/tempusdominus-bootstrap-4/js/tempusdominus-bootstrap-4.min.js"></script>
    <script src="${request.contextPath}/static/plugins/bootstrap-select/js/bootstrap-select.min.js"></script>

</#macro>

<#macro commonFooter >
    <footer class="main-footer">
    </footer>
</#macro>