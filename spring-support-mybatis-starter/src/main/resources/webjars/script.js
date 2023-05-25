//jQuery time
var current_fs, next_fs, previous_fs; //fieldsets
var left, opacity, scale; //fieldset properties which we will animate
var animating; //flag to prevent quick multi-click glitches
function initial() {


    $(".next").click(function () {
        if (animating) return false;
        animating = true;

        current_fs = $(this).parent();
        next_fs = $(this).parent().next();

        //activate next step on progressbar using the index of next_fs
        $("#progressbar li").eq($("fieldset").index(next_fs)).addClass("active");

        //show the next fieldset
        next_fs.show();
        //hide the current fieldset with style
        current_fs.animate({opacity: 0}, {
            step: function (now, mx) {
                //as the opacity of current_fs reduces to 0 - stored in "now"
                //1. scale current_fs down to 80%
                scale = 1 - (1 - now) * 0.2;
                //2. bring next_fs from the right(50%)
                left = (now * 50) + "%";
                //3. increase opacity of next_fs to 1 as it moves in
                opacity = 1 - now;
                current_fs.css({
                    'transform': 'scale(' + scale + ')',
                    'position': 'absolute'
                });
                next_fs.css({'left': left, 'opacity': opacity});
            },
            duration: 800,
            complete: function () {
                current_fs.hide();
                animating = false;
            },
            //this comes from the custom easing plugin
            easing: 'easeInOutBack'
        });
    });

    $(".previous").click(function () {
        if (animating) return false;
        animating = true;

        current_fs = $(this).parent();
        previous_fs = $(this).parent().prev();

        //de-activate current step on progressbar
        $("#progressbar li").eq($("fieldset").index(current_fs)).removeClass("active");

        //show the previous fieldset
        previous_fs.show();
        //hide the current fieldset with style
        current_fs.animate({opacity: 0}, {
            step: function (now, mx) {
                //as the opacity of current_fs reduces to 0 - stored in "now"
                //1. scale previous_fs from 80% to 100%
                scale = 0.8 + (1 - now) * 0.2;
                //2. take current_fs to the right(50%) - from 0%
                left = ((1 - now) * 50) + "%";
                //3. increase opacity of previous_fs to 1 as it moves in
                opacity = 1 - now;
                current_fs.css({'left': left});
                previous_fs.css({'transform': 'scale(' + scale + ')', 'opacity': opacity});
            },
            duration: 800,
            complete: function () {
                current_fs.hide();
                animating = false;
            },
            //this comes from the custom easing plugin
            easing: 'easeInOutBack'
        });
    });
    $(".submit").click(function () {

        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/generator', true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.responseType = "blob";    // 返回类型blob
        xhr.onload = function () {
            // 请求完成
            if (this.status === 200) {
                let disposition = xhr.getResponseHeader('Content-Disposition');

                // 返回200
                var blob = this.response;
                var reader = new FileReader();
                reader.readAsDataURL(blob);
                reader.onload = function (e) {
                    // 转换完成，创建一个a标签用于下载
                    var a = document.createElement('a');
                    a.download = disposition.replace("attachment;filename=", "");
                    a.href = e.target.result;
                    $("body").append(a);
                    a.click();
                    $(a).remove();
                }
            }
        };
        xhr.send(JSON.stringify({
            'name': $("#selectLeo").find('option:selected').text(),
            'author': $('#author').val(),
            'kotlin': $('#kotlin').is(":checked"),
            'swagger': $('#swagger').is(":checked"),
            'springdoc': $('#springdoc').is(":checked"),
            'include': $('#tables').text(),
            'packages': {
                'parent': $('#parent').val(),
                'moduleName': $('#moduleName').val(),
                'entity': $('#entity').val(),
                'service': $('#service').val(),
                'serviceImpl': $('#serviceImpl').val(),
                'mapper': $('#mapper').val(),
                'xml': $('#xml').val(),
                'controller': $('#controller').val(),
            },
            'entity': {}
        }));
        $('#identifier').modal('hide');

        return false;
    })
}
