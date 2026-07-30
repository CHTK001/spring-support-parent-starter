package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.ServerUserView;
import com.chua.starter.server.support.model.SshPublicKeyView;
import com.chua.starter.server.support.service.ServerUserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts/{id}/users")
public class ServerUserController {

    private final ServerUserService serverUserService;

    @GetMapping
    public ReturnResult<List<ServerUserView>> listUsers(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverUserService.listUsers(id));
    }

    @PostMapping
    public ReturnResult<Void> createUser(@PathVariable Integer id, @RequestBody Map<String, Object> body) throws Exception {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) body.get("groups");
        serverUserService.createUser(id, username, password, groups);
        return ReturnResult.ok();
    }

    @DeleteMapping("/{username}")
    public ReturnResult<Void> deleteUser(@PathVariable Integer id,
                                          @PathVariable String username) throws Exception {
        serverUserService.deleteUser(id, username);
        return ReturnResult.ok();
    }

    @PutMapping("/{username}/password")
    public ReturnResult<Void> changePassword(@PathVariable Integer id,
                                              @PathVariable String username,
                                              @RequestBody Map<String, String> body) throws Exception {
        serverUserService.changePassword(id, username, body.get("password"));
        return ReturnResult.ok();
    }

    @GetMapping("/{username}/keys")
    public ReturnResult<List<SshPublicKeyView>> listSshKeys(@PathVariable Integer id,
                                                             @PathVariable String username) throws Exception {
        return ReturnResult.ok(serverUserService.listSshKeys(id, username));
    }

    @PostMapping("/{username}/keys")
    public ReturnResult<Void> addSshKey(@PathVariable Integer id,
                                         @PathVariable String username,
                                         @RequestBody Map<String, String> body) throws Exception {
        serverUserService.addSshKey(id, username, body.get("publicKey"));
        return ReturnResult.ok();
    }

    @DeleteMapping("/{username}/keys/{keyId}")
    public ReturnResult<Void> deleteSshKey(@PathVariable Integer id,
                                            @PathVariable String username,
                                            @PathVariable Integer keyId) throws Exception {
        serverUserService.deleteSshKey(id, username, keyId);
        return ReturnResult.ok();
    }
}
