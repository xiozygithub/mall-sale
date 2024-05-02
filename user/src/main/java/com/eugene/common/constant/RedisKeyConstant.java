package com.eugene.common.constant;

/**
 * @Description Redis KEY
 * @Author eugene
 */
public class RedisKeyConstant {

    public static final String USER_KEY = "mall_user:user_%s";

    public static String getUserKey(Long userId) {
        return String.format(USER_KEY, userId);
    }

}
