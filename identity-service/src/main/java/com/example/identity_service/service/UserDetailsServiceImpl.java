package com.example.identity_service.service;

import com.example.identity_service.entity.User;
import com.example.identity_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

//Công cụ cho DAO sử dụng
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository repo;

    //Đây là hàm duy nhất được dùng tự động ở Provider thuộc Manager
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException{
        User user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + username));
        //Gồm username và password
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }


    public UserDetails loadUserForJwt(String username) {
        // 1. Lấy cái "User Rỗng" (Shadow User) chỉ có role
        User shadowUser = repo.findShadowUser(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + username));

        // 2. Chỉ gồm Username và Role
        return org.springframework.security.core.userdetails.User.builder()
                .username(shadowUser.getUsername()) // Có dữ liệu
                .password("")//Để rỗng vì k cần
                .roles(shadowUser.getRole().name()) // Có dữ liệu
                .build();
    }

}
