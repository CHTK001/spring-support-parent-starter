let loadSetting = $.loadSetting();
let types = loadSetting.type;

let $select = $('select');

$('[ type="text"]').slider({
    tooltip: 'always'
});
$('[ type="text"]').on("slide", function(slideEvt) {
    load();
});


for (const type in types) {
    $select.append('<option value="' + type + '">' + type + '</option>');
}

$select.on("change", function () {
    load();
})

$("[name='my-ul']").bootstrapSwitch({
    onSwitchChange: function (event, state) {
        load();
    }
});
$("[name='my-checkbox']").bootstrapSwitch({
    onSwitchChange: function (event, state) {
        load();
    }
});