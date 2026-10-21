from pathlib import Path
import subprocess,time,os,urllib.request,json,hashlib
b=Path(__file__).parent;p=b/'eatpick';d=b/'mysql-test-2';d.mkdir(exist_ok=True)
mysqlbin=Path('C:/Program Files/MySQL/MySQL Server 8.0/bin');java='C:/Program Files/Java/jdk-17/bin/java.exe'
log=open(b/'mysql-test.log','wb');apilog=open(b/'mysql-api.log','wb')
subprocess.run([str(mysqlbin/'mysqld.exe'),'--no-defaults','--initialize-insecure','--basedir='+str(mysqlbin.parent),'--datadir='+str(d)],stdout=log,stderr=log,check=True,timeout=30)
server=subprocess.Popen([str(mysqlbin/'mysqld.exe'),'--no-defaults','--basedir='+str(mysqlbin.parent),'--datadir='+str(d),'--bind-address=127.0.0.1','--port=33079','--mysqlx=OFF'],stdout=log,stderr=log,creationflags=0x08000000)
cli=[str(mysqlbin/'mysql.exe'),'--no-defaults','-h127.0.0.1','-P33079','-uroot','--connect-timeout=2','--default-character-set=utf8mb4']
api=None
try:
 for i in range(15):
  time.sleep(1);r=subprocess.run(cli,input=b'SELECT 1;',capture_output=True,timeout=4)
  if r.returncode==0:break
 else:raise RuntimeError(r.stderr.decode())
 r=subprocess.run(cli,input=(p/'database/schema-mysql.sql').read_bytes(),capture_output=True,timeout=10)
 if r.returncode:raise RuntimeError(r.stderr.decode())
 token='mysql-test-token';h=hashlib.sha256(token.encode()).hexdigest()
 sql=f"USE eatpick; INSERT INTO users(id,nickname) VALUES('test-user','MySQL test'); INSERT INTO nutrition_goals VALUES('goal','test-user',CURRENT_DATE,2300,320,100,65,50,2000,25); INSERT INTO auth_sessions VALUES('{h}','test-user','2030-01-01 00:00:00');"
 r=subprocess.run(cli,input=sql.encode(),capture_output=True,timeout=10)
 if r.returncode:raise RuntimeError(r.stderr.decode())
 env=os.environ.copy();env.update(SPRING_PROFILES_ACTIVE='mysql',DB_USER='root',DB_PASSWORD='',DB_URL='jdbc:mysql://127.0.0.1:33079/eatpick?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8',PORT='8082')
 api=subprocess.Popen([java,'-jar',str(p/'backend/target/eatpick-api-1.0.0.jar')],cwd=b,env=env,stdout=apilog,stderr=apilog,creationflags=0x08000000)
 for i in range(30):
  try:urllib.request.urlopen('http://127.0.0.1:8082/health',timeout=1);break
  except:time.sleep(1)
 else:raise RuntimeError('MySQL API failed startup')
 def req(path,method='GET',body=None):
  data=None if body is None else json.dumps(body).encode()
  return json.load(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8082'+path,data=data,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'},method=method),timeout=5))
 assert req('/api/me')['aiBackend']=='self_hosted'
 req('/api/meals','POST',{'foodCode':'chicken','servings':1.5,'slot':'dinner'})
 assert req('/api/home')['total']['protein']==34.5
 assert len(req('/api/foods'))==14
 print('PASS: MySQL 8.0.37 schema + Spring Boot JDBC startup, session auth, 14 foods, meal save and nutrition total')
finally:
 if api:api.terminate();api.wait(timeout=10)
 subprocess.run(cli,input=b'SHUTDOWN;',capture_output=True,timeout=4)
 try:server.wait(timeout=10)
 except:server.terminate()
 log.close();apilog.close()
