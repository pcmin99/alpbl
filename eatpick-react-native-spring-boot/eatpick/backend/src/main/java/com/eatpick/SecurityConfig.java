package com.eatpick;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.*;
import java.util.*;
@Configuration
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity http,@org.springframework.beans.factory.annotation.Qualifier("cors") CorsConfigurationSource corsSource,Auth auth,ObjectProvider<ClientRegistrationRepository> registrations,@Value("${app.mobile-redirect}") String redirect)throws Exception{
  http.csrf(c->c.disable()).cors(c->c.configurationSource(corsSource)).authorizeHttpRequests(a->a.requestMatchers("/auth/**","/oauth2/**","/login/**","/error","/health").permitAll().anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->{s.setStatus(401);s.setContentType("application/json;charset=UTF-8");s.getWriter().write("{\"message\":\"다시 로그인해주세요\"}");}))
   .addFilterBefore(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws java.io.IOException,ServletException{
    if(r.getRequestURI().startsWith("/api/")){SecurityContextHolder.clearContext();String h=r.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer ")){String u=auth.user(h.substring(7));if(u!=null)SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of()));}}
    chain.doFilter(r,s);
   }},UsernamePasswordAuthenticationFilter.class);
  if(registrations.getIfAvailable()!=null)http.oauth2Login(o->o.successHandler((r,s,a)->{
   var token=(OAuth2AuthenticationToken)a;String provider=token.getAuthorizedClientRegistrationId();Map<String,Object> attrs=token.getPrincipal().getAttributes();Object subject;
   if(provider.equals("naver")){subject=((Map<?,?>)attrs.get("response")).get("id");}else if((provider.equals("google")||provider.equals("apple"))){subject=attrs.get("sub");}else subject=attrs.get("id");
   Object c=r.getSession().getAttribute("mobileChallenge");if(c==null||subject==null){s.sendError(400);return;}
   String u=auth.social(provider,subject.toString());String code=auth.code(u,c.toString());r.getSession().invalidate();SecurityContextHolder.clearContext();s.sendRedirect(redirect+"?code="+code);
  }).failureHandler((r,s,e)->s.sendRedirect(redirect+"?error=login_failed")));
  return http.build();
 }
 @Bean CorsConfigurationSource cors(@Value("${app.cors-origins}") String origins){var c=new CorsConfiguration();c.setAllowedOrigins(Arrays.asList(origins.split(",")));c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type","Authorization"));c.setAllowCredentials(true);var src=new UrlBasedCorsConfigurationSource();src.registerCorsConfiguration("/**",c);return src;}
}
