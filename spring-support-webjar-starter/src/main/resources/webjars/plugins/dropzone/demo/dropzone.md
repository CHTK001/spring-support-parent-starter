**前置**
```
<link rel="stylesheet" href="module/plugins/dropzone/css/dropzone.min.css" />
<link rel="stylesheet" href="module/plugins/dropzone/css/basic.min.css" />
<script src="module/plugins/dropzone/js/dropzone.min.js"></script>
```
**HTML**
```
<div id="dropz" class="dropzone"></div>
```
**javascript**
```
// javscript
var myDropzone = new Dropzone("#dropz", {
    url: "/upload",
    dictDefaultMessage: '拖动文件至此或者点击上传', // 设置默认的提示语句
    paramName: "dropzFile", // 传到后台的参数名称
    init: function () {
        this.on("success", function (file, data) {
            // 上传成功触发的事件
        });
    }
});
```
```
// jQuery
$("div#myId").dropzone({ url: "/file/post" });
```
**案例**

```
var myDropzone = new Dropzone("#dropz", {
    url: "/upload", // 文件提交地址
    method: "post",  // 也可用put
    paramName: "file", // 默认为file
    maxFiles: 1,// 一次性上传的文件数量上限
    maxFilesize: 2, // 文件大小，单位：MB
    acceptedFiles: ".jpg,.gif,.png,.jpeg", // 上传的类型
    addRemoveLinks: true,
    parallelUploads: 1,// 一次上传的文件数量
    //previewsContainer:"#preview", // 上传图片的预览窗口
    dictDefaultMessage: '拖动文件至此或者点击上传',
    dictMaxFilesExceeded: "您最多只能上传1个文件！",
    dictResponseError: '文件上传失败!',
    dictInvalidFileType: "文件类型只能是*.jpg,*.gif,*.png,*.jpeg。",
    dictFallbackMessage: "浏览器不受支持",
    dictFileTooBig: "文件过大上传文件最大支持.",
    dictRemoveLinks: "删除",
    dictCancelUpload: "取消",
    init: function () {
        this.on("addedfile", function (file) {
            // 上传文件时触发的事件
        });
        this.on("success", function (file, data) {
            // 上传成功触发的事件
        });
        this.on("error", function (file, data) {
            // 上传失败触发的事件
        });
        this.on("removedfile", function (file) {
            // 删除文件时触发的方法
        });
    }
});
```

**方法**
```
myDropzone.on("complete", function(file) {
  myDropzone.removeFile(file);
});
```

|Parameter	|Description|
| ----- | :----- | 
|`drop`	|The user dropped something onto the dropzone|
|`dragstart`	|The user started to drag anywhere|
|`dragend`	|Dragging has ended|
|`dragenter`	|The user dragged a file onto the Dropzone|
|`dragover`	|The user is dragging a file over the Dropzone|
|`dragleave`	|The user dragged a file out of the Dropzone|
|`addedfile`	|添加了一个文件时发生|
|`removedfile`	|一个文件被移除时发生。你可以监听这个事件并手动从服务器删除这个文件|
|`thumbnail`	|When the thumbnail has been generated. Receives the dataUrl as second parameter.|
|`error`	|An error occured. Receives the errorMessage as second parameter and if the error was due to the XMLHttpRequest the xhr object as third.|
|`processing`	|When a file gets processed (since there is a queue not all files are processed immediately). This event was called processingfile previously.|
|`uploadprogress`	|上传时按一定间隔发生这个事件。第二个参数为一个整数，表示进度，从 0 到 100。第三个参数是一个整数，表示发送到服务器的字节数。当一个上传结束时，Dropzone 保证会把进度设为 100。注意：这个函数可能被以同一个进度调用多次|
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。.|
|`sending`	|Called just before each file is sent. Gets the xhr object and the formData objects as second and third parameters, so you can modify them (for example to add a CSRF token) or add additional data.|
|`success`	|文件成功上传之后发生，第二个参数为服务器响应|
|`complete`	|当文件上传成功或失败之后发生|
|`canceled`	|当文件在上传时被取消的时候发生.|
|`maxfilesreached`	|当文件数量达到最大时发生.|
|`maxfilesexceeded`	|当文件数量超过限制时发生.|
|`processingmultiple`	|See processing for description.|
|`sendingmultiple`	|See sending for description.|
|`successmultiple`	|See success for description.|
|`completemultiple`	|See complete for description.|
|`canceledmultiple`	|See canceled for description.|
|`totaluploadprogress`	|Called with the total uploadProgress (0-100), the totalBytes and the totalBytesSent. This event can be used to show the overall upload progress of all files.|
|`reset`	|Called when all files in the list are removed and the dropzone is reset to initial state.|
|`queuecomplete`	|Called when all files in the queue finish uploading.|

