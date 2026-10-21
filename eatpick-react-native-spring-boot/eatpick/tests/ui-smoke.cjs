const {chromium}=require('playwright');
const path=require('path');
(async()=>{
 const browser=await chromium.launch({...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{}),headless:true});
 const page=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')console.log('CONSOLE',m.text())});page.on('requestfailed',r=>console.log('FAILED',r.url(),r.failure()));
 try{
  await page.goto(process.env.WEB_URL||'http://localhost:8081');
  await page.getByText('로그인 없이 시연 시작',{exact:true}).click();
  await page.getByText('오늘 먹은 것',{exact:true}).waitFor({timeout:12000});
  await page.waitForTimeout(800);
  await page.screenshot({path:path.join(__dirname,'../docs/home-preview.png')});
  await page.getByRole('tab',{name:'기록',exact:true}).click();
  await page.getByText('김치찌개',{exact:true}).first().click();
  await page.getByText('점심으로 기록하기',{exact:true}).waitFor();
  await page.getByText('점심으로 기록하기',{exact:true}).click();
  await page.getByText('오늘 먹은 것',{exact:true}).waitFor({timeout:12000});
  await page.getByLabel('알림 닫기').click();
  await page.getByRole('tab',{name:'추천',exact:true}).click();
  await page.getByText('장보기에 담기',{exact:true}).first().click();
  await page.waitForTimeout(600);
  await page.screenshot({path:path.join(__dirname,'../docs/shop-preview.png')});
  await page.getByText('무료 플랜',{exact:true}).first().click();
  await page.getByText('시연: 프리미엄 시작하기',{exact:true}).click();
  await page.getByText('✦ 프리미엄',{exact:true}).waitFor();
  await page.getByRole('tab',{name:'분석',exact:true}).click();
  await page.getByText('기록일 평균 · 7일 기록',{exact:true}).waitFor();
  await page.waitForTimeout(800);
  await page.screenshot({path:path.join(__dirname,'../docs/report-preview.png')});
  console.log(JSON.stringify({result:'PASS',flows:['demo login','home','sample recognition','meal save','shopping add','premium switch','report'],pageErrors:errors}));
  if(errors.length)process.exitCode=1;
 }catch(e){console.log('BODY',await page.locator('body').innerText());console.log('ERRORS',errors);await page.screenshot({path:path.join(__dirname,'e2e-failed.png')});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
