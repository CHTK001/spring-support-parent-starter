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