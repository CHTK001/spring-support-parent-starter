const HOST = '/api/vuesql';
const URL = {
    KEYWORD: HOST+ '/table/keyword/{configId}',
    LIST_DATASOURCE: HOST + "/database/list",
    UPDATE_DATABASE: HOST + "/database/save",
    DELETE_DATABASE: HOST + "/database/delete/{configId}",
    GET_TABLE_INFO: HOST + "/table/{configId}"
}

export default URL;