package com.eatpick;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController @Profile("demo")
public class DemoApi {
 final Store s; final Auth auth;
 DemoApi(Store s,Auth auth){this.s=s;this.auth=auth;}
 @PostMapping("/auth/demo") Object login(){String u=s.createUser("잇픽러");
  for(int i=1;i<=6;i++)s.saveMeal(u,new Api.MealInput("chicken","dinner",2.5,null,java.time.LocalDate.now(Store.ZONE).minusDays(i).atTime(18,0).atZone(Store.ZONE).toInstant()));
  s.saveMeal(u,new Api.MealInput("yogurt","breakfast",1,null,java.time.LocalDate.now(Store.ZONE).atTime(8,10).atZone(Store.ZONE).toInstant()));
  s.saveMeal(u,new Api.MealInput("bibimbap","lunch",1,null,java.time.LocalDate.now(Store.ZONE).atTime(12,40).atZone(Store.ZONE).toInstant()));
  return Map.of("token",auth.session(u));}
 public record Plan(@NotNull Boolean premium){}
 @PutMapping("/api/demo/plan") Object plan(Authentication a,@Valid @RequestBody Plan p){s.demoPlan(a.getName(),p.premium());return s.me(a.getName());}
}
