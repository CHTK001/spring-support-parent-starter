<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Web Console</title>
    <script src="/webjars/jquery-3.2.1.min.js"></script>
    <script src="/webjars/vue.min.js"></script>
    <script src="/webjars/vue-web-terminal.js"></script>
    <script src="/webjars/reconnecting-websocket.min.js"></script>
    <script src="/webjars/terminal.js"></script>

    <style>
        body, html, #app {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
        }
    </style>
</head>
<body>

<div id="app">
    <terminal title="终端" style="height: 100%;width: 100%; top: 0; left: 0" name="my-terminal"
              @exec-cmd="onExecCmd"></terminal>
</div>

<script>

</script>
</body>
</html>