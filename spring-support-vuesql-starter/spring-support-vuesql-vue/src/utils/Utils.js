export function sformat(url, param) {
    if(param instanceof Array) {
        if(param.length == 0) {
            param = {};
        } else {
            param = param[0];
        }
    }
    let newUrl = url;
    for (let key of Object.keys(param)) {
        newUrl = newUrl.replace("{" + key + "}", param[key]);
    }
    return newUrl;
}