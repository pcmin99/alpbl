package com.eatpick;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import java.util.*;
@RestController
public class AuthApi {
 final Auth auth;final ObjectProvider<ClientRegistrationRepository> registrations;
 AuthApi(Auth auth,ObjectProvider<ClientRegistrationRepository> registrations){this.auth=auth;this.registrations=registrations;}
 @GetMapping("/health") Object health(){return Map.of("status","ok");}
 @GetMapping("/auth/start/{provider}") void start(@PathVariable String provider,@RequestParam String challenge,HttpServletRequest req,HttpServletResponse res)throws java.io.IOException{
  if(!List.of("google","kakao","naver","apple").contains(provider)||!challenge.matches("[A-Za-z0-9_-]{43}"))throw Store.error(400,"로그인 요청을 확인해주세요");
  var repo=registrations.getIfAvailable();if(repo==null||repo.findByRegistrationId(provider)==null)throw Store.error(503,"소셜 로그인 앱 키 설정이 필요해요");
  req.getSession(true).setAttribute("mobileChallenge",challenge);res.sendRedirect("/oauth2/authorization/"+provider);
 }
 public record Exchange(@NotBlank String code,@Pattern(regexp="[A-Za-z0-9._~-]{43,128}") @NotNull String verifier){}
 @PostMapping("/auth/exchange") Object exchange(@Valid @RequestBody Exchange e){return auth.exchange(e.code(),e.verifier());}
 @PostMapping("/api/logout") Object logout(@RequestHeader("Authorization") String token){auth.logout(token.substring(7));return Map.of("ok",true);}
}
