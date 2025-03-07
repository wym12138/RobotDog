package com.fd.config.picture;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class PConfiguration implements WebMvcConfigurer {

    // 为服务器文件存储的绝对路径
    private String uploadPathing = "/var/www/uploads/feidian/";
    // 您在部署到宝塔试一下
 // 可以了 亲
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /geoserver/postFile方法 到文件目录 /var/www/uploads/feidian/
        registry.addResourceHandler("/geoserver/postFile")
                .addResourceLocations("file:" + uploadPathing);
    }
}
//我微信找你可以吗  可以的 不要告诉平台哈 谢谢啦
//那我先付的那10快就可以了哈
//再转你20 好滴
