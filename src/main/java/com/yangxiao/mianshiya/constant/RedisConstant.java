package com.yangxiao.mianshiya.constant;

/**
 * Redis常量
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public interface RedisConstant {

    /**
     * 用户签到的Redis Key的前缀
     */

    String USER_SIGN_IN_REDIS_KEY_PREFIX = "mianshiya:user:signins";

    /**
     * 返回用户签到的Key
     *
     * @param year 要查询的年份
     * @param userId 查询的用户id
     * @return
     */
    static String getUserSignInRedisKey(int year, Long userId){
        return String.format("%s:%s:%s",USER_SIGN_IN_REDIS_KEY_PREFIX,year,userId);

    }
    
}

