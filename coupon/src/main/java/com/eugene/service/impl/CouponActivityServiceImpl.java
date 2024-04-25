package com.eugene.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.eugene.cache.ICouponActivityCacheService;
import com.eugene.cache.ICouponTemplateCacheService;
import com.eugene.common.enums.StatusEnum;
import com.eugene.common.exception.BusinessException;
import com.eugene.controller.request.*;
import com.eugene.controller.response.CouponActivityResponse;
import com.eugene.controller.response.CouponResponse;
import com.eugene.mapper.CouponActivityLogMapper;
import com.eugene.mapper.CouponActivityMapper;
import com.eugene.mapper.CouponJobMapper;
import com.eugene.mapper.CouponMapper;
import com.eugene.pojo.*;
import com.eugene.service.ICouponActivityService;
import com.eugene.service.ICouponCacheService;
import com.eugene.service.ICouponService;
import com.eugene.threads.CouponJobThreadHandle;
import com.eugene.utils.CouponRedisLuaUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.eugene.common.constant.RedisKeyConstant.getReceiveCouponKey;
import static com.eugene.common.enums.Errors.NOT_JOIN_RECEIVE_COUPON_ERROR;
import static com.eugene.common.enums.Errors.RECEIVE_COUPON_ERROR;
import static com.eugene.controller.response.CouponActivityResponse.buildCouponActivityResponse;
import static com.eugene.controller.response.CouponResponse.buildCouponResponse;
import static com.eugene.pojo.Coupon.buildCoupon;

/**
 * @Description TODO
 * @Author eugene
 * @Data 2023/4/7 18:30
 */
@Service
public class CouponActivityServiceImpl implements ICouponActivityService {

    private static final Logger log = LoggerFactory.getLogger(CouponActivityServiceImpl.class);

    @Autowired
    private CouponActivityMapper couponActivityMapper;
    @Autowired
    private ICouponActivityCacheService couponActivityCacheService;
    @Autowired
    private CouponActivityLogMapper couponActivityLogMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private CouponRedisLuaUtil couponRedisLuaUtil;
    @Autowired
    private ICouponCacheService couponCacheService;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private ICouponTemplateCacheService couponTemplateCacheService;
    @Autowired
    private CouponJobMapper couponJobMapper;
    @Autowired
    private ICouponService couponService;

    @Override
    public boolean addCouponActivity(AddCouponActivityRequest request) {
        CouponActivity couponActivity = new CouponActivity();
        couponActivity.setId(IdUtil.getSnowflakeNextId());
        couponActivity.setName(request.getName());
        couponActivity.setCouponTemplateCode(request.getCouponTemplateCode());
        couponActivity.setTotalNumber(request.getTotalNumber());
        couponActivity.setLimitNumber(request.getLimitNumber());
        couponActivity.setStatus(request.getStatus());
        couponActivity.setBeginTime(request.getBeginTime());
        couponActivity.setEndTime(request.getEndTime());
        couponActivity.setCreateTime(new Date());
        couponActivity.setUpdateTime(new Date());
        int result = couponActivityMapper.insert(couponActivity);
        if (result > 0) {
            // 保存到Redis缓存中
            couponActivityCacheService.setCouponActivityCache(couponActivity);
            // todo 用延时队列，在活动开始时，把活动信息放到生效中活动列表中
            // activityProducerService.delaySendMessage(activity);

            // todo 下面的逻辑是mq的消费者实现的
//            if (checkActivityIsAvailable(couponActivity)) {
//                // 保存生效活动到生效中活动列表中
//                couponActivityCacheService.setCouponActivityStatus(couponActivity.getId());
//            } else {
//
//            }

        }
        return result > 0;
    }

