package com.planu.group_meeting.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;

public class CookieUtil {
    public static void createCookie(HttpServletResponse response, String key, String value) {
        Cookie cookie = new Cookie(key, value);

        cookie.setMaxAge(24 * 60 * 60);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setAttribute("SameSite","None");
        cookie.setDomain("15.165.3.168.nip.io");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    public static Cookie deleteCookie(String cookieName){
        Cookie cookie = new Cookie(cookieName,null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setAttribute("SameSite","None");
        cookie.setDomain("15.165.3.168.nip.io");
        return cookie;
    }

    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if(cookies == null){
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
