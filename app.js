const WD = ['日','月','火','水','木','金','土'];

/* ============ storage (window.storage w/ in-memory fallback) ============ */
const mem = {};
let hasStore = false;
const Store = {
  async init(){ try{ await window.storage.list('journal:'); hasStore=true; }catch(e){ hasStore=false; } },
  async get(k){
    if(hasStore){ try{ const r=await window.storage.get(k); return r?JSON.parse(r.value):null; }catch(e){ return null; } }
    return (k in mem)?mem[k]:null;
  },
  async set(k,v){
    if(hasStore){ try{ await window.storage.set(k, JSON.stringify(v)); return; }catch(e){} }
    mem[k]=v;
  },
  async del(k){
    if(hasStore){ try{ await window.storage.delete(k); return; }catch(e){} }
    delete mem[k];
  },
  async listDays(){
    if(hasStore){
      try{ const r=await window.storage.list('journal:day:'); return (r&&r.keys)?r.keys:[]; }catch(e){ return []; }
    }
    return Object.keys(mem).filter(k=>k.startsWith('journal:day:'));
  }
};
const dayKey = ds => 'journal:day:'+ds;

/* ============ date helpers ============ */
function fmtKey(d){ return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); }
function jpDate(d){ return d.getFullYear()+'年'+(d.getMonth()+1)+'月'+d.getDate()+'日 ('+WD[d.getDay()]+')'; }
function jpDateShort(ds){ const [y,m,day]=ds.split('-').map(Number); const dt=new Date(y,m-1,day); return m+'月'+day+'日 ('+WD[dt.getDay()]+')'; }
const today = new Date();
const todayKey = fmtKey(today);
function dayOfYear(d){ const s=new Date(d.getFullYear(),0,0); return Math.floor((d-s)/86400000); }

/* ============ state ============ */
let murmurDay = todayKey;   // 呟き画面で表示・入力する日付
let calMonth = new Date(today.getFullYear(), today.getMonth(), 1);
let utsuroiPeriod = 'week';
const DEFAULT_PROMPTS = {
  draft: 'あなたはわたし本人です。以下の「今日の呟き」だけを手がかりに、明日のわたしへ宛てた一人称の振り返りを、日本語で3〜5文、穏やかで正直なトーンで書いてください。呟きに無い出来事は創作しないでください。',
  week: 'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、気づきをそっと差し出します。誇張や決めつけはしません。以下の記録をもとに、今週の全体の流れ・前の週と比べた変化・気づいたことを、やさしい日本語で短くまとめてください。記録に無いことは書かないでください。',
  month: 'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、気づきをそっと差し出します。誇張や決めつけはしません。以下の記録をもとに、今月の全体の流れ・前の月と比べた変化・気づいたことを、やさしい日本語で短くまとめてください。記録に無いことは書かないでください。',
  custom: 'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、気づきをそっと差し出します。誇張や決めつけはしません。以下の期間の記録をもとに、全体の流れ・その間の変化・気づいたことを、やさしい日本語で短くまとめてください。記録に無いことは書かないでください。'
};
let settings = {rem:true, remTime:'21:00', promptDraft:DEFAULT_PROMPTS.draft, promptWeek:DEFAULT_PROMPTS.week, promptMonth:DEFAULT_PROMPTS.month, promptCustom:DEFAULT_PROMPTS.custom};

/* ============ murmur day accessors ============ */
async function getDay(ds){ return (await Store.get(dayKey(ds))) || {murmurs:[], reflection:null}; }
async function setDay(ds,data){ await Store.set(dayKey(ds), data); }

function updatePostBtn(){
  const txt=document.getElementById('murmurInput').value.trim();
  document.getElementById('postBtn').disabled = !txt.length;
}

/* ============ murmur day bar ============ */
function murmurDayLabel(){
  if(murmurDay===todayKey) return 'きょう';
  const y=new Date(today); y.setDate(y.getDate()-1);
  if(murmurDay===fmtKey(y)) return 'きのう';
  const [yy,mm,dd]=murmurDay.split('-').map(Number);
  const dt=new Date(yy,mm-1,dd);
  return mm+'月'+dd+'日 ('+WD[dt.getDay()]+')';
}
function setMurmurDay(ds){
  if(ds>todayKey) ds=todayKey;
  murmurDay=ds;
  const lbl=document.getElementById('dayLabel');
  lbl.textContent=murmurDayLabel();
  lbl.classList.toggle('past', murmurDay!==todayKey);
  document.getElementById('dayNext').disabled=(murmurDay===todayKey);
  document.getElementById('dayPicker').value=murmurDay;
  document.getElementById('feedLabel').textContent=(murmurDay===todayKey?'きょう':murmurDayLabel())+'の呟き';
  document.getElementById('murmurInput').placeholder = murmurDay===todayKey ? 'いま、なにを思ってる?' : 'この日のことを、おもいだして。';
  renderFeed();
}
function shiftMurmurDay(n){
  const [y,m,d]=murmurDay.split('-').map(Number);
  const dt=new Date(y,m-1,d); dt.setDate(dt.getDate()+n);
  setMurmurDay(fmtKey(dt));
}

