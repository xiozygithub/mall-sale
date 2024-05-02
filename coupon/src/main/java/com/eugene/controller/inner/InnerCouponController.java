package com.eugene.controller.inner;

import com.eugene.controller.request.ExecuteActivityRequest;
import com.eugene.response.Response;
import com.eugene.service.ICouponActivityService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class InnerCouponController {

    @Autowired
    private ICouponActivityService couponActivityService;

    @PostMapping("/timingSendCoupon")
    @Operation(summary = "人工定时发券", description = "人工定时发券")
    public Response timingSendCoupon(@RequestBody ExecuteActivityRequest executeActivityRequest) {
        couponActivityService.timingSendCoupon(executeActivityRequest);
        return Response.success();
    }

    // todo 通过定时调度调用xxl-job or power-job执行
    @PostMapping("/timingSendCouponSchedule")
    @Operation(summary = "人工定时发券执行计划", description = "人工定时发券执行计划")
    public Response timingSendCouponSchedule() {
        couponActivityService.timingSendCouponSchedule();
        return Response.success();
    }


}
