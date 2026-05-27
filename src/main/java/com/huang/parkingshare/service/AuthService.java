package com.huang.parkingshare.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.common.Result;
import com.huang.parkingshare.dto.LoginRequest;
import com.huang.parkingshare.vo.LoginVO;
import com.huang.parkingshare.dto.RegisterRequest;
import com.huang.parkingshare.entity.User;
import com.huang.parkingshare.mapper.UserMapper;
import com.huang.parkingshare.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

@Service
public class AuthService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    @Qualifier("customStringRedisTemplate")  // 注意指定名称
    private RedisTemplate<String, String> redisTemplate;

    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:jti:";


    public void register(RegisterRequest request) {
        // 检查手机号是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, request.getPhone());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("手机号已注册");
        }

        User user = new User();
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() == null ? 0 : request.getRole());
        user.setNickname(request.getNickname());
        user.setBalance(BigDecimal.ZERO);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    public LoginVO login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, request.getPhone());
        User user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("手机号或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getPhone());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());

        return vo;
    }


    public void logout(String authHeader) {
        String token = authHeader.substring(7);
        String jti = jwtUtil.getJtiFromToken(token);
        // 获取 token 剩余有效期
        long ttl = jwtUtil.getRemainingTimeMillis(token);

        if (ttl > 0) {
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttl));
        }
    }

    public void kick(String authHeader) {
        String token = authHeader.substring(7);
        // 校验 token 合法性（可选，仅用于确保 token 有效）
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("无效token");
        }
        String jti = jwtUtil.getJtiFromToken(token);

        long ttl = jwtUtil.getRemainingTimeMillis(token);
        if (ttl > 0) {
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttl));
        }
    }
}