/* ============ render: murmur feed ============ */
async function renderFeed(){
  const day = await getDay(murmurDay);
  const feed = document.getElementById('murmurFeed');
  feed.innerHTML='';
  if(!day.murmurs.length){
    feed.innerHTML = murmurDay===todayKey
      ? '<div class="empty"><span class="big">まだ、しずかです</span>ひとことの呟きから、今日をはじめましょう。</div>'
      : '<div class="empty"><span class="big">この日の呟きはありません</span>おもいだしたことを、あとからでも残せます。</div>';
    return;
  }
  const sorted=[...day.murmurs].sort((a,b)=>b.ts-a.ts);
  sorted.forEach(m=>{
    const el=document.createElement('div');
    el.className='murmur';
    const badge=m.source==='hand'?'<span class="badge-hand">✎ 手書き</span>':'';
    const late=m.late?'<span class="badge-hand">あとから</span>':'';
    const thumb=m.img?`<img class="entry-thumb" src="${m.img}">`:'';
    el.innerHTML=`
      <div class="time">${m.time}</div>
      <div class="track"><span class="dot"></span></div>
      <div class="body">${escapeHtml(m.text)}${badge}${late}${thumb}</div>
      <button class="del" data-id="${m.id}">消す</button>`;
    const timg=el.querySelector('.entry-thumb');
    if(timg) timg.onclick=()=>openImg(m.img);
    el.querySelector('.del').onclick=async()=>{
      const d=await getDay(murmurDay);
      d.murmurs=d.murmurs.filter(x=>x.id!==m.id);
      await setDay(murmurDay,d);
      renderFeed(); refreshMeta();
    };
    feed.appendChild(el);
  });
}
function escapeHtml(s){ return s.replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }

/* ============ post murmur ============ */
async function postMurmur(){
  const inp=document.getElementById('murmurInput');
  const txt=inp.value.trim();
  if(!txt) return;
  const now=new Date();
  const day=await getDay(murmurDay);
  const isToday=murmurDay===todayKey;
  const entry={id:'m'+Date.now(), text:txt, ts:Date.now(), time:isToday?String(now.getHours()).padStart(2,'0')+':'+String(now.getMinutes()).padStart(2,'0'):'✎'};
  if(!isToday){ entry.late=true; entry.ts=new Date(murmurDay+'T12:00:00').getTime(); }
  day.murmurs.push(entry);
  await setDay(murmurDay,day);
  inp.value=''; inp.style.height='auto';
  updatePostBtn();
  renderFeed(); refreshMeta(); renderGathered();
  toast(isToday?'呟きをのこしました':murmurDayLabel()+'に呟きをのこしました');
}

/* ============ reflection ============ */
function updateSaveBtn(){
  const txt=document.getElementById('reflectInput').value.trim();
  document.getElementById('saveReflect').disabled=!txt.length;
}
async function renderGathered(){
  const day=await getDay(todayKey);
  const list=document.getElementById('gList');
  document.getElementById('gCount').textContent=day.murmurs.length+' 件';
  const adb=document.getElementById('aiDraftBtn'); if(adb) adb.disabled=!day.murmurs.length;
  if(!day.murmurs.length){ list.innerHTML='<div class="g-empty">今日はまだ呟きがありません。</div>'; return; }
  list.innerHTML='';
  [...day.murmurs].sort((a,b)=>a.ts-b.ts).forEach(m=>{
    const el=document.createElement('div');
    el.className='g-item';
    el.innerHTML=`<span class="gd"></span><span>${escapeHtml(m.text)}</span>`;
    list.appendChild(el);
  });
}
async function loadReflection(){
  const day=await getDay(todayKey);
  const inp=document.getElementById('reflectInput');
  const note=document.getElementById('savedNote');
  const aiNote=document.getElementById('aiNote'); if(aiNote) aiNote.textContent='';
  if(day.reflection){
    inp.value=day.reflection.text;
    note.textContent='保存済み — いつでも書き直せます';
  } else {
    inp.value=''; note.textContent='';
  }
  updateSaveBtn();
}
async function saveReflection(){
  const txt=document.getElementById('reflectInput').value.trim();
  if(!txt) return;
  const day=await getDay(todayKey);
  day.reflection={text:txt, savedAt:Date.now()};
  await setDay(todayKey,day);
  document.getElementById('savedNote').textContent='保存済み — いつでも書き直せます';
  refreshMeta();
  toast('今日に栞を挟みました');
}

/* ============ ai reflection draft (share bridge) ============ */
async function draftReflection(){
  const day=await getDay(todayKey);
  if(!day.murmurs.length) return;
  const lines=[...day.murmurs].sort((a,b)=>a.ts-b.ts)
    .map(m=>`- ${m.time} ${m.text}`).join('\n');
  openBridge({
    title:'呟きからAI下書き',
    sub:'呟きをAIに送って、下書きを貼り付け',
    prompt:(settings.promptDraft||DEFAULT_PROMPTS.draft),
    contextText:`【今日の呟き】\n${lines}`,
    confirmLabel:'下書きに反映',
    placeholder:'AIが書いた振り返りを貼り付け',
    onResult:(text)=>{
      document.getElementById('reflectInput').value=text;
      updateSaveBtn();
      const note=document.getElementById('aiNote'); if(note) note.textContent='AIの下書きを貼り付けました。直してから保存できます。';
    }
  });
}

