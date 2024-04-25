package com.eugene.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.eugene.controller.request.RegisterUserRequest;
import com.eugene.controller.request.UpgradeUserRequest;
import com.eugene.mapper.UserMapper;
import com.eugene.mq.producer.RegisterUserProducerService;
import com.eugene.mq.producer.TxRegisterUserProducerService;
import com.eugene.pojo.User;
import com.eugene.service.IMqTransactionLogService;
import com.eugene.service.IUserShardingService;
import com.eugene.utils.RedisUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.eugene.common.constant.RedisKeyConstant.getUserKey;
import static com.eugene.pojo.User.converUser;

/**
 * @Description 用户分表操作类
 * @Author eugene
 * @Data 2023/3/17 12:00
 */
@Service
@DS("shardingmaster")
public class UserShardingServiceImpl extends ServiceImpl<UserMapper, User> implements IUserShardingService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RegisterUserProducerService registerUserProducerService;
    @Autowired
    private IMqTransactionLogService mqTransactionLogService;
    @Autowired
    private TxRegisterUserProducerService txRegisterUserProducerService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(User user) {
        return retBool(userMapper.insert(user));
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveBatch(List<User> userList) {
        return userMapper.saveBatch(userList);
    }


    @Override
    public boolean register(RegisterUserRequest request) {
        User user = converUser(request);
        boolean saved = this.save(user);
        if (saved) {
            // redis里面需要存储注册成功后的标签
            redisUtil.set(getUserKey(user.getId()), JSONUtil.toJsonStr(user));
            // 发送用户注册消息
            registerUserProducerService.syncSendRegisterMessage(user);
            return true;
            // 亮点：发送分布式事务消息
//        txRegisterUserProducerService.sendHalfMessage(user);
//        return true;
        }
        return false;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerTx(User user, String transactionId) {
        // 保存用户
        save(user);
        // 写入mq事务日志
        mqTransactionLogService.saveMQTransactionLog(transactionId, user);
        return true;
    }

    @Override
    public boolean upgradeUser(UpgradeUserRequest request) {
        // 加锁，防止并发升级
        // 思路 查询数据库原等级 进行升级 如已是最高级别等级 则不升级
        RLock lock = redissonClient.getLock(getUserKey(request.getUserId()));
        if (lock.tryLock()) {
            try {
                QueryWrapper<User> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("id", request.getUserId());
                User user = userMapper.selectOne(queryWrapper);
                if (user == null) {
                    throw new RuntimeException("用户不存在");
                }
                if (user.getLevel() == 2) {
                    throw new RuntimeException("已是最高级别");
                }
                user.setLevel(user.getLevel() + 1);
                int updated = userMapper.updateById(user);//更新数据库
                if (updated == 0) {
                    throw new RuntimeException("更新失败");
                }
                // 同时在缓存里面存一份
                redisUtil.set(getUserKey(user.getId()), JSONUtil.toJsonStr(user));
                return true;
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
        return false;
    }

}
