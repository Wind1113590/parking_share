package com.huang.parkingshare.controller;

import com.huang.parkingshare.common.Result;
import com.huang.parkingshare.dto.LoginRequest;
import com.huang.parkingshare.vo.LoginVO;
import com.huang.parkingshare.dto.RegisterRequest;
import com.huang.parkingshare.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    @Autowired
    @Qualifier("customStringRedisTemplate")  // 注意指定名称
    private RedisTemplate<String, String> redisTemplate;


    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功");
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        LoginVO vo = authService.login(request);
        return Result.success(vo);
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return Result.success("登出成功");
    }

    @PostMapping("/admin/kickAll")
    public Result<String> kick(@RequestHeader("Authorization") String authHeader) {
        authService.kick(authHeader);
        return Result.success("踢人成功");
    }
}