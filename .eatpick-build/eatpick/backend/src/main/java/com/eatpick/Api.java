package com.eatpick;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class Api {
 final Store s; final RecognitionService ai;
 Api(Store s,RecognitionService ai){this.s=s;this.ai=ai;}
 String uid(Authentication a){return a.getName();}
 public record MealInput(@NotBlank String foodCode,@Pattern(regexp="breakfast|lunch|dinner|snack") @NotNull String slot,@DecimalMin("0.1") @DecimalMax("10") double servings,String recognitionJobId,Instant eatenAt){}
 public record GoalInput(@Pattern(regexp="diet|maintain|muscle_gain") @NotNull String goalType,@Min(1) @Max(10000) double kcal,@Min(0) @Max(2000) double carb,@Min(1) @Max(1000) double protein,@Min(0) @Max(1000) double fat,@Min(0) @Max(1000) double sugar,@Min(0) @Max(20000) double sodium,@Min(0) @Max(500) double fiber){}
 @GetMapping("/me") Object me(Authentication a){return s.me(uid(a));}
 @PutMapping("/me/goal") Object goal(Authentication a,@Valid @RequestBody GoalInput g){s.setGoal(uid(a),g);return s.me(uid(a));}
 @GetMapping("/foods") Object foods(Authentication a,@RequestParam(defaultValue="") String q){return s.foods(uid(a),q);}
 @GetMapping("/home") Object home(Authentication a,@RequestParam(required=false) LocalDate date){return s.home(uid(a),date==null?LocalDate.now(Store.ZONE):date);}
 @PostMapping("/meals") Object save(Authentication a,@Valid @RequestBody MealInput m){return s.saveMeal(uid(a),m);}
 @DeleteMapping("/meals/{id}") Object delete(Authentication a,@PathVariable String id){s.deleteMeal(uid(a),id);return Map.of("ok",true);}
 @GetMapping("/protein-trend") Object trend(Authentication a){return s.rows("SELECT local_date,SUM(protein) protein FROM meals WHERE user_id=? AND local_date BETWEEN ? AND ? GROUP BY local_date ORDER BY local_date",uid(a),LocalDate.now(Store.ZONE).minusDays(6),LocalDate.now(Store.ZONE));}
 @GetMapping("/reports") Object report(Authentication a,@RequestParam(defaultValue="7") int days){if(days!=7&&days!=28)throw Store.error(400,"7일 또는 28일을 선택해주세요");return s.report(uid(a),days);}
 @GetMapping("/products") Object products(Authentication a,@RequestParam(defaultValue="basic") String mode){if(!List.of("basic","ai").contains(mode))throw Store.error(400,"추천 모드를 확인해주세요");return s.products(uid(a),mode);}
 @GetMapping("/shopping") Object shopping(Authentication a){return s.shopping(uid(a));}
 public record ProductInput(@NotBlank String productId){}
 @PostMapping("/shopping") Object addShopping(Authentication a,@Valid @RequestBody ProductInput p){s.addShopping(uid(a),p.productId());return Map.of("ok",true);}
 @DeleteMapping("/shopping/{id}") Object dismiss(Authentication a,@PathVariable String id){s.dismissShopping(uid(a),id);return Map.of("ok",true);}
 @PostMapping("/recognitions") Object recognize(Authentication a,@Valid @RequestBody RecognitionService.Input in){return ai.recognize(uid(a),in);}
 @GetMapping("/recognitions/{id}") Object job(Authentication a,@PathVariable String id){return ai.result(uid(a),id);}
 @RestControllerAdvice static class Errors {
  @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(e.getReason(),"요청을 처리할 수 없어요")));}
  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<?> validation(Exception e){return ResponseEntity.badRequest().body(Map.of("message","입력 값을 확인해주세요"));}
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("message","요청 형식을 확인해주세요"));}
 }
}