/* ============ streak ============ */
async function calcStreak(){
  let n=0; let d=new Date(today);
  for(let i=0;i<400;i++){
    const day=await getDay(fmtKey(d));
    const has=(day.murmurs&&day.murmurs.length)||day.reflection;
    if(has){ n++; d.setDate(d.getDate()-1); }
    else{
      // allow today to be empty without breaking streak
      if(i===0){ d.setDate(d.getDate()-1); continue; }
      break;
    }
  }
  return n;
}
async function refreshMeta(){
  document.getElementById('streakNum').textContent=await calcStreak();
}

/* ============ history calendar ============ */
async function renderCalendar(){
  const y=calMonth.getFullYear(), m=calMonth.getMonth();
  document.getElementById('calTitle').textContent=y+'年 '+(m+1)+'月';
  const grid=document.getElementById('calGrid');
  grid.innerHTML='';
  const first=new Date(y,m,1).getDay();
  const days=new Date(y,m+1,0).getDate();
  for(let i=0;i<first;i++){ const c=document.createElement('div'); c.className='cell blank'; grid.appendChild(c); }
  for(let dnum=1;dnum<=days;dnum++){
    const ds=fmtKey(new Date(y,m,dnum));
    const day=await getDay(ds);
    const count=(day.murmurs?day.murmurs.length:0)+(day.reflection?1:0);
    const lv = count===0?0 : count<=1?1 : count<=3?2 : count<=5?3 : 4;
    const cell=document.createElement('div');
    cell.className='cell'+(count?' has lv'+lv:'')+(ds===todayKey?' today':'');
    if(count) cell.style.background=heatColor(lv);
    cell.innerHTML=`<div class="num">${dnum}</div>`;
    if(count) cell.onclick=()=>openDetail(ds);
    grid.appendChild(cell);
  }
  const leg=document.getElementById('calLegend');
  if(leg && !leg.dataset.built){
    leg.innerHTML='<span>少ない</span><div class="cl-cells">'+[0,1,2,3,4].map(l=>`<i style="background:${l?heatColor(l):'transparent'}"></i>`).join('')+'</div><span>多い</span>';
    leg.dataset.built='1';
  }
}
function heatColor(lv){
  // 記録件数に応じて栞紅を濃く（ヒートマップ風グラデーション）
  return ['transparent','#EDE3E3','#D9BFC1','#B98A8E','#8A4348'][lv];
}

/* ============ detail sheet ============ */
async function openDetail(ds){
  const day=await getDay(ds);
  document.getElementById('detailDate').textContent=jpDateShort(ds);
  document.getElementById('detailSub').textContent=(day.murmurs.length)+' 件の呟き'+(day.reflection?' ・ 振り返りあり':'');
  const body=document.getElementById('detailBody');
  let html='';
  if(day.murmurs.length){
    html+='<div class="sb-section-label">呟き</div>';
    [...day.murmurs].sort((a,b)=>a.ts-b.ts).forEach(m=>{
      const badge=m.source==='hand'?'<span class="badge-hand">✎ 手書き</span>':'';
      const thumb=m.img?`<img class="entry-thumb" src="${m.img}" style="margin-left:17px">`:'';
      html+=`<div class="sb-murmur"><span class="t">${m.time}</span><span class="d"></span><span>${escapeHtml(m.text)}${badge}</span></div>${thumb}`;
    });
  }
  if(day.reflection){
    const rbadge=day.reflection.source==='hand'?'<span class="badge-hand">✎ 手書き</span>':'';
    html+=`<div class="sb-section-label" style="margin-top:22px">振り返り${rbadge}</div>`;
    html+=`<div class="sb-reflect">${escapeHtml(day.reflection.text)}</div>`;
    if(day.reflection.img) html+=`<img class="entry-thumb" src="${day.reflection.img}" style="margin-top:12px">`;
  }
  if(!day.murmurs.length && !day.reflection){ html='<div class="sb-empty">この日の記録はありません。</div>'; }
  body.innerHTML=html;
  body.querySelectorAll('.entry-thumb').forEach(im=>im.onclick=()=>openImg(im.src));
  document.getElementById('overlay').classList.add('show');
  document.getElementById('detailSheet').classList.add('show');
}
function closeSheets(){
  document.getElementById('overlay').classList.remove('show');
  document.getElementById('detailSheet').classList.remove('show');
  document.getElementById('settingsSheet').classList.remove('show');
  document.getElementById('importSheet').classList.remove('show');
}

/* ============ settings ============ */
async function loadSettings(){
  const s=await Store.get('journal:settings');
  if(s) settings=Object.assign({}, settings, s);
  // 旧データにプロンプトが無ければ既定を補う
  if(!settings.promptDraft) settings.promptDraft=DEFAULT_PROMPTS.draft;
  if(!settings.promptWeek) settings.promptWeek=DEFAULT_PROMPTS.week;
  if(!settings.promptMonth) settings.promptMonth=DEFAULT_PROMPTS.month;
  if(!settings.promptCustom) settings.promptCustom=DEFAULT_PROMPTS.custom;
  document.getElementById('remToggle').classList.toggle('on',settings.rem);
  document.getElementById('remTime').value=settings.remTime;
  document.getElementById('remTimeRow').style.opacity=settings.rem?'1':'.4';
  document.getElementById('setPromptDraft').value=settings.promptDraft;
  document.getElementById('setPromptWeek').value=settings.promptWeek;
  document.getElementById('setPromptMonth').value=settings.promptMonth;
  document.getElementById('setPromptCustom').value=settings.promptCustom;
}
async function saveSettings(){ await Store.set('journal:settings',settings); }

