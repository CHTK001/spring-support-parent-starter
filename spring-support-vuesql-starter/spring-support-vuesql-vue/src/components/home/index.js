import $ from 'jquery'
import request from 'axios'

let sql_autocomplete_in_progress = !1,
    sql_autocomplete = !1;

const Home = {
    editor: undefined,
    initial: function () {
        this.editor = document.getElementById("sqltextarea");
        this.editor.on("inputRead", this.codemirrorAutocompleteOnInputRead);
        this.editor.refresh();
        this.editor.focus();
        $(this.editor.getWrapperElement()).bind("keydown", catchKeypressesFromSqlTextboxes);
    },

    codemirrorAutocompleteOnInputRead: function (a) {
        sql_autocomplete_in_progress || a.options.hintOptions.tables && sql_autocomplete || (sql_autocomplete ?
            (a.options.hintOptions.tables = sql_autocomplete, a.options.hintOptions.defaultTable = sql_autocomplete_default_table) :
            (a.options.hintOptions.defaultTable = "", sql_autocomplete_in_progress = !0,
                    request.get(URL.KEYWORD)
                        .then(xhr => {
                            sql_autocomplete = [];
                            a.options.hintOptions.tables = xhr.data;
                        }).finish(() => {
                        sql_autocomplete_in_progress = !1
                    })
            ));
        if (!a.state.completionActive) {
            var b = a.getCursor();
            b = a.getTokenAt(b);
            var c = "";
            b.string.match(/^[.`\w@]\w*$/) && (c = b.string);
            0 < c.length && CodeMirror.commands.autocomplete(a)
        }
    }

}

export default Home;