    @Override
    public List<CouponActivityResponse> getCouponCenterList(UserCouponRequest request) {
        //如果活动配置的每人可领取数量是2张，要在查询列表的时候就判断用户还能不能领取该优惠券，
        // 查询数据库, todo 优化改为查询Redis
        QueryWrapper<CouponActivity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", StatusEnum.AVAILABLE.getCode());
        List<CouponActivity> couponActivities = couponActivityMapper.selectList(queryWrapper);
        //活动id去活动表查询他的限制次数 map key:活动id  value:限制次数
        Map<Long, Long> couponLimitMap = couponActivities.stream().collect(Collectors.toMap(CouponActivity::getId, CouponActivity::getLimitNumber));
        //先去记录表根据userid查询出他所有的活动  map
        QueryWrapper<CouponActivityLog> queryWrapperLog = new QueryWrapper<>();
        queryWrapperLog.eq("user_id", request.getUserId());
        //根据userid查询下他所有的活动
        List<CouponActivityLog> couponActivityLogs = couponActivityLogMapper.selectList(queryWrapperLog);
        //根据活动id进行分组
        Map<Long, List<CouponActivityLog>> couponActivityLogsMap = couponActivityLogs.stream().collect(Collectors.groupingBy(CouponActivityLog::getCouponActivityId));

        // 遍历userid的活动map
        for (Map.Entry<Long, List<CouponActivityLog>> couponActivity : couponActivityLogsMap.entrySet()) {
            Long couponLimit = couponLimitMap.get(couponActivity.getKey());
            if (ObjectUtil.isNotNull(couponLimit) && couponActivity.getValue().size() >= couponLimit) {
                couponActivities.removeIf(activity -> activity.getId().equals(couponActivity.getKey()));
            }
        }

        for (Map.Entry<Long, List<CouponActivityLog>> getCouponActivityId : couponActivityLogsMap.entrySet()) {
            //遍历活动id的map
            for (Map.Entry<Long, Long> couponLimitData : couponLimitMap.entrySet()) {
                //如果活动id相同并且用户领取的数量大于等于活动限制的数量
                if (getCouponActivityId.getKey().equals(couponLimitData.getKey()) && getCouponActivityId.getValue().size() >= couponLimitData.getValue()) {
                    couponActivities.removeIf(couponActivity -> couponActivity.getId().equals(couponLimitData.getKey()));
                }
            }
        }

        return couponActivities.stream()
                // todo receivedNumber 取真实领取数量 改为查询Redis
                //        QueryWrapper<CouponActivityLog> queryWrapper = new QueryWrapper<>();
                //        queryWrapper.eq("coupon_activity_id", request.getCouponActivityId());
                //        Integer receivedNumber = couponActivityLogMapper.selectCount(queryWrapper);
                .map(couponActivity -> buildCouponActivityResponse(couponActivity, 0L))
                .collect(Collectors.toList());
    }

    @Override
    public CouponActivityResponse getCouponActivityDetail(CouponActivityRequest request) {
        CouponActivity couponActivityCache = couponActivityCacheService.getCouponActivityCache(request.getCouponActivityId());
        // 查询当前用户已领取数量, todo 优化改为查询Redis
        QueryWrapper<CouponActivityLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("coupon_activity_id", request.getCouponActivityId());
        queryWrapper.eq("user_id", request.getUserId());
        Integer receivedNumber = couponActivityLogMapper.selectCount(queryWrapper);
        return buildCouponActivityResponse(couponActivityCache, Long.valueOf(receivedNumber));
    }