/* ============ export ============ */
async function buildExportMd(fromDs,toDs){
  const [fy,fm,fd]=fromDs.split('-').map(Number);
  const [ty,tm,td]=toDs.split('-').map(Number);
  let d=new Date(fy,fm-1,fd); const end=new Date(ty,tm-1,td);
  let out=`# 栞（しおり）エクスポート\n\n期間: ${fromDs} 〜 ${toDs}\n`;
  let has=false;
  while(d<=end){
    const ds=fmtKey(d);
    const day=await getDay(ds);
    if((day.murmurs&&day.murmurs.length)||day.reflection){
      has=true;
      out+=`\n## ${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 (${WD[d.getDay()]})\n`;
      if(day.murmurs&&day.murmurs.length){
        out+=`\n### 呟き\n`;
        [...day.murmurs].sort((a,b)=>a.ts-b.ts).forEach(m=>{
          out+=`- ${m.time} ${m.text}${m.source==='hand'?'（手書き）':''}${m.img?'（画像あり）':''}\n`;
        });
      }
      if(day.reflection){
        out+=`\n### 振り返り${day.reflection.source==='hand'?'（手書き）':''}${day.reflection.img?'（画像あり）':''}\n${day.reflection.text}\n`;
      }
    }
    d.setDate(d.getDate()+1);
  }
  return {text:out, hasContent:has};
}
async function exportRange(){
  let from=document.getElementById('exFrom').value, to=document.getElementById('exTo').value;
  if(!from||!to){ toast('期間を選んでください'); return; }
  if(from>to){ const t=from; from=to; to=t; }
  const res=await buildExportMd(from,to);
  if(!res.hasContent){ toast('この期間に記録がありません'); return; }
  try{
    const blob=new Blob([res.text],{type:'text/markdown;charset=utf-8'});
    const url=URL.createObjectURL(blob);
    const a=document.createElement('a');
    a.href=url; a.download=`shiori_${from}_${to}.md`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
    toast('書き出しました');
  }catch(e){
    try{ await navigator.clipboard.writeText(res.text); toast('保存できない環境のためコピーしました'); }
    catch(e2){ toast('書き出しに失敗しました'); }
  }
}
function openSettings(){
  document.getElementById('overlay').classList.add('show');
  document.getElementById('settingsSheet').classList.add('show');
}

/* ============ toast ============ */
let toastT;
function toast(msg){
  const t=document.getElementById('toast');
  t.textContent=msg; t.classList.add('show');
  clearTimeout(toastT); toastT=setTimeout(()=>t.classList.remove('show'),1900);
}

/* ============ handwriting import ============ */
let impState=null;   // {storeUrl, result, error}
let revType='reflect', revDate=todayKey;

