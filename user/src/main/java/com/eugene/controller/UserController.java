package com.eugene.controller;

import cn.hutool.core.convert.Convert;
import com.eugene.cache.IUserCacheService;
import com.eugene.controller.request.RegisterUserRequest;
import com.eugene.controller.request.UpgradeUserRequest;
import com.eugene.pojo.User;
import com.eugene.response.Response;
import com.eugene.service.IUserShardingService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 用户相关
 */
@RestController
public class UserController {

    @Autowired
    private IUserShardingService userShardingService;
    @Autowired
    private IUserCacheService userCacheService;

    /**
     * 注册用户
     *
     * @return
     */
    @PostMapping("/register")
    @Operation(summary = "注册用户", description = "注册用户")
    public Response register(@RequestBody @Valid RegisterUserRequest request) {
        return Response.success(userShardingService.register(request));
    }

    @PostMapping("/getUser")
    @Operation(summary = "查询用户信息", description = "查询用户信息")
    public Response getUser(@RequestParam String userId) {
        // 查询数据库
//        User user = userShardingService.getOne(new QueryWrapper<User>().eq("id", userId));
        // 优化改为查询Redis缓存
        User user = userCacheService.getUserCache(Convert.toLong(userId));
        return Response.success(user);
    }

    @PostMapping("/upgradeUser")
    @Operation(summary = "升级用户等级", description = "升级用户等级")
    public Response upgradeUser(@RequestBody @Valid UpgradeUserRequest upgradeUserRequest) {
        return Response.success(userShardingService.upgradeUser(upgradeUserRequest));
    }

}
