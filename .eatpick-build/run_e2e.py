from pathlib import Path
import subprocess,os,time,urllib.request,sys
sys.stdout.reconfigure(encoding="utf-8")
b=Path(__file__).parent;p=b/'eatpick'
(p/'docs').mkdir(exist_ok=True)
java='C:/Program Files/Java/jdk-17/bin/java.exe'
env=os.environ.copy();env['SPRING_PROFILES_ACTIVE']='demo'
a=open(b/'e2e-api.log','wb');w=open(b/'e2e-web.log','wb')
api=subprocess.Popen([java,'-jar',str(p/'backend/target/eatpick-api-1.0.0.jar'),'--spring.datasource.url=jdbc:h2:mem:e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1','--app.cors-origins=http://127.0.0.1:8081'],cwd=b,env=env,stdout=a,stderr=a,creationflags=0x08000000)
web=subprocess.Popen(['python','-m','http.server','8081','--bind','127.0.0.1','--directory',str(p/'frontend/dist')],stdout=w,stderr=w,creationflags=0x08000000)
try:
 for i in range(30):
  try:
   urllib.request.urlopen('http://127.0.0.1:8080/health',timeout=1);break
  except:time.sleep(1)
 else:raise RuntimeError('Backend not ready')
 r=subprocess.run(['node',str(b/'e2e.cjs')],cwd=b,timeout=100,capture_output=True)
 (b/'e2e-result.txt').write_bytes(r.stdout+r.stderr)
 print(r.stdout.decode('utf-8',errors='replace'));print(r.stderr.decode('utf-8',errors='replace'))
 if r.returncode:raise RuntimeError('E2E failed')
finally:
 for proc in [web,api]:
  proc.terminate()
  try:proc.wait(timeout=5)
  except:proc.kill()
 a.close();w.close()