function loadImage(file){
  return new Promise((res,rej)=>{
    const r=new FileReader();
    r.onload=()=>{ const im=new Image(); im.onload=()=>res(im); im.onerror=()=>rej(new Error('decode')); im.src=r.result; };
    r.onerror=()=>rej(new Error('read')); r.readAsDataURL(file);
  });
}
function drawResize(img,max,q){
  const c=document.createElement('canvas');
  let w=img.width,h=img.height;
  const s=Math.min(1,max/Math.max(w,h));
  c.width=Math.round(w*s); c.height=Math.round(h*s);
  c.getContext('2d').drawImage(img,0,0,c.width,c.height);
  return c.toDataURL('image/jpeg',q);
}
async function processImage(file){
  const img=await loadImage(file);
  return { storeUrl:drawResize(img,640,0.72) };
}
function openImportSheet(){
  document.getElementById('overlay').classList.add('show');
  document.getElementById('importSheet').classList.add('show');
}
let bridgeCb=null;
function openBridge(opts){
  bridgeCb=opts.onResult||null;
  document.getElementById('impTitle').textContent=opts.title||'AIに送る';
  document.getElementById('impSub').textContent=opts.sub||'AIアプリに送って、結果を貼り付け';
  const canShare=!!navigator.share;
  const ctx=opts.contextText||'';
  document.getElementById('importBody').innerHTML=`
    <div class="imp-label">AIへのお願い（編集できます）</div>
    <textarea class="imp-textarea" id="bridgePrompt" style="font-family:var(--sans);font-size:14px;min-height:80px">${escapeHtml(opts.prompt||'')}</textarea>
    ${ctx?`<div class="imp-label">いっしょに送る内容</div><div class="bridge-context">${escapeHtml(ctx)}</div>`:''}
    <div class="share-row">
      <button class="r-action" id="bShare">${canShare?'AIアプリに共有':'まとめてコピー'}</button>
      <button class="r-action" id="bCopy">まとめてコピー</button>
    </div>
    <div class="ai-note">共有先のAIアプリ（Claude / ChatGPT / Gemini など）で実行し、返ってきた文章をコピーして戻ってきてください。</div>
    <div class="imp-divider">結果を貼り付け</div>
    <textarea class="imp-textarea" id="bPaste" placeholder="${opts.placeholder||'ここにAIの文章を貼り付け'}"></textarea>
    <button class="imp-confirm" id="bConfirm" disabled>${opts.confirmLabel||'反映する'}</button>`;
  const payload=()=>document.getElementById('bridgePrompt').value + (ctx?('\n\n'+ctx):'');
  document.getElementById('bShare').onclick=async()=>{
    try{ if(navigator.share){ await navigator.share({text:payload()}); } else { await navigator.clipboard.writeText(payload()); toast('まとめてコピーしました'); } }catch(e){}
  };
  document.getElementById('bCopy').onclick=async()=>{
    try{ await navigator.clipboard.writeText(payload()); toast('まとめてコピーしました'); }catch(e){ toast('コピーできませんでした'); }
  };
  const paste=document.getElementById('bPaste'), conf=document.getElementById('bConfirm');
  const upd=()=>conf.disabled=!paste.value.trim(); paste.addEventListener('input',upd); upd();
  conf.onclick=()=>{ const t=paste.value.trim(); if(!t) return; const cb=bridgeCb; bridgeCb=null; closeSheets(); if(cb) cb(t); };
  openImportSheet();
}
function insightHTML(text){
  return `<div class="u-review"><div class="u-insight">${escapeHtml(text)}</div><button class="u-regen" id="uRegen">AIに送り直す</button></div>`;
}
function defaultPromptFor(type){
  return type==='murmur'
    ? '添付の手書きメモを、書かれている日本語のとおりに文字起こししてください。結果の本文だけを返してください。'
    : '添付の手書きの日記を文字起こしし、内容を数文でやさしく要約してください。結果の本文だけを返してください。';
}
function renderShareImport(){
  revType=impState.defaultType || 'murmur';
  revDate=(impState.defaultType==='murmur') ? murmurDay : todayKey;
  document.getElementById('impTitle').textContent='画像から取り込む';
  document.getElementById('impSub').textContent='AIアプリに共有 → 結果を貼り付け';
  const body=document.getElementById('importBody');
  const canShare = !!(navigator.share);
  body.innerHTML=`
    ${impState.storeUrl?`<img class="imp-preview" src="${impState.storeUrl}">`:''}
    <div class="imp-label">AIへのお願い（編集できます）</div>
    <textarea class="imp-textarea" id="sharePrompt" style="min-height:76px;font-family:var(--sans);font-size:14px">${escapeHtml(defaultPromptFor(revType))}</textarea>
    <div class="share-row">
      <button class="r-action" id="shareBtn">${canShare?'AIアプリに共有':'お願いをコピー'}</button>
      <button class="r-action" id="copyPromptBtn">お願いをコピー</button>
    </div>
    <div class="ai-note">「共有」を押すとお願いも自動でコピーされます。AIアプリで画像に添えて（必要なら貼り付けて）実行し、返ってきた文章をコピーして戻ってきてください。</div>

    <div class="imp-divider">結果を貼り付け</div>
    <div class="imp-label">しゅるい</div>
    <div class="type-seg" id="revType">
      <button data-t="murmur">呟き</button>
      <button data-t="reflect">振り返り</button>
    </div>
    <div class="imp-label">AIの結果</div>
    <textarea class="imp-textarea" id="pasteText" placeholder="ここにAIの文章を貼り付け"></textarea>
    <div class="imp-label">この記録の日付</div>
    <input type="date" class="imp-date" id="revDate" value="${revDate}" max="${todayKey}">
    <button class="imp-confirm" id="impConfirm" disabled>取り込む</button>`;

  const typeSeg=document.getElementById('revType');
  const syncType=()=>[...typeSeg.children].forEach(b=>b.classList.toggle('sel',b.dataset.t===revType));
  [...typeSeg.children].forEach(b=>b.onclick=()=>{ revType=b.dataset.t; syncType(); });
  syncType();

  document.getElementById('revDate').onchange=e=>{ revDate=e.target.value||todayKey; };

  document.getElementById('shareBtn').onclick=doShare;
  document.getElementById('copyPromptBtn').onclick=copyPrompt;

  const paste=document.getElementById('pasteText');
  const conf=document.getElementById('impConfirm');
  const upd=()=>conf.disabled=!paste.value.trim();
  paste.addEventListener('input',upd); upd();
  conf.onclick=confirmImport;
}
async function doShare(){
  const prompt=document.getElementById('sharePrompt').value;
  // お願いは常にクリップボードにも入れておく（共有時にテキストが落ちても貼れるように）
  try{ await navigator.clipboard.writeText(prompt); }catch(e){}
  try{
    if(navigator.canShare && impState.file && navigator.canShare({files:[impState.file]})){
      await navigator.share({files:[impState.file], text:prompt});
      toast('お願いはコピー済み。AIアプリで画像に添えて貼り付けを');
    } else if(navigator.share){
      await navigator.share({text:prompt});
      toast('画像はAIアプリで手動添付してください');
    } else {
      toast('この環境は共有非対応。お願いをコピーしました');
    }
  }catch(e){ /* ユーザーがキャンセル */ }
}
async function copyPrompt(){
  try{ await navigator.clipboard.writeText(document.getElementById('sharePrompt').value); toast('お願いをコピーしました'); }
  catch(e){ toast('コピーできませんでした'); }
}
async function confirmImport(){
  const text=document.getElementById('pasteText').value.trim();
  if(!text) return;
  const ds=revDate;
  const day=await getDay(ds);
  if(revType==='murmur'){
    day.murmurs.push({id:'h'+Date.now(),text,ts:new Date(ds+'T12:00:00').getTime(),time:'✎',img:impState.storeUrl||null,source:'hand'});
  }else{
    day.reflection={text,savedAt:Date.now(),img:impState.storeUrl||null,source:'hand'};
  }
  await setDay(ds,day);
  closeSheets(); impState=null;
  await renderFeed(); await refreshMeta();
  if(document.getElementById('screen-history').classList.contains('active')) renderCalendar();
  if(document.getElementById('screen-reflect').classList.contains('active')){ renderGathered(); loadReflection(); }
  toast('取り込みました');
}
async function startImport(file, defaultType){
  impState={storeUrl:null, file:file, defaultType:(defaultType||'murmur')};
  openImportSheet();
  document.getElementById('importBody').innerHTML=`<div class="imp-loading"><div class="spinner"></div><div class="lt">画像を読み込んでいます…</div></div>`;
  try{
    const {storeUrl}=await processImage(file);
    impState.storeUrl=storeUrl;
    renderShareImport();
  }catch(err){
    impState.storeUrl=null;
    renderShareImport();
  }
}

