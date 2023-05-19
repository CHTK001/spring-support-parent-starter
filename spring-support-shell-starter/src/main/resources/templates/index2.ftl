<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Web Console</title>
    <link href="webjars/jquery.terminal.min.css" rel="stylesheet"/>

    <script src="webjars/jquery-3.2.1.min.js"></script>
    <script src="webjars/jquery.terminal.min.js"></script>
    <script src="webjars/unix_formatting.js"></script>
    <script src="webjars/autocomplete_menu.js"></script>
    <script src="webjars/ansi_up.js"></script>
    <script src="webjars/reconnecting-websocket.min.js"></script>
    <style>
        .xterm-viewport,
        .xterm-screen {
            min-height: calc(80vh);
        }

        .terminal .xterm-rows {
            position: absolute;
            left: 0;
            top: 0;
            width: 100% !important;
        }

        .xterm {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
        }

        h1, h2 {
            font-weight: normal;
        }

        ul {
            list-style-type: none;
            padding: 0;
        }

        li {
            display: inline-block;
            margin: 0 10px;
        }

        a {
            color: #42b983;
        }

        form {
            margin: 10px;
        }

        body .shell {
            width: 400px;
            height: 200px;
        }

        body .shell .terminal {
            height: calc(100% - 29px);
            --size: 1.1;
            /*
             * padding bottom 0 on terminal and margin
             * on .cmd will be in version 2.0.1
             * that fixes FireFox issue
             */
            padding-bottom: 0;
        }

        body .shell .terminal .cmd {
            margin-bottom: 10px;
        }

        .shell .typed-cursor, .shell .cursor {
            background: transparent;
        }

        .shell > .status-bar .title {
            cursor: move;
        }

        /*
         * fix to shell.js to center title to free space
         */
        .shell.windows .status-bar .title {
            right: 106px;
        }

        @supports (--css: variables) {
            .shell .terminal {
                --color: #aaa;
            }

            .shell.ubuntu .terminal {
                --background: #300924;
            }

            .shell.osx .content.terminal {
                --background: #222;
            }

            .shell.light .content.terminal {
                --background: white;
                --color: black;
            }

            /*
            * windows and custom are the last ones so
            * they don't get overwritten by light
            */
            .shell.windows .content.terminal {
                --background: black;
                --color: white;
                --animation: terminal-underline;
            }

            .shell.custom .content.terminal {
                --background: #292929;
                --color: #aaa;
            }
        }

        /*
         * overwrite shell.js style because shell.js
         * selectors are stronger then terminal ones
         */
        .cmd span.cursor {
            animation: none;
            width: auto;
            background-color: var(--background, #000);
        }

        .shell:not(.light) terminal.content,
        .shell.osx.dark .content,
        .shell.ubuntu:not(.light) .content {
            background-color: var(--background, #222) !important;
        }

        .shell .terminal.content {
            font-size: 12px;
        }

        .cmd {
            background-color: inherit;
        }

        @supports (--css: variables) {
            .shell .terminal.content {
                font-size: calc(var(--size, 1) * 12px);
            }
        }

        /* fix for Firefox */
        .terminal > .resizer, .terminal > .font .resizer {
            visibility: visible;
            pointer-events: none;
        }

        .terminal::-webkit-scrollbar-track {
            border: 1px solid var(--color, #aaa);
            background-color: var(--background);
        }

        .terminal::-webkit-scrollbar {
            width: 10px;
            background-color: var(--background);
        }

        .terminal::-webkit-scrollbar-thumb {
            background-color: var(--color, #aaa);
        }
    </style>

</head>
<body>
<div class="console dark ubuntu" id="xterm" style="min-height: calc(100vh)"></div>
</body>
<script>
    var ansi_up = new AnsiUp;

    var socket = new ReconnectingWebSocket("ws://" + location.host + "/channel/shell");
    //连接打开事件
    socket.onopen = function () {
        console.log("Socket 已打开");
    };
    window.help = [];

    let $term
    //收到消息事件
    socket.onmessage = function (msg) {
        let data = msg.data;
        if (data.startsWith("@")) {
            help = data.substring(1).split(',')
            return
        }
        if (!!$term) {
            $term.echo($.terminal.from_ansi(msg.data, {
                unixFormatting: {
                    escapeBrackets: false,
                    ansiParser: {},
                    ansiArt: true
                }
            }));//把接收的数据写到这个插件的屏幕上
        }
    };
    //连接关闭事件
    socket.onclose = function () {
    };
    //发生了错误事件
    socket.onerror = function () {
    }
    $.terminal.prism_formatters = {
        prompt: true,
        echo: true,
        animation: true, // will be supported in version >= 2.32.0
        command: true
    };
    $('#xterm').terminal(function (command) {
        $term = this;
        if (command !== '') {
            socket.send(command);
        }
    }, {
        greetings: '终端',
        pipe: true,
        ansi: true,
        'font-size': '15px',
        name: 'term',
        height: 200,
        width: 'auto',
        prompt: '$ > ',
        unixFormatting: {
            escapeBrackets: false,
            ansiParser: {},
            ansiArt: true
        },
        autocompleteMenu: true,
        completion: help
    });

</script>
</html>