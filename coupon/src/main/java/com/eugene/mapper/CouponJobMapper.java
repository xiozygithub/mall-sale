package com.eugene.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eugene.pojo.CouponJob;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CouponJobMapper extends BaseMapper<CouponJob> {
    int saveBatch(@Param("couponJobs") List<CouponJob> couponJobs);
}