/* ============ image viewer ============ */
function openImg(src){
  document.getElementById('imgFull').src=src;
  document.getElementById('imgViewer').classList.add('show');
}
function closeImg(){ document.getElementById('imgViewer').classList.remove('show'); }

/* ============ utsuroi (insights) ============ */
async function gatherPeriod(days, offset){
  offset=offset||0;
  const arr=[];
  for(let i=days-1;i>=0;i--){
    const d=new Date(today); d.setDate(d.getDate()-i-offset);
    arr.push({ds:fmtKey(d), date:new Date(d), day:await getDay(fmtKey(d))});
  }
  return arr;
}
async function gatherRange(fromDs,toDs){
  const [fy,fm,fd]=fromDs.split('-').map(Number);
  const [ty,tm,td]=toDs.split('-').map(Number);
  let d=new Date(fy,fm-1,fd); const end=new Date(ty,tm-1,td);
  const arr=[];
  while(d<=end){
    arr.push({ds:fmtKey(d), date:new Date(d), day:await getDay(fmtKey(d))});
    d.setDate(d.getDate()+1);
  }
  return arr;
}
function customRange(){
  let from=document.getElementById('uFrom').value, to=document.getElementById('uTo').value;
  if(!from||!to) return null;
  if(from>to){ const t=from; from=to; to=t; }
  return {from,to};
}
function digestArr(arr){
  return arr.map(o=>{
    const day=o.day; if(!day.murmurs.length && !day.reflection) return null;
    const mur=day.murmurs.map(m=>m.text).join(' / ');
    const ref=day.reflection?day.reflection.text:'';
    let s=`${o.date.getMonth()+1}/${o.date.getDate()}(${WD[o.date.getDay()]})`;
    if(mur) s+=` 呟き:${mur}`;
    if(ref) s+=` 振り返り:${ref}`;
    return s;
  }).filter(Boolean).join('\n');
}
function cacheKey(){
  if(utsuroiPeriod==='custom'){
    const r=customRange();
    return r?`journal:insight:custom:${r.from}:${r.to}`:null;
  }
  return `journal:insight:${utsuroiPeriod}:${todayKey}`;
}
function periodLabel(){
  if(utsuroiPeriod==='week') return '今週';
  if(utsuroiPeriod==='month') return '今月';
  const r=customRange();
  return r?`${r.from.replaceAll('-','/')} 〜 ${r.to.replaceAll('-','/')}`:'この期間';
}
function genButtonHTML(){
  const lbl=(utsuroiPeriod==='custom'?'この期間':periodLabel())+'をAIに読み解いてもらう';
  return `<button class="u-gen" id="uGen">
    <svg class="sp" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/></svg>
    <span>${lbl}</span></button>
    <div class="ai-note" style="margin-top:12px;text-align:center">記録をAIアプリに送り、返ってきた読み解きを貼り付けて保存します。</div>`;
}
async function generateUtsuroi(){
  let arrNow, ctxPrev='', label=periodLabel();
  if(utsuroiPeriod==='custom'){
    const r=customRange();
    if(!r){ toast('期間を選んでください'); return; }
    arrNow=await gatherRange(r.from,r.to);
  }else{
    const days=utsuroiPeriod==='week'?7:30;
    arrNow=await gatherPeriod(days,0);
    const prev=await gatherPeriod(days,days);
    const dp=digestArr(prev);
    const plabel=utsuroiPeriod==='week'?'1週間':'1か月';
    ctxPrev=`\n\n【その前の${plabel}】\n${dp||'（記録なし）'}`;
  }
  const dn=digestArr(arrNow);
  if(!dn.trim()){ toast('この期間に記録がありません'); return; }
  const promptText = utsuroiPeriod==='week' ? (settings.promptWeek||DEFAULT_PROMPTS.week)
                   : utsuroiPeriod==='month' ? (settings.promptMonth||DEFAULT_PROMPTS.month)
                   : (settings.promptCustom||DEFAULT_PROMPTS.custom);
  openBridge({
    title:label+'の読み解き',
    sub:'記録をAIに送って、読み解きを貼り付け',
    prompt:promptText,
    contextText:`【${label}の記録】\n${dn}${ctxPrev}`,
    confirmLabel:'読み解きを保存',
    placeholder:'AIの読み解きを貼り付け',
    onResult:async(text)=>{
      const k=cacheKey();
      if(k) await Store.set(k, {text, at:Date.now()});
      renderUtsuroi();
    }
  });
}
async function renderUtsuroi(){
  const seg=document.getElementById('uSeg');
  [...seg.children].forEach(b=>b.classList.toggle('sel',b.dataset.p===utsuroiPeriod));
  document.getElementById('uRange').style.display = utsuroiPeriod==='custom' ? 'flex' : 'none';
  const area=document.getElementById('uReviewArea');
  let arr;
  if(utsuroiPeriod==='custom'){
    const r=customRange();
    if(!r){ area.innerHTML='<div class="u-empty"><span class="big">期間を選んでください</span>開始日と終了日を選ぶと、読み解けます。</div>'; return; }
    arr=await gatherRange(r.from,r.to);
  }else{
    arr=await gatherPeriod(utsuroiPeriod==='week'?7:30,0);
  }
  const hasData=arr.some(o=>o.day.murmurs.length||o.day.reflection);
  if(!hasData){
    area.innerHTML='<div class="u-empty"><span class="big">この期間に記録がありません</span>呟きや振り返りがたまると、ここでAIに読み解いてもらえます。</div>';
    return;
  }
  const k=cacheKey();
  const cached=k?await Store.get(k):null;
  if(cached && cached.text!==undefined){
    area.innerHTML=insightHTML(cached.text);
    const rg=document.getElementById('uRegen'); if(rg) rg.onclick=generateUtsuroi;
  }
  else { area.innerHTML=genButtonHTML(); document.getElementById('uGen').onclick=generateUtsuroi; }
}

