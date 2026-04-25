package com.stocklog.global.config;

import com.stocklog.global.config.interceptor.LoginCheckInterceptor;
import com.stocklog.global.config.resolver.LoginUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",                        // 메인 페이지
                        "/auth/**",                 // 로그인, 회원가입 관련 경로 (현재 templates/auth 구조 반영)
                        "/login", "/signup",        // 직접적인 URL 매핑이 따로 있다면 추가
                        "/css/**", "/js/**",        // 정적 리소스 (JS 폴더가 생길 것에 대비)
                        "/images/**", "/logo/**",   // 정적 리소스 (이미지)
                        "/*.ico",                   // 파비콘
                        "/error", "/error/**"       // 에러 페이지 및 스프링 부트 기본 에러 핸들링 경로
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }
}
