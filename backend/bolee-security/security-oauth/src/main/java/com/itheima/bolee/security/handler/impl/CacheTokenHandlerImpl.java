package com.itheima.bolee.security.handler.impl;

import com.itheima.bolee.framework.commons.constant.security.OauthCacheConstant;
import com.itheima.bolee.framework.commons.properties.SecurityConfigProperties;
import com.itheima.bolee.framework.commons.constant.security.OauthConstant;
import com.itheima.bolee.security.handler.CacheTokenHandler;
import com.itheima.bolee.framework.commons.dto.security.DataSecurityVO;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName CacheTokenHandlerImpl.java
 * @Description Token缓存处理
 */
@Component
@EnableConfigurationProperties(SecurityConfigProperties.class)
public class CacheTokenHandlerImpl implements CacheTokenHandler {

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    SecurityConfigProperties securityConfigProperties;

    @Override
    public UserVO cacheToken(OAuth2AccessToken oAuth2AccessToken){

        // =========================
        // 1️⃣ 从 OAuth2 token 中提取“增强信息”（JWT payload 扩展字段）
        // =========================

        // 用户ID（核心身份标识）
        Long id = Long.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.USER_ID_KEY).toString());

        // jti：token唯一标识（整个系统的“会话ID”）
        String userToken = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.JTI_KEY));

        // 手机号
        String mobile = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.MOBILE_KEY));

        // 微信 openId（第三方登录用）
        String openId = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.OPEN_ID_KEY));

        // 权限资源（接口路径级权限）
        Set<String> resources = (Set<String>) oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.RESOURCS_KEY);

        // 角色信息（角色标识）
        Set<String> roles = (Set<String>) oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.ROLES_KEY);

        // 用户名
        String username = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.USER_NAME_KEY));

        // 性别
        String sex = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.SEX_KEY));

        // 客户端ID（区分PC/APP）
        String clientId = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.CLIENT_ID_KEY));

        // 真实姓名
        String realName = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.REAL_NAME_KEY));

        // 部门编号
        String deptNo = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.DEPT_NO_KEY));

        // 岗位编号
        String postNo = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.POST_NO_KEY));

        // 是否只做认证（可能用于只登录不授权的场景）
        Boolean onlyAuthenticate = (Boolean) oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.ONLY_AUTHENTICATE_KEY);

        // 数据权限信息（行级/列级权限控制）
        DataSecurityVO dataSecurityVO = (DataSecurityVO) oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.DATA_SECURITY_KEY);

        // 公司编号（多租户关键字段）
        String companyNo = String.valueOf(oAuth2AccessToken.getAdditionalInformation()
                .get(OauthConstant.COMPANY_NO_KEY));

        // =========================
        // 2️⃣ 缓存 access_token 到 Redis
        // =========================

        // 获取 access_token 字符串（JWT）
        String accessToken = oAuth2AccessToken.getValue();

        // Redis key：ACCESS_TOKEN + jti
        RBucket<String> accessTokenBucket =
                redissonClient.getBucket(OauthCacheConstant.ACCESS_TOKEN + userToken);

        // 获取 access_token 过期时间（秒）
        long accessTokenExpiresIn = Long.valueOf(
                oAuth2AccessToken.getAdditionalInformation()
                        .get(OauthConstant.EXPIRES_IN_KEY).toString()
        );

        // 存入 Redis（设置过期时间）
        accessTokenBucket.set(accessToken, accessTokenExpiresIn, TimeUnit.SECONDS);


        // =========================
        // 3️⃣ 缓存 refresh_token 到 Redis
        // =========================

        // 获取 refresh_token
        OAuth2RefreshToken oAuth2RefreshToken = oAuth2AccessToken.getRefreshToken();
        String refreshToken = oAuth2RefreshToken.getValue();

        // refresh_token 过期时间（一般比 access_token 长）
        long refreshTokenExpiresIn = securityConfigProperties.getRefreshTokenValiditySeconds();

        // Redis key：REFRESH_TOKEN + jti
        RBucket<String> refreshTokenBucket =
                redissonClient.getBucket(OauthCacheConstant.REFRESH_TOKEN + userToken);

        // 存入 Redis
        refreshTokenBucket.set(refreshToken, refreshTokenExpiresIn, TimeUnit.SECONDS);


        // =========================
        // 4️⃣ 建立 username → token 的绑定关系
        // =========================

        // 用于“踢人下线 / 单点登录”
        // 一个用户名只能对应一个 userToken（或最后一次登录）
        RBucket<String> usernameBucket =
                redissonClient.getBucket(OauthCacheConstant.USER_TOKEN_BIND + username);

        usernameBucket.set(userToken, refreshTokenExpiresIn, TimeUnit.SECONDS);


        // =========================
        // 5️⃣ 构建返回给前端的用户信息对象
        // =========================

        UserVO userVO = UserVO.builder()
                .id(id)                          // 用户ID
                .clientId(clientId)              // 客户端ID
                .companyNo(companyNo)            // 租户
                .username(username)              // 用户名
                .userToken(userToken)            // 会话ID（jti）
                .resourceRequestPaths(resources) // 权限资源
                .roleLabels(roles)               // 角色
                .openId(openId)                  // 微信ID
                .mobile(mobile)                 // 手机号
                .deptNo(deptNo)                 // 部门
                .postNo(postNo)                 // 岗位
                .dataSecurityVO(dataSecurityVO) // 数据权限
                .onlyAuthenticate(onlyAuthenticate)
                .sex(sex)
                .realName(realName)
                .build();


        // =========================
        // 6️⃣ 缓存用户信息（userToken → UserVO）
        // =========================

        // Redis key：USER_TOKEN + jti
        RBucket<UserVO> userTokenBucket =
                redissonClient.getBucket(OauthCacheConstant.USER_TOKEN + userToken);

        userTokenBucket.set(userVO, refreshTokenExpiresIn, TimeUnit.SECONDS);


        // =========================
        // 7️⃣ 返回用户信息（给前端）
        // =========================
        return userVO;
    }
    @Override
    public Boolean deleteToken(UserVO userVO) {
        //删除accessToken
        RBucket<String> accessTokenBucket = redissonClient.getBucket(OauthCacheConstant.ACCESS_TOKEN + userVO.getUserToken());
        boolean deleteAccessToken = accessTokenBucket.delete();
        //删除refreshToken
        RBucket<String> refreshTokenBucket = redissonClient.getBucket(OauthCacheConstant.REFRESH_TOKEN + userVO.getUserToken());
        boolean deleteRefreshToken = refreshTokenBucket.delete();
        //删除绑定username
        RBucket<String> usernameBucket = redissonClient.getBucket(OauthCacheConstant.USER_TOKEN_BIND + userVO.getUsername());
        boolean deleteUsername = usernameBucket.delete();
        //删除用户和userToken关联
        RBucket<UserVO> userTokenBucket = redissonClient.getBucket(OauthCacheConstant.USER_TOKEN + userVO.getUserToken());
        boolean deleteUserTokenBucket = userTokenBucket.delete();
        //清理完成
        if (deleteAccessToken&&deleteRefreshToken&&deleteUsername&&deleteUserTokenBucket){
            return true;
        }else {
            return false;
        }
    }

}
