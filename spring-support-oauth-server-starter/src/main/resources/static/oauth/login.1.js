let msg = $('#msg').val();
let rq = $('#rq').val();
if (!!msg) {
    toastr.info(msg);//提醒
}

const refreshCode = function () {
    $('img')[0].src = rq + "/captcha.jpg?ver=" + Math.ceil(Math.random() * 1000000);
}

$(function () {

    $('#gitee').off("click");
    $('#gitee').on("click", function (e) {
        $.get(rq + "/gitee" + window.location.search, xhr => {
            window.location.href = xhr;
        })
    });
    // input iCheck
    $('input').iCheck({
        checkboxClass: 'icheckbox_square-blue',
        radioClass: 'iradio_square-blue',
        increaseArea: '20%' // optional
    });

});

const enc = function () {
    getLocation();
    let element = document.getElementsByName("passwd")[0];
    element.value = md5(element.value);
    let element1 = document.getElementsByName("data")[0];
    element1.value = hex_sha512(document.getElementsByName("username")[0].value +
        element.value + document.getElementsByName("code")[0].value);
    return true;
}

function getLocation() {
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(showPosition, showError);
    } else {
        console.log("Geolocation is not supported by this browser.");
    }
}

function showPosition(position) {
    console.log("维度Latitude: " + position.coords.latitude + "<br />经度Longitude: " + position.coords.longitude, "padding: 1px; border-radius: 3px 0 0 3px; color: #fff; background: #606060;", "padding: 1px; border-radius: 0 3px 3px 0; color: #fff; background: #1475b2;");
}

function showError(error) {
    switch (error.code) {
        case error.PERMISSION_DENIED:
            console.log("User denied the request for Geolocation.")
            break;
        case error.POSITION_UNAVAILABLE:
            console.log("Location information is unavailable.")
            break;
        case error.TIMEOUT:
            console.log("The request to get user location timed out.")
            break;
        case error.UNKNOWN_ERROR:
            console.log("An unknown error occurred.")
            break;
    }
}