/* ============ navigation ============ */
const titles={murmur:'呟き',reflect:'振り返り',history:'履歴',utsuroi:'うつろい'};
function switchScreen(name){
  document.querySelectorAll('.screen').forEach(s=>s.classList.remove('active'));
  document.getElementById('screen-'+name).classList.add('active');
  document.querySelectorAll('.nav button').forEach(b=>b.classList.toggle('active',b.dataset.screen===name));
  document.getElementById('screenTitle').textContent=titles[name];
  if(name==='reflect'){ renderGathered(); loadReflection(); }
  if(name==='history'){ renderCalendar(); }
  if(name==='utsuroi'){ renderUtsuroi(); }
  document.querySelector('.screen.active').scrollTop=0;
}

/* ============ sample seed ============ */
async function seedIfEmpty(){
  const keys=await Store.listDays();
  if(keys.length>0) return;
  const mTexts=[
    '朝のコーヒーがちょうどいい温度だった','窓の外の雲をぼんやり眺めてた','帰り道、金木犀の匂いがした',
    '少し疲れた。早めに休もう','友だちからの連絡がうれしかった','本を10ページ読めた','何もしない時間も、悪くない',
    '夕焼けがとてもきれいだった','昼にちゃんと歩けた','締め切りが近い、でも大丈夫','あたたかいスープを飲んだ',
    'ひとつ、やり残しを片づけた','雨の音が心地よかった','会議が長かった','小さな失敗、でも笑えた',
    '今日の空はうすい水色','ねこが膝にのってきた','考えごとで手が止まった'
  ];
  const rTexts=[
    '急がなくていい日だった。','うまくいかないこともあったけど、まあいい。','小さな幸せに、いくつか気づけた。',
    '静かに過ごせた。悪くない一日。','疲れたけれど、前に進んだ気がする。','人のやさしさに助けられた日。',
    'なんでもない日を、大切にしたい。'
  ];
  let seed=7;
  const rnd=()=>{ seed=(seed*9301+49297)%233280; return seed/233280; };
  for(let i=1;i<=20;i++){
    const d=new Date(today); d.setDate(d.getDate()-i);
    const ds=fmtKey(d);
    const nM=1+Math.floor(rnd()*3);
    const murmurs=[];
    for(let j=0;j<nM;j++){
      const hh=8+Math.floor(rnd()*13);
      const mm=Math.floor(rnd()*60);
      const t=new Date(d); t.setHours(hh,mm,0,0);
      murmurs.push({id:'s'+i+'_'+j, text:mTexts[Math.floor(rnd()*mTexts.length)], ts:t.getTime(), time:String(hh).padStart(2,'0')+':'+String(mm).padStart(2,'0')});
    }
    murmurs.sort((a,b)=>a.ts-b.ts);
    let reflection=null;
    if(rnd()<0.65){ reflection={text:rTexts[Math.floor(rnd()*rTexts.length)], savedAt:d.getTime()}; }
    await setDay(ds,{murmurs,reflection});
  }
}

