package com.guochang.chanpicturebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云对象存储客户端
 *
 * @author <a href="https://github.com/Guo-chang16">guochang</a>
 * @from <a href="https://blog.csdn.net/chan12345678910?spm=1011.2480.3001.5343">CSDN_blogger</a>
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {
    /** 域名     */
    private String host;
    /** secretId     */
    private String secretId;
    /** 密钥（注意不要泄露）     */
    private String secretKey;
    /** 区域     */
    private String region;
    /** 桶名     */
    private String bucket;

    @Bean
    public COSClient cosClient() {
        // 初始化用户身份信息(secretId, secretKey)
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置bucket的区域, COS地域的简称请参照 [对象存储 地域和访问域名_腾讯云](https://www.qcloud.com/document/product/436/6224)
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 生成cos客户端
        return new COSClient(cred, clientConfig);
    }
}
