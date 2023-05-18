layui.config({ base: 'js/modules/' }).extend({
  numinput: 'textool/textool.min'
}).use(['form', 'textool'], function() {
  var $ = layui.$, form = layui.form, textool = layui.textool;
  textool.init({
    // 根据元素 id 值单独渲染，为空默认根据 class='layext-text-tool' 批量渲染
    eleId: null,
    // 批量设置输入框最大长度，可结合 eleId 单独设置最大长度
    maxlength: -1,
    // 初始化回调，无参
    initEnd: $.noop,
    // 显示回调，参数为当前输入框和工具条面板的 jQuery 对象
    showEnd: $.noop,
    // 隐藏回调，参数为当前输入框和工具条面板的 jQuery 对象
    hideEnd: $.noop,
    // 初始化展开，默认展开，否则收起
    initShow: true,
    // 启用指定工具模块，默认依次为字数统计、复制内容、重置内容、清空内容，按数组顺序显示
    tools: ['count', 'copy', 'reset', 'clear'],
    // 工具按钮提示类型，默认为 'title' 属性，可选 'laytips'，使用 layer 组件的吸附提示， 其他值不显示提示
    tipType: 'title',
    // 吸附提示背景颜色
    tipColor: '#01AAED',
    // 对齐方向，默认右对齐，可选左对齐 'left'
    align: 'right',
    // 工具条字体颜色
    color: '#666666',
    // 工具条背景颜色
    bgColor: '#FFFFFF',
    // 工具条边框颜色
    borderColor: '#E6E6E6',
    // 工具条附加样式类名
    className: '',
    // z-index
    zIndex: 19891014
  });
});