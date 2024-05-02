package com.eugene.controller.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UpgradeUserRequest {
    // 用户userID
    @NotNull(message = "用户ID不能为空")
    private Long userId;

}