package com.eatpick;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:live;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.sql.init.mode=always","app.demo=false"})
@AutoConfigureMockMvc @ActiveProfiles("mysql")
class LiveModeTest {
 @Autowired MockMvc mvc; @Autowired Store s; @Autowired Auth auth;
 @Test void demoRoutesAbsentAndMissingModelsDoNotFallback()throws Exception{
  mvc.perform(post("/auth/demo")).andExpect(status().isNotFound());String u=s.createUser("실제모드");String token=auth.session(u);
  mvc.perform(put("/api/demo/plan").header("Authorization","Bearer "+token).contentType("application/json").content("{\"premium\":true}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/recognitions").header("Authorization","Bearer "+token).contentType("application/json").content("{\"requestKey\":\"free\"}")).andExpect(status().isServiceUnavailable());s.demoPlan(u,true);
  mvc.perform(post("/api/recognitions").header("Authorization","Bearer "+token).contentType("application/json").content("{\"requestKey\":\"paid\",\"imageBase64\":\"aGVsbG8=\",\"mimeType\":\"image/jpeg\"}")).andExpect(status().isServiceUnavailable());
 }
}
