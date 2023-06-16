export function guid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = Math.random() * 16 | 0,
            v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

export function isNewSame(item, arr) {
    for (const objElement of Object.keys(item)) {
        if (arr[objElement] !== item[objElement]) {
            return !1;
        }
    }
    return !0;
}

export function copy(obj) {
    const rs = {};
    for (const objElement of Object.keys(obj)) {
        rs[objElement] = obj[objElement];
    }

    return rs;
}

export function sformat(url, ...param) {
    let newUrl = url;
    if(param.length == 1) {
        param = param[0];
        if(param instanceof Array) {
            if(param.length == 0) {
                param = {};
            } else {
                param = param[0];
            }
        }
        for (let key of Object.keys(param)) {
            newUrl = newUrl.replace("{" + key + "}", param[key]);
        }
    } else {
        for (const paramElement of param) {
            for (let key of Object.keys(paramElement)) {
                newUrl = newUrl.replace("{" + key + "}", paramElement[key]);
            }
        }
    }


    return newUrl;
}