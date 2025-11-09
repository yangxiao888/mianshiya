package com.yangxiao.mianshiya.service.impl;

import static com.yangxiao.mianshiya.constant.UserConstant.USER_LOGIN_STATE;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yangxiao.mianshiya.common.ErrorCode;
import com.yangxiao.mianshiya.constant.CommonConstant;
import com.yangxiao.mianshiya.constant.RedisConstant;
import com.yangxiao.mianshiya.exception.BusinessException;
import com.yangxiao.mianshiya.mapper.UserMapper;
import com.yangxiao.mianshiya.model.dto.user.UserQueryRequest;
import com.yangxiao.mianshiya.model.entity.User;
import com.yangxiao.mianshiya.model.enums.UserRoleEnum;
import com.yangxiao.mianshiya.model.vo.LoginUserVO;
import com.yangxiao.mianshiya.model.vo.UserVO;
import com.yangxiao.mianshiya.satoken.DeviceUtils;
import com.yangxiao.mianshiya.service.UserService;
import com.yangxiao.mianshiya.utils.SqlUtils;

import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 用户服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Resource
    private RedissonClient redissonClient;


    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "yupi";

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
       /* // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);*/

        //Sa-Token登录，并指定设备信息，实现同端登录互斥
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);

        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo, HttpServletRequest request) {
        String unionId = wxOAuth2UserInfo.getUnionId();
        String mpOpenId = wxOAuth2UserInfo.getOpenid();
        // 单机锁
        synchronized (unionId.intern()) {
            // 查询用户是否已存在
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("unionId", unionId);
            User user = this.getOne(queryWrapper);
            // 被封号，禁止登录
            if (user != null && UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封，禁止登录");
            }
            // 用户不存在则创建
            if (user == null) {
                user = new User();
                user.setUnionId(unionId);
                user.setMpOpenId(mpOpenId);
                user.setUserAvatar(wxOAuth2UserInfo.getHeadImgUrl());
                user.setUserName(wxOAuth2UserInfo.getNickname());
                boolean result = this.save(user);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败");
                }
            }
            // 记录用户的登录态
            request.getSession().setAttribute(USER_LOGIN_STATE, user);
            return getLoginUserVO(user);
        }
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (Objects.isNull(loginId)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return (User) StpUtil.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (Objects.isNull(loginId)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return (User) StpUtil.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        // 基于 Sa-Token 改造
        Object userObj = StpUtil.getSession().get(USER_LOGIN_STATE);
        // Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        StpUtil.checkLogin();
        // 移除登录态
        StpUtil.logout();
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


    /**
     * 添加用户签到记录
     *
     * @param userId 用户id
     * @return 是否签到成功
     */
    @Override
    public boolean addUserSignIn(Long userId) {
        //获取key
        LocalDate now = LocalDate.now();
        String userSignInRedisKey = RedisConstant.getUserSignInRedisKey(now.getYear(), userId);
        //通过 Redisson 客户端从 Redis 中获取一个名为userSignInRedisKey的位图（BitSet）对象
        RBitSet SignInBitSet = redissonClient.getBitSet(userSignInRedisKey);
        //获取当天的天数
        int dayOfYear = now.getDayOfYear();
        //检查当天是否签到
        if (!SignInBitSet.get(dayOfYear)) {
            //没有签到，添加记录
            return SignInBitSet.set(dayOfYear, true);
        }
        return true;
    }

    /**
     * 查询用户签到记录
     *
     * @param userId 用户id
     * @return 签到记录
     */
    @Override
    public ArrayList<Integer> getUserSignInRecord(Long userId, Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        //获取key
        String userSignInRedisKey = RedisConstant.getUserSignInRedisKey(year, userId);
        //通过 Redisson 客户端从 Redis 中获取一个名为userSignInRedisKey的位图（BitSet）对象
        RBitSet SignInBitSet = redissonClient.getBitSet(userSignInRedisKey);
        //判断请求优化————将redis数据拷贝到java中，减少请求
        BitSet bitSet = SignInBitSet.asBitSet();
        /*//LinkedHashMap 保证有序
        LinkedHashMap<LocalDate, Boolean> userSignInMap = new LinkedHashMap<>();*/
        /*获取本年总年数
        int totalOfDay = Year.of(year).length();
        for (int i = 1; i < totalOfDay; i++) {
            /*//*/获取当天的日期
            LocalDate date = LocalDate.ofYearDay(year, i);*//*
            if(bitSet.get(i)) {
               userSignInList.add(i);
            }
        }*/
        //返回值优化————减少返回的数据
        ArrayList<Integer> userSignInList = new ArrayList<>();
        //计算优化————减少循环判断占有cpu的资源
        int index = bitSet.nextSetBit(0);
        while (index >= 0) {
            userSignInList.add(index);
            //查找下一个为1的位
            index = bitSet.nextSetBit(index + 1);
        }

        return userSignInList;
    }
}
