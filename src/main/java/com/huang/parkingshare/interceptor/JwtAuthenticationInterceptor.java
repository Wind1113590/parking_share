package com.huang.parkingshare.interceptor;

import com.huang.parkingshare.context.CurrentUserHolder;
import com.huang.parkingshare.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    @Qualifier("customStringRedisTemplate")  // 注意指定名称
    private RedisTemplate<String,String> redisTemplate;

    public static final String TOKEN_PREFIX = "token:user:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("未携带token");
            return false;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(401);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("token无效或过期");
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);

        String key = TOKEN_PREFIX + userId + ":" + token;
        // 3. 验证 Redis 中是否存在
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            response.setStatus(401);
            response.getWriter().write("Token has been revoked");
            return false;
        }

        // 4. 存入 ThreadLocal
        CurrentUserHolder.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        CurrentUserHolder.clear();
    }
}