    /**
     * 领取优惠券
     * 1、请求量大、并发写
     * 2、优惠券要控制不能超发、每人不能超领，【防刷】
     *
     * @param request
     * @return
     */
    @Override
    public CouponResponse receive(ReceiveCouponRequest request) throws BusinessException {
        CouponResponse couponResponse = null;
        CouponActivity couponActivityCache = couponActivityCacheService.getCouponActivityCache(request.getCouponActivityID());
        // 检查是否可参与领券活动
        boolean canJoin = checkIsCanJoinActivity(couponActivityCache, request.getUserId());
        if (!canJoin) {
            throw new BusinessException(NOT_JOIN_RECEIVE_COUPON_ERROR.getCode(), NOT_JOIN_RECEIVE_COUPON_ERROR.getMsg());
        }
        // 参与活动，加锁防重复提交
        RLock lock = redissonClient.getLock(getReceiveCouponKey(request.getUserId(), request.getCouponActivityID()));
        if (lock.tryLock()) {
            try {
                // 领取优惠券，received 用户是否已领取成功， todo 可以使用Bitmap优化，设置给活动key设置已领取用户的标记
                boolean received = false;
                if (couponActivityCache.existLimit()) {
                    received = couponRedisLuaUtil.receive(request);
                }
                // 添加用户优惠券信息， todo优化：原子性
                if (received) {
                    CouponTemplate couponTemplateCache = couponTemplateCacheService.getCouponTemplateCache(couponActivityCache.getCouponTemplateCode());
                    Coupon coupon = buildCoupon(request, couponTemplateCache);
                    // 保存优惠券信息，
                    couponCacheService.setCouponCache(coupon);
                    couponCacheService.addUserCouponCode(request.getMobile(), coupon.getCode());
                    // todo 优化：改为MQ异步更新Mysql数据库，提高性能
                    // todo 遇到了新问题，MQ如何保障消息不丢失，如何保障Redis和Mysql之间的数据一致性
                    couponMapper.insert(coupon);
                    // 保存领券活动参与记录
                    CouponActivityLog couponActivityLog = getCouponActivityLog(request, coupon);
                    couponActivityLogMapper.insert(couponActivityLog);
                    // todo 发送优惠券过期延时队列，更新优惠券过期状态
                    // 组装返回领取的优惠券信息
                    couponResponse = buildCouponResponse(coupon);
                } else {
                    throw new BusinessException(RECEIVE_COUPON_ERROR.getCode(), RECEIVE_COUPON_ERROR.getMsg());
                }
            } finally {
                if (lock.isLocked()) {
                    // TODO 重点注意：释放锁前要判断当前是否被锁住了lock.isLocked()，否则之间调用unlock会报异常
                    // 严谨一点，防止当前线程释放掉其他线程的锁
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } else {
            // 重试获取锁，幂等、防重校验、告警、日志记录、友好的提示等等
        }
        return couponResponse;
    }

    public static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            30, 30, 300, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "thread_pool_coupon_job_handle_task_" + r.hashCode());
                }
            }, new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Override
    public void timingSendCoupon(ExecuteActivityRequest executeActivityRequest) {
        couponJobMapper.insert(transExecuteActivityRequestToCouponJob(executeActivityRequest));
    }

    private CouponJob transExecuteActivityRequestToCouponJob(ExecuteActivityRequest executeActivityRequest) {
        CouponJob couponJob = new CouponJob();
        couponJob.setCouponCode(executeActivityRequest.getCouponCode());
        couponJob.setUserId(executeActivityRequest.getUserId());
        couponJob.setCouponNum(executeActivityRequest.getCouponNum());
        couponJob.setCouponStatus(executeActivityRequest.getCouponStatus());
        couponJob.setSendTime(executeActivityRequest.getSendTime());
        couponJob.setCreateTime(new Date());
        couponJob.setUpdateTime(new Date());
        return couponJob;
    }

    @Override
    public void timingSendCouponSchedule() {
        //1. 查询所有未执行的任务
        List<CouponJob> couponJobs = couponJobMapper.selectList(new QueryWrapper<CouponJob>().eq("status", 0));
        //2. 遍历执行
        for (CouponJob couponJob : couponJobs) {
            //判断是否到达执行时间
            if (couponJob.getSendTime().after(new Date())) {
                log.info("优惠券任务未到执行时间，couponJob:{}", couponJob);
                continue;
            }
            if (StrUtil.isNotBlank(couponJob.getUserId())) {
                String[] couponUserId = couponJob.getUserId().split(",");
                // 给多个用户发券
                for (String userId : couponUserId) {
                    //这里应该是给用户去发券，而不是更新优惠券的状态
                    SendCouponRequest request = new SendCouponRequest();
                    request.setUserId(Long.valueOf(userId));
                    //request.setMobile();
                    request.setCouponTemplateCode(couponJob.getCouponCode().toString());
                    request.setNumber(couponJob.getCouponNum());
                    //调用这个方法发券
                    threadPool.execute(new CouponJobThreadHandle(request, couponService));
                }
            }
            //3. 执行任务0-未执行 1-已执行
            couponJob.setCouponStatus(1);
            couponJob.setUpdateTime(new Date());
            couponJobMapper.updateById(couponJob);
        }
    }


    private static CouponActivityLog getCouponActivityLog(ReceiveCouponRequest request, Coupon coupon) {
        CouponActivityLog couponActivityLog = new CouponActivityLog();
        couponActivityLog.setCouponActivityId(request.getCouponActivityID());
        couponActivityLog.setCode(coupon.getCode());
        couponActivityLog.setUserId(request.getUserId());
        couponActivityLog.setMobile(request.getMobile());
        couponActivityLog.setCreateTime(new Date());
        couponActivityLog.setUpdateTime(new Date());
        return couponActivityLog;
    }

    /**
     * 判断是否可以参加活动
     *
     * @param couponActivity
     * @param userId
     * @return
     */
    private boolean checkIsCanJoinActivity(CouponActivity couponActivity, Long userId) {
        boolean available = checkActivityIsAvailable(couponActivity);
        if (available) {
            // 查询当前用户已领取数量 todo 优化改为查询Redis
            QueryWrapper<CouponActivityLog> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("coupon_activity_id", couponActivity.getId());
            queryWrapper.eq("user_id", userId);
            Integer receivedNumber = couponActivityLogMapper.selectCount(queryWrapper);
            if (couponActivity.getLimitNumber() > receivedNumber) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前活动是否生效
     * 当前是活动状态是生效、当前时间在活动范围内、
     *
     * @param couponActivity
     * @return
     */
    private boolean checkActivityIsAvailable(CouponActivity couponActivity) {
        if (couponActivity.getStatus().equals(StatusEnum.AVAILABLE.getCode())
                && couponActivity.getBeginTime().before(new Date())
                && couponActivity.getEndTime().after(new Date())) {
            return true;
        }
        return false;
    }
}
