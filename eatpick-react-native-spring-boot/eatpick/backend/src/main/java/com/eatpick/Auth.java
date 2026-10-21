package com.eatpick;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
@Service
public class Auth {
 final Store s;final SecureRandom random=new SecureRandom();
 Auth(Store s){this.s=s;}
 static String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 static String challenge(String v){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException(e);}}
 String secret(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 String session(String u){String token=secret();s.db().update("INSERT INTO auth_sessions VALUES(?,?,?)",hash(token),u,Timestamp.from(Instant.now().plus(Duration.ofDays(30))));return token;}
 void logout(String token){s.db().update("DELETE FROM auth_sessions WHERE token_hash=?",hash(token));}
 String user(String token){var data=s.rows("SELECT user_id FROM auth_sessions WHERE token_hash=? AND expires_at>?",hash(token),Timestamp.from(Instant.now()));return data.isEmpty()?null:data.get(0).get("user_id").toString();}
 @Transactional public Map<String,Object> exchange(String code,String verifier){var rows=s.rows("SELECT * FROM auth_codes WHERE code_hash=? AND expires_at>? FOR UPDATE",hash(code),Timestamp.from(Instant.now()));if(rows.isEmpty())throw Store.error(401,"로그인 코드가 만료되었어요");var r=rows.get(0);if(!MessageDigest.isEqual(challenge(verifier).getBytes(StandardCharsets.US_ASCII),r.get("challenge").toString().getBytes(StandardCharsets.US_ASCII)))throw Store.error(401,"로그인 검증이 실패했어요");s.db().update("DELETE FROM auth_codes WHERE code_hash=?",hash(code));return Map.of("token",session(r.get("user_id").toString()));}
 @Transactional public String social(String provider,String subject){var rows=s.rows("SELECT user_id FROM social_accounts WHERE provider=? AND subject=?",provider,subject);if(!rows.isEmpty())return rows.get(0).get("user_id").toString();String u=s.createUser("잇픽러");s.db().update("INSERT INTO social_accounts VALUES(?,?,?,?)",Store.id(),u,provider,subject);return u;}
 String code(String u,String c){String code=secret();s.db().update("INSERT INTO auth_codes VALUES(?,?,?,?)",hash(code),u,c,Timestamp.from(Instant.now().plusSeconds(60)));return code;}
}
