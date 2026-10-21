package com.eatpick;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
@Service
public class RecognitionService {
 final Store s; final TransactionTemplate tx; final ObjectMapper json; final boolean demo; final String url,key,model;
 RecognitionService(Store s,PlatformTransactionManager tm,ObjectMapper json,@Value("${app.demo:false}") boolean demo,@Value("${app.llm.url:}") String url,@Value("${app.llm.key:}") String key,@Value("${app.llm.model:unconfigured}") String model){this.s=s;tx=new TransactionTemplate(tm);this.json=json;this.demo=demo;this.url=url;this.key=key;this.model=model;}
 public record Candidate(@NotBlank String foodCode,@DecimalMin("0") @DecimalMax("1") Double confidence){}
 public record Input(@NotBlank @Size(max=80) String requestKey,@Size(max=40) String sampleFoodCode,@Size(max=8000000) String imageBase64,@Pattern(regexp="image/jpeg|image/png") String mimeType,@Size(max=5) List<@Valid Candidate> deviceCandidates,@Size(max=120) String deviceModelVersion){}
 public Map<String,Object> recognize(String u,Input in){
  boolean[] fresh={false};
  String job=tx.execute(st->{
   s.one("SELECT id FROM users WHERE id=? FOR UPDATE",u);
   var existing=s.rows("SELECT id FROM recognition_jobs WHERE user_id=? AND request_key=?",u,in.requestKey());
   if(!existing.isEmpty())return existing.get(0).get("id").toString();
   boolean paid=s.premium(u);
   if(!demo && in.sampleFoodCode()!=null)throw Store.error(400,"실제 모드에서는 샘플 인식을 사용할 수 없어요");
   if(!demo && paid && (url.isBlank()||key.isBlank()||model.equals("unconfigured")))throw Store.error(503,"LLM API 설정이 필요해요");
   if(!demo && paid && (in.imageBase64()==null||in.mimeType()==null))throw Store.error(400,"음식 사진이 필요해요");
   if(!demo && !paid && (in.deviceCandidates()==null||in.deviceCandidates().isEmpty()||in.deviceModelVersion()==null))throw Store.error(503,"자체 온디바이스 모델 연결이 필요해요");
   if(!paid){var date=LocalDate.now(Store.ZONE);var usage=s.rows("SELECT used_count FROM scan_daily_usage WHERE user_id=? AND usage_date=?",u,date);if(usage.isEmpty())s.db().update("INSERT INTO scan_daily_usage VALUES(?,?,0)",u,date);if(s.db().update("UPDATE scan_daily_usage SET used_count=used_count+1 WHERE user_id=? AND usage_date=? AND used_count<5",u,date)==0)throw Store.error(429,"오늘 무료 사진 인식 5회를 모두 사용했어요");}
   String id=Store.id();String ver=demo?(paid?"sample-llm-v1":"sample-mobilenet-v1"):(paid?model:in.deviceModelVersion());
   s.db().update("INSERT INTO recognition_jobs(id,user_id,request_key,plan_code,backend,model_version,simulated,status) VALUES(?,?,?,?,?,?,?,?)",id,u,in.requestKey(),paid?"premium":"free",paid?"llm_api":"self_hosted",ver,demo,"running");fresh[0]=true;return id;
  });
  if(!fresh[0])return result(u,job);
  String attempt=null;
  try {
   List<Candidate> candidates;
   var j=s.one("SELECT * FROM recognition_jobs WHERE id=?",job);
   if(demo){String f=in.sampleFoodCode()==null?"kimchistew":in.sampleFoodCode();s.one("SELECT id FROM foods WHERE code=?",f);candidates=List.of(new Candidate(f,"llm_api".equals(j.get("backend"))?.96:.87));}
   else if("self_hosted".equals(j.get("backend"))){candidates=in.deviceCandidates();}
   else {
    attempt=Store.id();s.db().update("INSERT INTO llm_api_attempts(id,job_id,model_version,status) VALUES(?,?,?,'running')",attempt,job,model);
    if(!URI.create(url).getScheme().equals("https"))throw Store.error(503,"LLM 게이트웨이는 HTTPS 주소가 필요해요");
    var payload=Map.of("model",model,"imageBase64",in.imageBase64(),"mimeType",in.mimeType(),"allowedFoodCodes",s.rows("SELECT code FROM foods").stream().map(x->x.get("code")).toList());
    var req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45)).header("Content-Type","application/json").header("Authorization","Bearer "+key).header("Idempotency-Key",job).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
    var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(req,HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()<200||response.statusCode()>=300)throw Store.error(502,"외부 AI 요청이 실패했어요");
    var root=json.readTree(response.body());var list=root.path("candidates");if(!list.isArray()||list.isEmpty()||list.size()>5)throw Store.error(502,"AI 응답 형식을 확인해주세요");
    candidates=new ArrayList<>();for(var c:list)candidates.add(new Candidate(c.path("foodCode").asText(),c.hasNonNull("confidence")?c.get("confidence").asDouble():null));
    Integer it=root.hasNonNull("inputTokens")?root.get("inputTokens").asInt():null;Integer ot=root.hasNonNull("outputTokens")?root.get("outputTokens").asInt():null;
    s.db().update("UPDATE llm_api_attempts SET status='succeeded',input_tokens=?,output_tokens=? WHERE id=?",it,ot,attempt);
   }
   final var cs=candidates;
   tx.executeWithoutResult(st->{Set<String> seen=new HashSet<>();int rank=0;for(var c:cs){if(!seen.add(c.foodCode())||c.confidence()!=null&&(!Double.isFinite(c.confidence())||c.confidence()<0||c.confidence()>1))throw Store.error(502,"유효하지 않은 AI 후보예요");var f=s.one("SELECT id FROM foods WHERE code=?",c.foodCode());s.db().update("INSERT INTO recognition_candidates VALUES(?,?,?,?)",job,f.get("id"),++rank,c.confidence());}s.db().update("UPDATE recognition_jobs SET status='succeeded' WHERE id=?",job);});
   return result(u,job);
  }catch(Exception e){s.db().update("UPDATE recognition_jobs SET status='failed',error_code='ANALYSIS_FAILED' WHERE id=?",job);if(attempt!=null)s.db().update("UPDATE llm_api_attempts SET status='failed',error_code='GATEWAY_ERROR' WHERE id=?",attempt);if(e instanceof org.springframework.web.server.ResponseStatusException r)throw r;throw Store.error(502,"AI 분석을 완료하지 못했어요. 잠시 후 다시 시도해주세요");}
 }
 Map<String,Object> result(String u,String job){var j=new LinkedHashMap<>(s.one("SELECT * FROM recognition_jobs WHERE id=? AND user_id=?",job,u));boolean paid="premium".equals(j.get("plan_code"))&&s.premium(u);j.put("candidates",s.rows("SELECT f.*,c.confidence FROM recognition_candidates c JOIN foods f ON c.food_id=f.id WHERE job_id=? ORDER BY candidate_rank",job).stream().map(f->s.mask(f,paid)).toList());return j;}
}
