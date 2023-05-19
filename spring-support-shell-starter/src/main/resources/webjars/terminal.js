var socket = new ReconnectingWebSocket("ws://" + location.host + "/channel/shell");
//连接打开事件
socket.onopen = function () {
    console.log("Socket 已打开");
};
let successFunction;

function initial() {
    let vue = new Vue({
        el: '#app',
        methods: {
            onExecCmd(key, command, success, failed) {
                new FTerminal(this, command, success, failed).execute();
                console.log(command, key)
            },
            changeHeight() {
                this.height = window.innerHeight + 'px';
            },
            searchHandler1() {
                debugger
            }

        },
        mounted() {
            //监听自适应
            window.addEventListener('resize', this.changeHeight);
            this.changeHeight();
        },
        beforeDestroy() {
            // 移除绑定的listenResizeFn事件监听
            window.removeEventListener("resize", this.changeHeight);
        }
    });
    let app = Vue.use(vue);
    app.component('vue-web-terminal', {});
}

let flash;
let _data, _type;
//  监听 ctrl + c 事件
function endFlush() {
    if (!!_data) {
        window['vue-web-terminal'].api.pushMessage('my-terminal', {
            type: _type,
            content: _data
        })
    }
}
$(document).unbind('keydown').bind('keydown', function (e) {

    if (e.ctrlKey && e.keyCode == 67) {
        if (!!flash) {
            try {
                flash.finish();
            } catch (e) {
            }
            flash = null;
        }

        if (!!socket) {
            socket.send("@close");
        }

        endFlush();
        console.log("ctrl + c")
        return false;
    }
});

function createType(data) {
    let substring = data.substring(0, data.indexOf(" "));
    if (!!substring) {
        return substring;
    }
    substring = data.substring(0, data.indexOf("{"));
    if (!!substring) {
        return substring;
    }
    return "ansi";
}

function createData(type, data) {
    data = data.substring(type.length);
    if (type == 'table' || type == 'json') {
        return JSON.parse(data);
    }
    return data;
}

socket.onmessage = function (msg) {
    let data = msg.data;
    if (data.startsWith("@help")) {
        window['commandStore'] = JSON.parse(data.substring(5));
        initial()
        return
    }
    if (data.startsWith("@flushStart")) {
        if (!!flash) {
            flash.finish();
            flash = null;
        }
        flash = new window['vue-web-terminal'].Flash();
        successFunction(flash);
        return
    }

    if (data.startsWith("@flushEnd")) {
        if (!!flash) {
            try {
                flash.finish();
                endFlush();
            } catch (e) {
            }
        }
        flash = null;
        return
    }

    if (data.startsWith("@flush")) {
        if (!!flash) {
            let type = "ansi";
            data = data.substring(6).trim();
            if (data.startsWith("@")) {
                data = data.substring(1);
                type = createType(data);
                data = createData(type, data);
            }
            flash.flush(data);
            _type = type;
            _data = data;
        }
        return
    }




    let type = "ansi";
    if (data.startsWith("@")) {
        data = data.substring(1);
        type = createType(data);
        data = createData(type, data);
    }

    if (!data || (Object.prototype.toString.call(data) == '[object String]' && data.startsWith("$"))) {
        if (!!successFunction) {
            successFunction({
                type: type,
                tag: '成功',
                content: ''
            });
        }
        return;
    }
    if (!!successFunction) {

        successFunction({
            type: type,
            tag: '成功',
            content: data
        });
    }

};
//连接关闭事件
socket.onclose = function () {
};
//发生了错误事件
socket.onerror = function () {
}
const FTerminal = function (dom, command, success, failed) {
    successFunction = success;
    return {
        execute: function () {
            socket.send(command);
        }
    }
}