package com.chua.starter.common.support.view;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.file.ResourceFile;
import com.chua.common.support.lang.file.ResourceFileFactory;
import com.chua.common.support.lang.file.function.ResourceListener;
import com.chua.common.support.lang.file.impl.function.FileReader;
import com.chua.common.support.lang.file.meta.FileMetadata;
import com.chua.common.support.lang.file.meta.Row;
import com.chua.common.support.lang.treenode.TreeNode;
import com.chua.common.support.media.MediaType;
import com.chua.common.support.oss.entity.GetResult;
import com.chua.common.support.oss.view.ViewResult;
import com.chua.common.support.oss.view.Viewer;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 档案查看器
 *
 * @author CH
 */
@Spi({"java-archive", "zip", "x-tar-gz"})
public class ArchiveViewer implements Viewer {
    @Override
    public ViewResult resolve(GetResult getResult) {
        if(getResult.getBytes() == null) {
            return ViewResult.EMPTY;
        }

        List<JsonObject> jsonArray  = new LinkedList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(getResult.getBytes())) {
            ResourceFile resourceFile = ResourceFileFactory.getResourceFile(FileUtils.getSimpleExtension(getResult.getName()), bais);
            if(null == resourceFile) {
                return new ViewResult(getResult.getMediaType(), getResult.getBytes());
            }
            FileReader reader = resourceFile.reader();
            reader.read(new ResourceListener() {
                @Override
                public Boolean safeApply(Row row) throws Throwable {
                    JsonObject item = new JsonObject();
                    FileMetadata fileMetadata = row.getSource(FileMetadata.class);
                    item.put("name", FileUtils.normalize(new File(fileMetadata.getName()).getName()));
                    item.put("lastModified", fileMetadata.getLastModified());
                    item.put("size", fileMetadata.getSize());
                    item.put("isDirectory", fileMetadata.isDirectory());
                    if("/".equalsIgnoreCase(fileMetadata.getName())) {
                        item.put("id", "/");
                        item.put("pid", "-1");
                    } else {
                        item.put("id", FileUtils.normalize(fileMetadata.getName()));
                        item.put("pid", FileUtils.normalize(Optional.ofNullable(new File(fileMetadata.getName()).getParent()).orElse("/")));
                    }
                    jsonArray.add(item);
                    return null;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int port = SpringBeanUtils.getPort();
        String requestURI = RequestUtils.getRequest().getRequestURI();
        String contextPath = SpringBeanUtils.getContextPath();
        try {
            String html = IoUtils.toString(new URL("http://localhost:" + port  + contextPath +
                    "/archive.html").toURI());


            html = html.replace("./assets",  contextPath + "/storage");
            html += "<input style=\"display:none;\" id='fileId' value='"+ Base64.getEncoder().encodeToString(
                    requestURI.replace("/preview/", "/download/").getBytes(StandardCharsets.UTF_8))+ "' ></input>";
            TreeNode<JsonObject> transfer = TreeNode.transfer(jsonArray, new Function<JsonObject, TreeNode>() {
                @Override
                public TreeNode<JsonObject> apply(JsonObject jsonObject) {
                    TreeNode<JsonObject> item = new TreeNode<>();
                    item.setId(jsonObject.getString("id"));
                    item.setPid(Optional.ofNullable(jsonObject.getString("pid")).orElse("/"));
                    item.setValue(jsonObject.getString("name"));
                    return item;
                }
            });
            html += "<div style='display:none' id='data'>"+ Json.toJson(transfer) +"</div>";
            return new ViewResult(
                    MediaType.create("text", "html"),
                    html.getBytes(StandardCharsets.UTF_8)
            );
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
