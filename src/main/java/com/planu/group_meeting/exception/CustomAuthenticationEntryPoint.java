package com.planu.group_meeting.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planu.group_meeting.dto.BaseResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        BaseResponse errorResponse = BaseResponse.toEntity(HttpStatus.UNAUTHORIZED, "로그인이 필요한 URL입니다.");
        new ObjectMapper().writeValue(response.getWriter(), errorResponse);
    }
}
