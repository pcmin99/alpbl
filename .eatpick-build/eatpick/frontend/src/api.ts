import {Platform} from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
export const API_URL=(process.env.EXPO_PUBLIC_API_URL || (Platform.OS==='android'?'http://10.0.2.2:8080':'http://localhost:8080')).replace(/\/$/,'');
export const DEMO=process.env.EXPO_PUBLIC_DEMO_MODE!=='false';
let token:string|null=null;
export async function loadToken(){token=Platform.OS==='web'?sessionStorage.getItem('eatpick-token'):await SecureStore.getItemAsync('eatpick-token');return token;}
export async function setToken(value:string|null){token=value;if(Platform.OS==='web'){value?sessionStorage.setItem('eatpick-token',value):sessionStorage.removeItem('eatpick-token');}else{value?await SecureStore.setItemAsync('eatpick-token',value):await SecureStore.deleteItemAsync('eatpick-token');}}
export class ApiError extends Error {constructor(public status:number,message:string){super(message);}}
export async function api<T>(path:string,options:RequestInit={}):Promise<T>{
 const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),60000);
 try {const res=await fetch(API_URL+path,{...options,signal:controller.signal,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`} : {}),...options.headers}});const data=await res.json().catch(()=>({}));if(!res.ok){if(res.status===401)await setToken(null);throw new ApiError(res.status,data.message||'요청을 처리하지 못했어요');}return data as T;}catch(e){if(e instanceof ApiError)throw e;throw new Error('서버에 연결할 수 없어요. API 주소와 네트워크를 확인해주세요.');}finally{clearTimeout(timer);}
}
export function post<T>(path:string,body:unknown){return api<T>(path,{method:'POST',body:JSON.stringify(body)});}
export function requestKey(){return Crypto.randomUUID();}
