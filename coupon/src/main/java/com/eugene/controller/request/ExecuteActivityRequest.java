package com.eugene.controller.request;

import lombok.Data;

import java.util.Date;

@Data
public class ExecuteActivityRequest {

    //角色
    private String userId;
    //发送时间
    private Date sendTime;
    //券码
    private String couponCode;
    //券码张数
    private Integer couponNum;
    //使用状态
    private Integer couponStatus;
}

