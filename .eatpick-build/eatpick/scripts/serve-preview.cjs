const http=require('http');const fs=require('fs');const path=require('path');
const root=path.resolve(__dirname,'../preview-web');
const mime={'.html':'text/html; charset=utf-8','.js':'application/javascript; charset=utf-8','.json':'application/json','.png':'image/png','.ttf':'font/ttf','.woff2':'font/woff2','.ico':'image/x-icon'};
http.createServer((req,res)=>{
 if(req.method!=='GET'&&req.method!=='HEAD'){res.writeHead(405);return res.end();}
 let target;try{const url=new URL(req.url,'http://localhost');target=path.resolve(root,'.'+decodeURIComponent(url.pathname));}catch{res.writeHead(400);return res.end();}
 if(target!==root&&!target.startsWith(root+path.sep)){res.writeHead(403);return res.end();}
 if(target===root)target=path.join(root,'index.html');
 fs.stat(target,(err,stat)=>{if(err||!stat.isFile()){res.writeHead(404);return res.end('Not found');}res.writeHead(200,{'Content-Type':mime[path.extname(target)]||'application/octet-stream','Content-Length':stat.size});if(req.method==='HEAD')res.end();else fs.createReadStream(target).pipe(res);});
}).listen(8081,'127.0.0.1',()=>console.log('Eatpick preview: http://localhost:8081 (start backend first)'));
