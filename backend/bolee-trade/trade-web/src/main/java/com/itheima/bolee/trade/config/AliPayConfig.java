package com.itheima.bolee.trade.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.easysdk.kernel.Config;
import com.itheima.bolee.framework.commons.constant.trade.TradeConstant;
import com.itheima.bolee.framework.commons.dto.trade.PayChannelVO;
import com.itheima.bolee.framework.commons.enums.trade.TradeEnum;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.framework.commons.utils.EmptyUtil;
import com.itheima.bolee.trade.face.PayChannelFace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * @ClassName AlipayConfig.java
 * @Description 支付宝配置类
 */
@Slf4j
@Configuration
public class AliPayConfig {

    @Autowired
    PayChannelFace payChannelFace;

    @Value("${bolee.payment.alipay.environment:PRODUCTION}")
    private String environment;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Value("${ALIPAY_SANDBOX_GATEWAY_HOST:openapi-sandbox.dl.alipaydev.com}")
    private String sandboxGatewayHost;

    @Value("${ALIPAY_SANDBOX_APP_ID:}")
    private String sandboxAppId;

    @Value("${ALIPAY_SANDBOX_MERCHANT_PRIVATE_KEY:}")
    private String sandboxMerchantPrivateKey;

    @Value("${ALIPAY_SANDBOX_PUBLIC_KEY:}")
    private String sandboxAlipayPublicKey;

    @Value("${ALIPAY_SANDBOX_NOTIFY_URL:}")
    private String sandboxNotifyUrl;

    /***
     * @description easy-sdk创建配置
     * @return
     */
    public Config config(String companyNo ){
        if (isSandbox()) {
            requireSandboxCredentials();
            Config sandboxConfig = new Config();
            sandboxConfig.protocol = "https";
            sandboxConfig.gatewayHost = sandboxGatewayHost;
            sandboxConfig.signType = "RSA2";
            sandboxConfig.appId = sandboxAppId;
            sandboxConfig.merchantPrivateKey = sandboxMerchantPrivateKey;
            sandboxConfig.alipayPublicKey = sandboxAlipayPublicKey;
            sandboxConfig.notifyUrl = EmptyUtil.isNullOrEmpty(sandboxNotifyUrl) ? null : sandboxNotifyUrl;
            return sandboxConfig;
        }
        //1、缓存配置
        PayChannelVO payChannelVO = payChannelFace.findPayChannelVlid(TradeConstant.TRADE_CHANNEL_ALI_PAY, companyNo);
        //2、配置信息校验
        if (EmptyUtil.isNullOrEmpty(payChannelVO)){
            throw  new ProjectException(TradeEnum.CONFIG_EMPT);
        }
        //2、创建配置
        Config config = new Config();
        config.protocol = "https";
        config.gatewayHost =payChannelVO.getDomain();
        config.signType = "RSA2";
        config.appId = payChannelVO.getAppId();
        //2.1、配置应用私钥
        config.merchantPrivateKey = payChannelVO.getMerchantPrivateKey();
        //2.2、配置支付宝公钥
        config.alipayPublicKey = payChannelVO.getPublicKey();
        //2.3、可设置异步通知接收服务地址（可选）
        config.notifyUrl = payChannelVO.getNotifyUrl()+"/"+payChannelVO.getCompanyNo();
        //2.4、设置AES密钥，调用AES加解密相关接口时需要（可选）
        config.encryptKey = payChannelVO.getEncryptKey();
        //2.5、配置信息放入configHashMap中
        return config;
    }

    /***
     * @description 通用-sdk创建配置
     * @return
     */
    public DefaultAlipayClient createAlipayClient(String companyNo ){
        if (isSandbox()) {
            requireSandboxCredentials();
            return new DefaultAlipayClient("https://" + sandboxGatewayHost + "/gateway.do",
                    sandboxAppId, sandboxMerchantPrivateKey, "json", "UTF-8", sandboxAlipayPublicKey, "RSA2");
        }
        //1、缓存配置
        PayChannelVO payChannelVO = payChannelFace.findPayChannelVlid(TradeConstant.TRADE_CHANNEL_ALI_PAY, companyNo);
        //2、配置信息校验
        if (EmptyUtil.isNullOrEmpty(payChannelVO)){
            throw  new ProjectException(TradeEnum.CONFIG_EMPT);
        }
        return  new DefaultAlipayClient("https://"+payChannelVO.getDomain()+"/gateway.do",
                payChannelVO.getAppId(),
                payChannelVO.getMerchantPrivateKey(),
                "json",
                "GBK",
                payChannelVO.getPublicKey(),
                "RSA2");
    }

    private boolean isSandbox() {
        String profiles = activeProfiles == null ? "" : activeProfiles.toLowerCase();
        if (profiles.contains("dev") || profiles.contains("demo")) {
            return true;
        }
        return "SANDBOX".equalsIgnoreCase(environment);
    }

    private void requireSandboxCredentials() {
        if (EmptyUtil.isNullOrEmpty(sandboxGatewayHost) || EmptyUtil.isNullOrEmpty(sandboxAppId)
                || EmptyUtil.isNullOrEmpty(sandboxMerchantPrivateKey)
                || EmptyUtil.isNullOrEmpty(sandboxAlipayPublicKey)) {
            throw new ProjectException(TradeEnum.CONFIG_ERROR);
        }
    }

}
