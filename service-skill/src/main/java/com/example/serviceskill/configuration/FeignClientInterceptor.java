package com.example.serviceskill.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class FeignClientInterceptor  {
/*
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader(AUTHORIZATION_HEADER);
            if (token != null && token.startsWith(BEARER_PREFIX)) {
                log.info("Adding token to Feign request: {}", token);
                template.header(AUTHORIZATION_HEADER, token);
            } else {
                log.warn("No valid token found in the request headers.");
            }
        } else {
            log.warn("RequestContextHolder is null. Cannot retrieve the token.");
        }
    }
*/
}