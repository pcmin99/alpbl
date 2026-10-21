package com.eatpick;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:apitest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("demo")
class ApiIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired Store s; @Autowired Auth auth; @Autowired RecognitionService ai;
 String login()throws Exception{return json.readTree(mvc.perform(post("/auth/demo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();}
 String job(String token,String key)throws Exception{return mvc.perform(post("/api/recognitions").header("Authorization","Bearer "+token).contentType("application/json").content("{\"requestKey\":\""+key+"\",\"sampleFoodCode\":\"kimchistew\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
 @Test void browserCorsPreflight()throws Exception{
  mvc.perform(options("/auth/demo").header("Origin","http://localhost:8081").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:8081"));
 }
 @Test void authenticationAndFieldGating()throws Exception{
  mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());String t=login();
  mvc.perform(get("/api/home").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.total.sodium").doesNotExist()).andExpect(jsonPath("$.meals.length()").value(2));
  mvc.perform(get("/api/reports").header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
  mvc.perform(put("/api/demo/plan").header("Authorization","Bearer "+t).contentType("application/json").content("{\"premium\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.aiBackend").value("llm_api"));
  mvc.perform(get("/api/home").header("Authorization","Bearer "+t)).andExpect(jsonPath("$.total.sodium").exists());
  String r=job(t,"paid");assertEquals("llm_api",json.readTree(r).get("backend").asText());assertTrue(json.readTree(r).get("simulated").asBoolean());
  mvc.perform(post("/api/logout").header("Authorization","Bearer "+t)).andExpect(status().isOk());mvc.perform(get("/api/me").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
 }
 @Test void quotaAndIdempotency()throws Exception{
  String t=login();String first=job(t,"one");assertEquals(first,job(t,"one"));for(int i=2;i<=5;i++)job(t,"job"+i);
  mvc.perform(post("/api/recognitions").header("Authorization","Bearer "+t).contentType("application/json").content("{\"requestKey\":\"six\",\"sampleFoodCode\":\"salad\"}")).andExpect(status().isTooManyRequests());
  assertEquals(first,job(t,"one"));
 }
 @Test void ownershipNutritionAndShopping()throws Exception{
  String t=login(),other=login();String r=mvc.perform(post("/api/meals").header("Authorization","Bearer "+t).contentType("application/json").content("{\"foodCode\":\"chicken\",\"slot\":\"dinner\",\"servings\":1.5}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String id=json.readTree(r).get("id").asText();
  mvc.perform(delete("/api/meals/"+id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
  mvc.perform(get("/api/home").header("Authorization","Bearer "+t)).andExpect(jsonPath("$.total.protein").value(68.5));
  mvc.perform(post("/api/meals").header("Authorization","Bearer "+t).contentType("application/json").content("{\"foodCode\":\"chicken\",\"slot\":\"invalid\",\"servings\":0}")).andExpect(status().isBadRequest());
  String list=mvc.perform(get("/api/shopping").header("Authorization","Bearer "+t)).andReturn().getResponse().getContentAsString();String item=json.readTree(list).get(0).get("id").asText();mvc.perform(delete("/api/shopping/"+item).header("Authorization","Bearer "+t)).andExpect(status().isOk());mvc.perform(get("/api/shopping").header("Authorization","Bearer "+t)).andExpect(jsonPath("$.length()").value(2));
 }
 @Test void concurrentQuotaCannotExceedFive()throws Exception{
  String u=s.createUser("동시성검증");ExecutorService pool=Executors.newFixedThreadPool(8);try{List<Callable<Integer>> tasks=new ArrayList<>();for(int i=0;i<8;i++){final int n=i;tasks.add(()->{try{ai.recognize(u,new RecognitionService.Input("parallel"+n,"salad",null,null,null,null));return 200;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}});}var results=pool.invokeAll(tasks);long ok=0;for(var r:results){int status=r.get();assertTrue(status==200||status==429);if(status==200)ok++;}assertEquals(5,ok);}finally{pool.shutdownNow();}
 }
 @Test void oneTimeAuthCodeIsBoundToVerifier(){String u=s.createUser("로그인검증"),v="a".repeat(64);String code=auth.code(u,Auth.challenge(v));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.exchange(code,"b".repeat(64)));assertNotNull(auth.exchange(code,v).get("token"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.exchange(code,v));}
}
