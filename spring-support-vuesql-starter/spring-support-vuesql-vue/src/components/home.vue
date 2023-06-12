<template>
  <div className="json-editor">
    <textarea ref="textarea"/>
  </div>
</template>

<script>
import CodeMirror from 'codemirror'
import 'codemirror/lib/codemirror.css'
import 'codemirror/mode/sql/sql.js'
// 替换主题这里需修改名称
import 'codemirror/theme/idea.css'

export default {
  props: {
    value: {
      type: String,
      required: true
    },
    height: {
      type: String,
      required: true
    }
  },
  data() {
    return {
      editor: false
    }
  },
  watch: {
    value(value) {
      const editorValue = this.editor.getValue()
      if (value !== editorValue) {
        this.editor.setValue(this.value)
      }
    },
    height(value) {
      this.editor.setSize('auto', this.height)
    }
  },
  mounted() {
    this.editor = CodeMirror.fromTextArea(this.$refs.textarea, {
      mode: 'text/x-mysql', //语言
      lineNumbers: true, //是否在编辑器左侧显示行号
      lint: true,
      matchBrackets: true, // 括号匹配
      lineWrapping: true,
      tabSize: 2, // 缩进格式
      styleActiveLine: true, // 高亮选中行
      cursorHeight: 0.9,
      // 替换主题这里需修改名称
      theme: 'idea',
      //是否为只读,如果为"nocursor" 不仅仅为只读 连光标都无法在区域聚焦
      readOnly: false,
    })
    this.editor.setSize('auto', this.height)
    this.editor.setValue(this.value)
  },
  methods: {
    getValue() {
      return this.editor.getValue()
    }
  }
}
</script>

<style scoped>
.json-editor {
  height: 100%;
}

.json-editor >>> .CodeMirror {
  font-size: 14px;
  /* overflow-y:auto; */
  font-weight: normal
}

.json-editor >>> .CodeMirror-scroll {
}

.json-editor >>> .cm-s-rubyblue span.cm-string {
  color: #F08047;
}
</style>
