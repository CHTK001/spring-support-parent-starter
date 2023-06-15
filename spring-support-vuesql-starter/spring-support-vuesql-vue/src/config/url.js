const HOST = '/api/vuesql';
const URL = {
    OPEN_TABLE: HOST + '/table/open/{configId}/{realName}',
    KEYWORD: HOST + '/table/keyword/{configId}',
    EXECUTE: HOST + '/table/execute/{configId}',
    EXPLAIN: HOST + '/table/explain/{configId}',
    LIST_DATASOURCE: HOST + "/database/list",
    UPDATE_DATABASE: HOST + "/database/save",
    DELETE_DATABASE: HOST + "/database/delete/{configId}",
    DATABASE_TYPES: HOST + "/database/type",
    GET_TABLE_INFO: HOST + "/table/{configId}",
    UPDATE_TABLE: HOST + "/table/update"
}

export default URL;