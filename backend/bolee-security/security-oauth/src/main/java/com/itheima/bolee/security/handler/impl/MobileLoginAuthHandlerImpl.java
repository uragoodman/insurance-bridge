package com.itheima.bolee.security.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.itheima.bolee.framework.commons.constant.security.OauthCacheConstant;
import com.itheima.bolee.framework.commons.constant.security.OauthConstant;
import com.itheima.bolee.framework.commons.dto.security.CompanyVO;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.dto.security.UsernameVO;
import com.itheima.bolee.framework.commons.enums.security.AuthEnum;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.framework.commons.utils.EmptyUtil;
import com.itheima.bolee.security.handler.CacheTokenHandler;
import com.itheima.bolee.security.handler.LoginAuthHandler;
import com.itheima.bolee.security.feign.CustomerFeign;
import com.itheima.bolee.security.feign.UserFeign;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.endpoint.TokenEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.security.Principal;
import java.util.Map;

/**
 * @ClassName MobileLoginAuthHandlerImpl.java
 * @Description 手机验证码登录处理器实现
 */
@Slf4j
@Component("mobileLoginAuthHandler")
public class MobileLoginAuthHandlerImpl implements LoginAuthHandler {

    @Autowired
    TokenEndpoint tokenEndpoint;

    @Autowired
    CacheTokenHandler cacheTokenHandler;

    //调用RPC原创服务
    @Autowired
    UserFeign userFeign;

    //调用RPC原创服务
    @Autowired
    CustomerFeign customerFeign;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public UserVO loginHandler(Principal principal, Map<String, String> parameters,
                               String loginBeanName, CompanyVO companyVO)
            throws HttpRequestMethodNotSupportedException {

        // 1️⃣ 从请求参数中获取手机号（当前登录方式是基于手机号的场景）
        String mobile = parameters.get(OauthConstant.MOBILE_KEY);

        // 2️⃣ 获取 OAuth2 客户端ID（用于区分不同客户端，比如 PC / APP）
        String clientId = parameters.get(OauthConstant.CLIENT_ID_KEY);

        // 3️⃣ 获取登录类型（用于区分是手机号登录 / 用户名登录 / 微信登录等）
        String loginType = parameters.get(OauthConstant.LOGIN_TYPE_KEY);

        // 4️⃣ 构建统一的“用户名载体对象”
        // ⚠️ 注意：这里不是简单的 username，而是把多个业务字段封装进去
        // 目的是后续 OAuth2 认证阶段可以拿到完整上下文（租户 + 登录方式等）
        UsernameVO usernameVO = UsernameVO.builder()
                .username(mobile)                         // 实际登录账号（这里是手机号）
                .clientId(clientId)                       // 客户端ID
                .companyNo(companyVO.getCompanyNo())      // 公司编号（多租户关键）
                .loginType(loginType)                     // 登录方式
                .loginBeanName(loginBeanName)             // 登录处理器标识
                .build();

        // 5️⃣ 将 UsernameVO 转成 JSON 字符串
        // ⚠️ 关键设计：把复杂身份信息塞进 OAuth2 的 username 参数中
        String username = JSONObject.toJSONString(usernameVO);

        // 6️⃣ 替换 OAuth2 标准参数中的 username
        // 👉 后续 Spring Security 会从这里拿 username，再进入 UserDetailsService
        parameters.put(OauthConstant.USER_NAME_KEY, username);

        // =========================
        // 到这里为止：
        // 只是“准备认证参数”，还没有真正校验密码！
        // =========================

        // 7️⃣ 调用 Spring OAuth2 的标准 Token 生成接口
        // 👉 这里才真正进入认证流程：
        //    - 校验 client_id / client_secret
        //    - 调用 UserDetailsService 查数据库
        //    - 校验密码（PasswordEncoder）
        //    - 生成 access_token / refresh_token / JWT
        OAuth2AccessToken oAuth2AccessToken =
                tokenEndpoint.postAccessToken(principal, parameters).getBody();

        // 8️⃣ 对生成的 token 进行缓存和封装
        // 👉 一般会做：
        //    - 存 Redis（用于登出 / 踢人 / 黑名单）
        //    - 封装成 UserVO（返回用户信息 + token）
        return cacheTokenHandler.cacheToken(oAuth2AccessToken);
    }

    @Override
    public UserVO findUserDetail(String loginType, String mobile,String companyNo) {
        //手机登录处理
        String key = OauthCacheConstant.LOGIN_CODE+mobile;
        //存储手机发送的验证码到存在中
//        RBucket<String> code = redissonClient.getBucket(key);
//        if (EmptyUtil.isNullOrEmpty(code.get())){
//            throw  new ProjectException(AuthEnum.CODE_FAIL);
//        }
//        String password = bCryptPasswordEncoder.encode(code.get());
        String password = bCryptPasswordEncoder.encode("123456");
        //处理登录
        UserVO userVO = null;
        switch (loginType){
            case OauthConstant.USER_MOBILE:
                userVO = userFeign.mobileLogin(mobile,companyNo);
                if (!EmptyUtil.isNullOrEmpty(userVO)){
                    userVO.setPassword(password);
                    userVO.setOnlyAuthenticate(false);
                }
                return userVO;
            case OauthConstant.CUSTOMER_MOBILE:
                userVO = customerFeign.mobileLogin(mobile,companyNo);
                if (!EmptyUtil.isNullOrEmpty(userVO)){
                    userVO.setPassword(password);
                    userVO.setOnlyAuthenticate(true);
                }
                return userVO;
            default:
                throw new ProjectException(AuthEnum.LOGIN_FAIL);
        }
    }
}
