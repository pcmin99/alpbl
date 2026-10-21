export const C={bg:'#EEF1EB',surface:'#FFFFFF',ink:'#16261C',muted:'#5F6C64',line:'#D5DCD3',brand:'#14774F',tint:'#E2F0E8',yellow:'#F5B92E',yellowTint:'#FFF4D8',danger:'#B4372A'};
export const slots=[['breakfast','아침'],['lunch','점심'],['dinner','저녁'],['snack','간식']] as const;
export const nutrients=[['carb','탄수화물','g'],['sugar','당류','g'],['protein','단백질','g'],['fat','지방','g'],['sodium','나트륨','mg'],['fiber','식이섬유','g']] as const;
export const fmt=(n:number|undefined)=>Number(n||0).toLocaleString('ko-KR',{maximumFractionDigits:1});
