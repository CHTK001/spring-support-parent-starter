const HOST = '/api/vuesql';
const URL = {
    KEYWORD: HOST+ '/table/keyword/{configId}',
    DATASOURCE: HOST + "/database/list",
    GET_TABLE_INFO: HOST + "/table/{configId}"
}

export default URL;