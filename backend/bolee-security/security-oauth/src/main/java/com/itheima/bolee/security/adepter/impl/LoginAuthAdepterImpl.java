package com.itheima.bolee.security.adepter.impl;

import com.itheima.bolee.framework.commons.constant.security.CompanyCacheConstant;
import com.itheima.bolee.framework.commons.constant.security.OauthConstant;
import com.itheima.bolee.framework.commons.dto.security.CompanyVO;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.enums.security.AuthEnum;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.framework.commons.utils.EmptyUtil;
import com.itheima.bolee.framework.commons.utils.RegisterBeanHandler;
import com.itheima.bolee.security.handler.LoginAuthHandler;
import com.itheima.bolee.security.adepter.LoginAuthAdepter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;

/**
 * @ClassName LoginAuthAdepterImpl.java
 * @Description 登录适配器
 */
@Component
public class LoginAuthAdepterImpl implements LoginAuthAdepter {

    @Autowired
    RegisterBeanHandler registerBeanHandler;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    HttpServletRequest httpServletRequest;

    @Override
    public UserVO adepterRoutes(Principal principal, Map<String, String> parameters)
            throws HttpRequestMethodNotSupportedException {

        // 1️⃣ 从请求头中获取域名（多租户关键）
        // x-forwarded-host 一般是网关转发过来的真实访问域名
        // 例如：a.example.com:8080 → 取出 a.example.com
        String host = httpServletRequest.getHeaders("x-forwarded-host")
                .nextElement()
                .split(",")[0]   // 防止多个代理，取第一个
                .split(":")[0];  // 去掉端口号

        // 2️⃣ 构造 Redis 中的公司缓存 key
        // 例如：website:a.example.com
        String key = CompanyCacheConstant.WEBSITE + host;

        // 3️⃣ 从 Redis 获取公司信息（多租户识别）
        // 这里使用 Redisson 的 RBucket，相当于 key-value 存储
        RBucket<CompanyVO> bucket = redissonClient.getBucket(key);

        // 4️⃣ 获取缓存中的公司信息
        CompanyVO companyVO = bucket.get();

        // 5️⃣ 如果没有查到公司，说明这个域名不合法（非法租户）
        if (EmptyUtil.isNullOrEmpty(companyVO)) {
            // 抛出异常：域名校验失败
            throw new ProjectException(AuthEnum.HSOT_FAIL);
        }

        // =========================
        // 到这里：已经确定“你属于哪个公司”
        // =========================


        // 6️⃣ 获取登录类型（决定用哪种登录方式）
        // 例如：USER_USERNAME、USER_MOBILE、WECHAT 等
        String loginType = parameters.get(OauthConstant.LOGIN_TYPE_KEY);

        // 7️⃣ 登录类型不能为空
        if (EmptyUtil.isNullOrEmpty(loginType)) {
            throw new ProjectException(AuthEnum.LOGIN_FAIL);
        }

        // 8️⃣ 根据 login_type 解析出对应的 Bean 名称
        // login_type 可能类似：xxx-username → 取后半部分
        // 再从常量映射中拿到具体 Bean 名
        String loginBeanName = OauthConstant.loginBeanNames.get(
                loginType.split("-")[1]
        );

        // 9️⃣ 从 Spring 容器中获取对应的登录处理器（策略模式）
        // 不同登录方式对应不同实现类
        LoginAuthHandler loginAuthHandler =
                registerBeanHandler.getBean(loginBeanName, LoginAuthHandler.class);

        // 🔟 调用具体登录处理器执行登录逻辑
        // 并把：租户信息 + 登录参数 一起传进去
        return loginAuthHandler.loginHandler(
                principal,
                parameters,
                loginBeanName,
                companyVO
        );
    }
}
