package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerFileContent;
import com.chua.starter.server.support.model.ServerFileEntry;
import com.chua.starter.server.support.model.ServerFileOperationResult;
import java.io.InputStream;
import java.util.List;

public interface ServerFileService {

    List<ServerFileEntry> listFiles(Integer serverId, String path) throws Exception;

    ServerFileContent readContent(Integer serverId, String path, Integer maxBytes) throws Exception;

    byte[] download(Integer serverId, String path) throws Exception;

    ServerFileOperationResult upload(Integer serverId, String directory, String fileName, InputStream inputStream) throws Exception;

    ServerFileOperationResult writeContent(Integer serverId, String path, String content) throws Exception;

    ServerFileOperationResult createDirectory(Integer serverId, String path) throws Exception;

    ServerFileOperationResult rename(Integer serverId, String path, String targetPath) throws Exception;

    ServerFileOperationResult delete(Integer serverId, String path, boolean recursive) throws Exception;
}
