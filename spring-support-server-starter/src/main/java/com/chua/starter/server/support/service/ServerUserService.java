package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerUserView;
import com.chua.starter.server.support.model.SshPublicKeyView;
import java.util.List;

public interface ServerUserService {

    List<ServerUserView> listUsers(Integer serverId) throws Exception;

    void createUser(Integer serverId, String username, String password, List<String> groups) throws Exception;

    void deleteUser(Integer serverId, String username) throws Exception;

    void changePassword(Integer serverId, String username, String newPassword) throws Exception;

    List<SshPublicKeyView> listSshKeys(Integer serverId, String username) throws Exception;

    void addSshKey(Integer serverId, String username, String publicKey) throws Exception;

    void deleteSshKey(Integer serverId, String username, Integer keyId) throws Exception;
}