/* ============ init ============ */
async function init(){
  await Store.init();
  await seedIfEmpty();
  document.getElementById('todayDate').textContent=jpDate(today);
  await loadSettings();
  await renderFeed();
  await refreshMeta();

  // compose input
  const inp=document.getElementById('murmurInput');
  inp.addEventListener('input',()=>{ inp.style.height='auto'; inp.style.height=Math.min(inp.scrollHeight,160)+'px'; updatePostBtn(); });
  document.getElementById('postBtn').onclick=postMurmur;

  // day bar (past-day murmurs)
  document.getElementById('dayPrev').onclick=()=>shiftMurmurDay(-1);
  document.getElementById('dayNext').onclick=()=>shiftMurmurDay(1);
  const picker=document.getElementById('dayPicker');
  picker.max=todayKey;
  document.getElementById('dayLabel').onclick=()=>{ picker.style.pointerEvents='auto'; picker.showPicker?picker.showPicker():picker.click(); };
  picker.onchange=e=>{ picker.style.pointerEvents='none'; if(e.target.value) setMurmurDay(e.target.value); };
  setMurmurDay(todayKey);

  // reflect
  document.getElementById('reflectInput').addEventListener('input',updateSaveBtn);
  document.getElementById('saveReflect').onclick=saveReflection;
  document.getElementById('aiDraftBtn').onclick=draftReflection;

  // nav
  document.querySelectorAll('.nav button').forEach(b=>b.onclick=()=>switchScreen(b.dataset.screen));

  // calendar nav
  document.getElementById('prevMonth').onclick=()=>{ calMonth.setMonth(calMonth.getMonth()-1); renderCalendar(); };
  document.getElementById('nextMonth').onclick=()=>{ calMonth.setMonth(calMonth.getMonth()+1); renderCalendar(); };

  // utsuroi period toggle
  [...document.getElementById('uSeg').children].forEach(b=>b.onclick=()=>{ utsuroiPeriod=b.dataset.p; renderUtsuroi(); });
  const uAgo=new Date(today); uAgo.setDate(uAgo.getDate()-6);
  document.getElementById('uFrom').value=fmtKey(uAgo);
  document.getElementById('uTo').value=todayKey;
  document.getElementById('uFrom').max=todayKey;
  document.getElementById('uTo').max=todayKey;
  document.getElementById('uFrom').onchange=renderUtsuroi;
  document.getElementById('uTo').onchange=renderUtsuroi;

  // sheets
  document.getElementById('overlay').onclick=closeSheets;
  document.getElementById('openSettings').onclick=openSettings;

  // import (murmur / reflection)
  const fileInput=document.getElementById('importFile');
  let pendingType='murmur';
  document.getElementById('importBtn').onclick=()=>{ pendingType='murmur'; fileInput.click(); };
  document.getElementById('reflectImportBtn').onclick=()=>{ pendingType='reflect'; fileInput.click(); };
  fileInput.onchange=e=>{ const f=e.target.files[0]; e.target.value=''; if(f) startImport(f,pendingType); };

  // image viewer
  document.getElementById('imgViewer').onclick=closeImg;

  // settings interactions
  document.getElementById('remToggle').onclick=async()=>{
    settings.rem=!settings.rem;
    document.getElementById('remToggle').classList.toggle('on',settings.rem);
    document.getElementById('remTimeRow').style.opacity=settings.rem?'1':'.4';
    await saveSettings();
  };
  document.getElementById('remTime').onchange=async(e)=>{ settings.remTime=e.target.value; await saveSettings(); toast('リマインドを '+settings.remTime+' に設定'); };

  // プロンプト編集（変更確定時に保存）
  const bindPrompt=(id,key)=>{
    const el=document.getElementById(id);
    el.addEventListener('change', async()=>{ settings[key]=el.value.trim()||DEFAULT_PROMPTS[key.replace('prompt','').toLowerCase()]; el.value=settings[key]; await saveSettings(); toast('プロンプトを保存しました'); });
  };
  bindPrompt('setPromptDraft','promptDraft');
  bindPrompt('setPromptWeek','promptWeek');
  bindPrompt('setPromptMonth','promptMonth');
  bindPrompt('setPromptCustom','promptCustom');
  document.getElementById('resetPrompts').onclick=async()=>{
    settings.promptDraft=DEFAULT_PROMPTS.draft;
    settings.promptWeek=DEFAULT_PROMPTS.week;
    settings.promptMonth=DEFAULT_PROMPTS.month;
    settings.promptCustom=DEFAULT_PROMPTS.custom;
    document.getElementById('setPromptDraft').value=settings.promptDraft;
    document.getElementById('setPromptWeek').value=settings.promptWeek;
    document.getElementById('setPromptMonth').value=settings.promptMonth;
    document.getElementById('setPromptCustom').value=settings.promptCustom;
    await saveSettings(); toast('プロンプトを既定に戻しました');
  };

  document.getElementById('resetData').onclick=async()=>{
    const keys=await Store.listDays();
    for(const k of keys){ await Store.del(k); }
    await renderFeed(); await refreshMeta(); closeSheets();
    if(document.getElementById('screen-history').classList.contains('active')) renderCalendar();
    toast('まっさらにしました');
  };

  // export
  const ago=new Date(today); ago.setDate(ago.getDate()-29);
  document.getElementById('exFrom').value=fmtKey(ago);
  document.getElementById('exTo').value=todayKey;
  document.getElementById('exFrom').max=todayKey;
  document.getElementById('exTo').max=todayKey;
  document.getElementById('exportBtn').onclick=exportRange;
}
init();
