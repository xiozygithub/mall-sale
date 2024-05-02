package com.eugene.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@TableName("coupon_job")
@EqualsAndHashCode
public class CouponJob {
    private static final long serialVersionUID = -712335614171029202L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "id", required = true)
    private Long id;
    @Schema(description = "优惠券码", required = true)
    private String couponCode;
    @Schema(description = "优惠券相关联的用户ID", required = true)
    private String userId;
    @Schema(description = "发券数量", required = true)
    private Integer couponNum;
    @Schema(description = "任务状态 0-未执行 1-已执行", required = true)
    private Integer couponStatus;
    @Schema(description = "优惠券发送时间", required = true)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date sendTime;
    @Schema(description = "创建时间", required = true)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @Schema(description = "更新时间", required = true)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
