package hu.reptrack.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC kiegészítések: a kötelező jelszócsere kikényszerítése ({@link PasswordChangeInterceptor}). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PasswordChangeInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(PasswordChangeInterceptor.CHANGE_PATH, "/error",
                        "/css/**", "/js/**", "/images/**", "/webjars/**");
    }
}
