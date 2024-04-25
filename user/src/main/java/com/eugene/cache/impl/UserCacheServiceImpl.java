package com.eugene.cache.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.eugene.cache.IUserCacheService;
import com.eugene.pojo.User;
import com.eugene.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.eugene.common.constant.RedisKeyConstant.getUserKey;

@Service
public class UserCacheServiceImpl implements IUserCacheService {

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public User getUserCache(Long UserId) {
        String result = (String) redisUtil.get(getUserKey(Convert.toLong(UserId)));
        return StrUtil.isNotEmpty(result) ? JSONUtil.toBean(result, User.class) : null;
    }

}
