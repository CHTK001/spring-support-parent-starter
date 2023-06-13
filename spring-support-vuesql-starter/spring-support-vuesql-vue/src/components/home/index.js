import request from 'axios'
import URL from '@/config/url'
import {sformat} from '@/utils/Utils'
import {format} from 'sql-formatter';

let sql_autocomplete_in_progress = !1,
    sql_autocomplete = !1;

const Home = {
    editor: undefined,
    CodeMirror: undefined,
    currentDatabaseData: undefined,
    currentTableData: undefined,
    code: undefined,
    resultSet: undefined,
    setEditor(editor) {
        this.editor = editor;
    },
    setResultSet(resultSet) {
        this.resultSet = resultSet;
    },
    setCode(code) {
        this.code = code;
    },
    /**
     * 数据库页面
     */
    addConfig: () => {
        debugger
    },

    initial: function (currentDatabaseData, currentTableData, CodeMirror) {
        if (!!currentTableData && !!currentTableData.value) {
            this.currentTableData = currentTableData.value;
        }

        if (currentDatabaseData && !!currentDatabaseData.value) {
            this.currentDatabaseData = currentDatabaseData.value;
        }

        if(!!CodeMirror) {
            this.CodeMirror = CodeMirror;
        }
    },
    codemirrorAutocompleteOnInputRead: function (a) {
        sql_autocomplete_in_progress || a.options.hintOptions.tables && sql_autocomplete || (sql_autocomplete ?
            (a.options.hintOptions.tables = sql_autocomplete, a.options.hintOptions.defaultTable = sql_autocomplete_default_table) :
            (a.options.hintOptions.defaultTable = "", sql_autocomplete_in_progress = !0,(
                    request.get(sformat(URL.KEYWORD, Home.currentDatabaseData))
                        .then(({data}) => {
                            sql_autocomplete = [];
                            a.options.hintOptions.tables = data.data;
                        }).finally(() => {
                        sql_autocomplete_in_progress = !1
                    })
            )));
        if (!a.state.completionActive) {
            var b = a.getCursor();
            b = a.getTokenAt(b);
            var c = "";
            b.string.match(/^[.`\w@]\w*$/) && (c = b.string);
            0 < c.length && Home.CodeMirror.commands.autocomplete(a)
        }
    },

    clearSQL() {
        this.editor.setValue('');
    },
    run() {
        let value = this.editor.getSelection() || this.editor.getValue();
        this.resultSet.value.run(value);
    },
    formatSQL() {
        let selection = this.editor.getSelection();
        if(!!selection) {
            this.editor.replaceSelection(format(selection));
            return false;
        }
        let value = this.editor.getValue();
        this.editor.setValue(format(value));
    }
}

export default Home;