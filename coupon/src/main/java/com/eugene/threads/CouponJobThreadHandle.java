package com.eugene.threads;

import com.eugene.controller.request.SendCouponRequest;
import com.eugene.service.ICouponService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class CouponJobThreadHandle implements Runnable {

    private SendCouponRequest sendCouponRequest;
    private ICouponService couponService;


    @Override
    public void run() {
        couponService.send(sendCouponRequest);
    }


}
