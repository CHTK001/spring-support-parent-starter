<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Web Console</title>
    <link rel="stylesheet" href="webjars/style.css"/>
    <link rel="stylesheet" href="webjars/xterm.css"/>
    <script src="webjars/xterm.js"></script>
    <script src="webjars/xterm-addon-fit.js"></script>
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
    </style>

</head>
<body>
<div class="console" id="xterm" style="min-height: calc(100vh)"></div>
</body>
<script>
    const terminalPrefix = ``;
    let line_inputs = [],
        history = [],
        upDownKeyPressedCount = -1;
    var socket = new ReconnectingWebSocket("ws://" + location.host + "/channel/shell");
    //连接打开事件
    socket.onopen = function () {
        console.log("Socket 已打开");
    };

    let help = [];
    //收到消息事件
    socket.onmessage = function (msg) {
        let data = msg.data;
        if (data.startsWith("@")) {
            help = data.substring(1).split(',')
            return
        }
        term.write("\r\n" + data);//把接收的数据写到这个插件的屏幕上
    };
    //连接关闭事件
    socket.onclose = function () {
    };
    //发生了错误事件
    socket.onerror = function () {
    }
    var commandKey = [];

    var term = new Terminal({
        rendererType: "canvas", //渲染类型
        convertEol: true, //启用时，光标将设置为下一行的开头
        scrollback: 100, //终端中的回滚量
        cursorStyle: "block", //光标样式
        cursorBlink: true, //光标闪烁
        cols: 210,
        rows: 36,
        theme: {
            foreground: 'yellow', //字体
            background: '#060101', //背景色
            cursor: 'help',//设置光标
        },
        bellStyle: 'sound',
        rightClickSelectsWord: true,
        screenReaderMode: true,
        allowProposedApi: true,
        LogLevel: 'debug',
    });

    let content = '';
    term.open(document.getElementById("xterm"), true);
    writeTermLine = function (value) {
        term.write("$  " + value);
    }
    writeTerm = function (value) {
        term.write("\r\n " + value);
        term.newLineprompt();
    }
    let position = 0;
    term.focus();
    term.onKey((key, ev) => {
        const kc = key.domEvent.keyCode;

        if (kc === 13) {
            const input = line_inputs.join("").toString();
            history.push(input);
            if (input === "clear" || input === "cls") {
                return term.clear(), term.newLineprompt(), (line_inputs = []);
            } else if (input === "history") {
                term.newLine()
                for (const historyElement in history) {
                    writeTerm(history[historyElement]);
                }
                return term.newLine(), line_inputs = [], term.newLineprompt();
            } else {
                socket.send(input), line_inputs = [];
            }
        } else if (kc === 46 || kc === 35 || kc === 36) {
            // DELETE: 46, END: 35, HOME: 36, INSERT: 45, '-': 45
            return true;
        } else if (kc === 38 || kc === 40) {
            // upArrow > 38 , downArrow > 40
            if (parseInt(upDownKeyPressedCount) >= parseInt(history.length)) {
                upDownKeyPressedCount = parseInt(history.length - 1);
            } else if (parseInt(upDownKeyPressedCount) < 0) {
                upDownKeyPressedCount = 0;
            } else {
            }

            if (kc === 38) {
                // up history
                if (history.length > 0) {
                    const value = history[upDownKeyPressedCount];
                    return (
                        (line_inputs = value.split("")),
                            term.write('\x1b[2K\r'),
                            writeTermLine(value),
                            upDownKeyPressedCount++
                    );
                }
            } else if (kc === 40) {
                // down history
                if (history.length > 0) {
                    const value = history[upDownKeyPressedCount];
                    return (
                        (line_inputs = value.split("")),
                            term.write('\x1b[2K\r'),
                            writeTermLine(value),
                            upDownKeyPressedCount--
                    );
                }
            } else {
                // prevent left-right arrow
                return true;
            }
        } else if (kc === 8) {
            if (line_inputs.length === 0) {
                return false;
            }
            if (position <= 0) {
                return false;
            }
            position--
            // Backspace: remove previous character when key pressed
            return (term.write("\b \b") , false,
                    line_inputs.pop()
            );
        } else if (kc === 9) {
            let filter = help.filter(item => {
                return item.startsWith(line_inputs.join(""));
            });
            term.write('\r\n' + filter.join(' '))
        } else {
            if (kc == 37) {
                if (position <= 0) {
                    return false;
                }
                position--;
            } else {

                position++
            }
            line_inputs.push(key.key), term.write(key.key);
        }

    });
    term.prompt = () => {
        term.write('\r\n$ ');
    };
    term.newLine = () => {
        term.write("\r\n");
    };

    term.newLineprompt = () => {
        term.newLine();
        term.prompt();
    };
    const fitAddon = new FitAddon.FitAddon();
    term.loadAddon(fitAddon);
    fitAddon.fit();

</script>
</html>