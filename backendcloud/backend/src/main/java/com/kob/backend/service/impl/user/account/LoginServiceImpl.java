package com.kob.backend.service.impl.user.account;

import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.account.LoginService;
import com.kob.backend.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Override
    public Map<String, String> getToken(String username, String password) {
        UsernamePasswordAuthenticationToken authenticationToken=
                new UsernamePasswordAuthenticationToken(username,password);//加密


        Authentication authenticate= authenticationManager.authenticate(authenticationToken);//如果登陆失败，会自动处理
        UserDetailsImpl loginUser=(UserDetailsImpl) authenticate.getPrincipal();//UserDetails的实例，可以获取到代表当前用户的信息
        User user=loginUser.getUser();
        String jwt= JwtUtil.createJWT(user.getId().toString());//前面的工具类得到token

        Map<String,String> map=new HashMap<>();
        map.put("error_message","success");
        map.put("token",jwt);

        return map;
    }
}
