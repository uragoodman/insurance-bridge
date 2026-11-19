package com.itheima.bolee.security.details;

import com.alibaba.fastjson.JSONObject;
import com.itheima.bolee.framework.commons.constant.basic.SuperConstant;
import com.itheima.bolee.framework.commons.dto.security.*;
import com.itheima.bolee.framework.commons.utils.EmptyUtil;
import com.itheima.bolee.framework.commons.utils.RegisterBeanHandler;
import com.itheima.bolee.security.feign.CustomerFeign;
import com.itheima.bolee.security.feign.UserFeign;
import com.itheima.bolee.security.handler.LoginAuthHandler;
import com.itheima.bolee.security.base.UserAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @ClassName ReactiveUserDetailsServiceImpl.java
 * @Description 支持flum的身份类实现ReactiveUserDetailsService接口
 */
@Slf4j
@Component("oauth2UserDetailsService")
public class Oauth2UserDetailsServiceImpl implements UserDetailsService {

    //调用RPC原创服务
    @Autowired
    UserFeign userFeign;

    //调用RPC原创服务
    @Autowired
    CustomerFeign customerFeign;

    @Autowired
    RegisterBeanHandler registerBeanHandler;

    @Override
    public UserDetails loadUserByUsername(String principal) throws UsernameNotFoundException {

        // 1️⃣ 将前面封装好的 JSON 字符串（principal）反序列化成 UsernameVO
        // ⚠️ 这里的 principal 不是普通用户名，而是包含：
        //    用户名 + 租户 + 登录方式 + clientId 等信息
        UsernameVO usernameVO = JSONObject.parseObject(principal, UsernameVO.class);


        // =========================
        // 2️⃣ 根据登录方式动态获取对应的登录处理器（策略模式）
        // =========================

        LoginAuthHandler loginAuthHandler = registerBeanHandler
                .getBean(usernameVO.getLoginBeanName(), LoginAuthHandler.class);


        // =========================
        // 3️⃣ 查询用户信息（真正查数据库 / 调远程服务的地方）
        // =========================
        // 根据：
        //   - 登录类型（用户名/手机号/微信）
        //   - 用户账号
        //   - 公司编号（多租户）
        // 查出用户详情,从数据库里查询用户的密码
        UserVO userVO = loginAuthHandler.findUserDetail(
                usernameVO.getLoginType(),
                usernameVO.getUsername(),
                usernameVO.getCompanyNo()
        );


        // =========================
        // 4️⃣ 基础校验（账号是否存在）
        // =========================
        if (EmptyUtil.isNullOrEmpty(userVO)) {
            // 用户不存在 → 认证失败
            throw new DisabledException("无效的账号");
        }

        // =========================
        // 5️⃣ 账号状态校验（是否被禁用）
        // =========================
        if (SuperConstant.DATA_STATE_1.equals(userVO.getDataState())) {
            // 账号被禁用 → 拒绝登录
            throw new LockedException("账户被禁用");
        }


        // =========================
        // 6️⃣ 补充客户端信息（用于后续 token / 业务区分）
        // =========================
        userVO.setClientId(usernameVO.getClientId());


        // =========================
        // 7️⃣ 查询接口权限（资源权限）
        // =========================
        // 从用户服务获取该用户可访问的接口路径
        List<ResourceVO> resourceVOs = userFeign.findResourceByUserId(userVO.getId());

        if (!EmptyUtil.isNullOrEmpty(resourceVOs)){
            // 转成 Set<String>，用于权限判断
            Set<String> resources = resourceVOs.stream()
                    .map(ResourceVO::getRequestPath)  // 例如：/api/user/list
                    .collect(Collectors.toSet());

            // 填充到 userVO
            userVO.setResourceRequestPaths(resources);
        }


        // =========================
        // 8️⃣ 查询角色信息（角色权限）
        // =========================
        List<RoleVO> roleVOs = userFeign.findRoleByUserId(userVO.getId());

        if (!EmptyUtil.isNullOrEmpty(roleVOs)){
            // 提取角色标识（如：admin、operator）
            Set<String> roleLabel = roleVOs.stream()
                    .map(RoleVO::getLabel)
                    .collect(Collectors.toSet());

            userVO.setRoleLabels(roleLabel);
        }


        // =========================
        // 9️⃣ 查询数据权限（更细粒度控制）
        // =========================
        // 构造查询条件（用户ID + 角色）
        QueryDataSecurityVO queryDataSecurityVO = QueryDataSecurityVO.builder()
                .userId(userVO.getId())
                .roleVOs(roleVOs)
                .build();

        // 调用远程服务获取数据权限
        DataSecurityVO dataSecurityVO = userFeign.userDataSecurity(queryDataSecurityVO);

        if (!EmptyUtil.isNullOrEmpty(dataSecurityVO)){
            userVO.setDataSecurityVO(dataSecurityVO);
        }


        // =========================
        // 🔟 返回 UserAuth（关键！）
        // =========================
        // 👉 把 userVO 封装成 Spring Security 可识别的 UserDetails
        // 👉 后续会用于：
        //    - 密码校验（getPassword）
        //    - 权限校验（getAuthorities）
        //    - JWT 生成
        return new UserAuth(userVO);
    }
}