**配置**

|Option|Description|default|
| --------   | :----- | :----- |
|`url`|指明了文件提交到哪个页面 |null|
|`method`|默认为 post，如果需要，可以改为 put|post|
|withCredentials|Will be set on the XHRequest.|false|
|timeout|The timeout for the XHR requests in milliseconds (since v4.4.0).|30000|
|parallelUploads|How many file uploads to process in parallel (See the Enqueuing file uploads* documentation section for more info)|2|
|`uploadMultiple`|指明是否允许 Dropzone 一次提交多个文件。默认为 false。如果设为 true，则相当于 HTML 表单添加 multiple 属性|false|
|chunking|Whether you want files to be uploaded in chunks to your server. This can't be used in combination with uploadMultiple.See chunksUploaded for the callback to finalise an upload.|false|
|forceChunking|If chunking is enabled, this defines whether every file should be chunked, even if the file size is below chunkSize. This means, that the additional chunk form data will be submitted and the chunksUploaded callback will be invoked.|false|
|chunkSize|If chunking is true, then this defines the chunk size in bytes.|2000000|
|parallelChunkUploads|If true, the individual chunks of a file are being uploaded simultaneously.|false|
|retryChunks|Whether a chunk should be retried if it fails.|false|
|retryChunksLimit|If retryChunks is true, how many times should it be retried.|3|
|`maxFilesize`|最大文件大小，单位是 MB|256|
|`paramName`|相当于 `<input>` 元素的 name 属性，默认为 file|"file"|
|createImageThumbnails|Whether thumbnails for images should be generated|true|
|maxThumbnailFilesize|In MB. When the filename exceeds this limit, the thumbnail will not be generated.|10|
|thumbnailWidth|If null, the ratio of the image will be used to calculate it.|120|
|thumbnailHeight|The same as thumbnailWidth. If both are null, images will not be resized.|120|
|thumbnailMethod|How the images should be scaled down in case both, thumbnailWidth and thumbnailHeight are provided. Can be either contain or crop.|'crop'|
|resizeWidth|If set, images will be resized to these dimensions before being uploaded. If only one, resizeWidth or resizeHeight is provided, the original aspect ratio of the file will be preserved.The options.transformFile function uses these options, so if the transformFile function is overridden, these options don't do anything.|null|
|resizeHeight|See resizeWidth.|null|
|resizeMimeType|The mime type of the resized image (before it gets uploaded to the server). If null the original mime type will be used. To force jpeg, for example, use image/jpeg. See resizeWidth for more information.|null|
|resizeQuality|The quality of the resized images. See resizeWidth.|0.8|
|resizeMethod|How the images should be scaled down in case both, resizeWidth and resizeHeight are provided. Can be either contain or crop.|'contain'|
|filesizeBase|The base that is used to calculate the filesize. You can change this to 1024 if you would rather display kibibytes, mebibytes, etc... 1024 is technically incorrect, because 1024 bytes are 1 kibibyte not 1 kilobyte. You can change this to 1024 if you don't care about validity.|1000|
|`maxFiles`|默认为 null，可以指定为一个数值，限制最多文件数量|null|
|`headers`|如果设定，则会作为额外的 header 信息发送到服务器。例如：{"custom-header": "value"}|null|
|clickable|If true, the dropzone element itself will be clickable, if false nothing will be clickableYou can also pass an HTML element, a CSS selector (for multiple elements) or an array of those. In that case, all of those elements will trigger an upload when clicked.|true|
|ignoreHiddenFiles|Whether hidden files in directories should be ignored.|true|
|`acceptedFiles`|指明允许上传的文件类型，格式是逗号分隔的 MIME type 或者扩展名。例如：image/*, application/pdf, .psd, .obj|null|
|acceptedMimeTypes|Deprecated! Use acceptedFiles instead.||
|autoProcessQueue|If false, files will be added to the queue but the queue will not be processed automatically. This can be useful if you need some additional user input before sending files (or if you want want all files sent at once). If you're ready to send the file simply call myDropzone.processQueue().|true|
|autoQueue|If false, files added to the dropzone will not be queued by default. You'll have to call enqueueFile(file) manually.|true|
|`addRemoveLinks`|默认 false。如果设为 true，则会给文件添加一个删除链接|false|
|previewsContainer|Defines where to display the file previews – if null the Dropzone element itself is used. Can be a plain HTMLElement or a CSS selector. The element should have the dropzone-previews class so the previews are displayed properly.||
|hiddenInputContainer|This is the element the hidden input field (which is used when clicking on the dropzone to trigger file selection) will be appended to. This might be important in case you use frameworks to switch the content of your page.Can be a selector string, or an element directly.|default: "body"|
|capture|If null, no capture type will be specified If camera, mobile devices will skip the file selection and choose camera If microphone, mobile devices will skip the file selection and choose the microphone If camcorder, mobile devices will skip the file selection and choose the camera in video mode On apple devices multiple must be set to false. AcceptedFiles may need to be set to an appropriate mime type (e.g. "image/", "audio/", or "video/*").||
|renameFilename|Deprecated. Use renameFile instead.||
|renameFile|A function that is invoked before the file is uploaded to the server and renames the file. This function gets the File as argument and can use the file.name. The actual name of the file that gets used during the upload can be accessed through file.upload.filename.||
|`forceFallback`|Fallback 是一种机制，当浏览器不支持此插件时，提供一个备选方案。默认为 false。如果设为 true，则强制 fallback|false|
|`dictDefaultMessage`|没有任何文件被添加的时候的提示文本.|see description|
|`dictFallbackMessage`|Fallback 情况下的提示文本.|see description|
|`dictFallbackText`|The text that will be added before the fallback form. If you provide a fallback element yourself, or if this option is null this will be ignored.||
|`dictFileTooBig`|文件大小过大时的提示文本.|see description|
|`dictInvalidFileType`|文件类型被拒绝时的提示文本.|see description|
|`dictResponseError`|If the server response was invalid. {{statusCode}} will be replaced with the servers status code.|see description|
|`dictCancelUpload`|取消上传链接的文本.|see description|
|`dictUploadCanceled`|The text that is displayed if an upload was manually canceled|see description|
|`dictCancelUploadConfirmation`|取消上传确认信息的文本.|see description|
|`dictRemoveFile`|移除文件链接的文本.|see description|
|`dictRemoveFileConfirmation`|If this is not null, then the user will be prompted before removing a file.|see description|
|`dictMaxFilesExceeded`|超过最大文件数量的提示文本.|see description|
|`dictFileSizeUnits`|Allows you to translate the different units. Starting with tb for terabytes and going down to b for bytes.functions you can override to change or extend default behavior:||
|`init`|一个函数，在 Dropzone 初始化的时候调用，可以用来添加自己的事件监听器|empty function|
|params|Can be an object of additional parameters to transfer to the server, or a Function that gets invoked with the files, xhr and, if it's a chunked upload, chunk arguments. In case of a function, this needs to return a map.The default implementation does nothing for normal uploads, but adds relevant information for chunked uploads.This is the same as adding hidden input fields in the form element.|function|
|accept|A function that gets a file and a done function as parameters.If the done function is invoked without arguments, the file is "accepted" and will be processed. If you pass an error message, the file is rejected, and the error message will be displayed. This function will not be called if the file is too big or doesn't match the mime types.|function|
|chunksUploaded|The callback that will be invoked when all chunks have been uploaded for a file. It gets the file for which the chunks have been uploaded as the first parameter, and the done function as second. done() needs to be invoked when everything needed to finish the upload process is done.|function|
|`fallback`|一个函数，如果浏览器不支持此插件则调用|function|
|resize|Gets called to calculate the thumbnail dimensions.It gets file, width and height (both may be null) as parameters and must return an object containing:srcWidth & srcHeight (required).trgWidth & trgHeight (required).srcX & srcY (optional, default 0).trgX & trgY (optional, default 0).Those values are going to be used by ctx.drawImage().|function|
|transformFile|Can be used to transform the file (for example, resize an image if necessary).The default implementation uses resizeWidth and resizeHeight (if provided) and resizes images according to those dimensions.Gets the file as the first parameter, and a done() function as the second, that needs to be invoked with the file when the transformation is done.|function|
|previewTemplate|A string that contains the template used for each dropped file. Change it to fulfill your needs but make sure to properly provide all elements.If you want to use an actual HTML element instead of providing a String as a config option, you could create a div with the id tpl, put the template inside it and provide the element like this:|HTML template