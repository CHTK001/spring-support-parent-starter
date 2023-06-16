export function isNewSame(obj, arr) {
    for(let item of arr) {
        let isSame = !0;
        item = item.newData;
        if(item.action === 'add') {
            p: for (const objElement of Object.keys(item)) {
                if(obj[objElement] !== item[objElement]) {
                    isSame = !1;
                    break p;
                }
            }
        }


        if(isSame) {
            return !0;
        }
    }

    return !1;
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