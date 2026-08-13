const WD = ['日','月','火','水','木','金','土'];

/* ============ storage (IndexedDB) ============
   保存の正はここ一つ。キー体系は不変（journal:day:* / journal:settings /
   journal:insight:*）。値はオブジェクトのまま structured clone で保存する
   ので、画像(base64)を含む記録もそのまま入る。呼び出し側は get/set/del/
   listDays の4メソッドだけを使い、Step 1 以前から変更していない。 */
const DB_NAME = 'shiori';
const DB_VERSION = 1;
const STORE = 'kv';
let _db = null;

function openDB(){
  return new Promise((resolve, reject)=>{
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = ()=>{
      const db = req.result;
      if(!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
    };
    req.onsuccess = ()=>resolve(req.result);
    req.onerror = ()=>reject(req.error);
  });
}
function idbReq(mode, fn){
  return new Promise((resolve, reject)=>{
    const tx = _db.transaction(STORE, mode);
    const os = tx.objectStore(STORE);
    let out;
    const r = fn(os);
    if(r) r.onsuccess = ()=>{ out = r.result; };
    tx.oncomplete = ()=>resolve(out);
    tx.onerror = ()=>reject(tx.error);
    tx.onabort = ()=>reject(tx.error);
  });
}

const Store = {
  async init(){
    _db = await openDB();
    // 永続化を要求（自動削除の対象外にしてもらう）。結果はログに残す。
    try{
      if(navigator.storage && navigator.storage.persist){
        const already = await navigator.storage.persisted();
        const granted = already ? true : await navigator.storage.persist();
        console.log('[shiori] storage.persisted():', already, '-> persist granted:', granted);
        if(navigator.storage.estimate){
          const est = await navigator.storage.estimate();
          console.log('[shiori] storage.estimate():', est);
        }
      } else {
        console.log('[shiori] StorageManager API 非対応（永続化は要求できません）');
      }
    }catch(e){ console.warn('[shiori] persist request failed:', e); }
  },
  async get(k){
    const v = await idbReq('readonly', os=>os.get(k));
    return v===undefined ? null : v;
  },
  async set(k,v){
    await idbReq('readwrite', os=>os.put(v, k));
  },
  async del(k){
    await idbReq('readwrite', os=>os.delete(k));
  },
  async listDays(){
    const keys = await idbReq('readonly', os=>os.getAllKeys());
    return (keys||[]).filter(k=>typeof k==='string' && k.startsWith('journal:day:'));
  },
  async listAll(){
    const keys = await idbReq('readonly', os=>os.getAllKeys());
    return (keys||[]).filter(k=>typeof k==='string');
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
let detailDay = null;       // 履歴の詳細シートで表示中の日付
let utsuroiPeriod = 'week';
let utsuroiAngle = 'base';   // うつろいで選んだ読み解きの角度（'base'=期間の既定プロンプト）
const DEFAULT_PROMPTS = {
  draft: 'あなたはわたし本人です。以下の「今日の呟き」だけを手がかりに、明日のわたしへ宛てた一人称の振り返りを、日本語で3〜5文、穏やかで正直なトーンで書いてください。呟きに無い出来事は創作しないでください。',
  week: 'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、気づきをそっと差し出します。誇張や決めつけはしません。以下の記録をもとに、今週の全体の流れ・前の週と比べた変化・気づいたことを、やさしい日本語で短くまとめてください。記録に無いことは書かないでください。',
  month: 'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、気づきをそっと差し出します。誇張や決めつけはしません。以下の記録をもとに、今月の全体の流れ・前の月と比べた変化・気づいたことを、やさしい日本語で短くまとめてください。記録に無いことは書かないでください。',
  custom: 'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、気づきをそっと差し出します。誇張や決めつけはしません。以下の期間の記録をもとに、全体の流れ・その間の変化・気づいたことを、やさしい日本語で短くまとめてください。記録に無いことは書かないでください。'
};
// うつろいで選べる「読み解きの角度」の既定プロンプト集。設定で追加・編集・削除できる。
const DEFAULT_ANGLES = [
  {id:'d1', name:'感情の波', text:'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。評価や説教はせず、誇張や決めつけもしません。以下の記録から、わたしの気持ちが動いた瞬間（うれしさ・焦り・安らぎ・苛立ちなど）を拾い、「何がきっかけで、どう動いたか」をやさしい日本語で短くまとめてください。記録に無いことは書かないでください。'},
  {id:'d2', name:'大切にしているもの', text:'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。以下の記録に繰り返し現れる言葉・話題・場面から、わたしが大切にしていそうなもの（価値観）を2〜3つ、根拠となる記録の言葉を添えて、やさしく差し出してください。断定はせず「〜を大切にしているのかもしれません」の距離感で。記録に無いことは書かないでください。'},
  {id:'d3', name:'エネルギーの出入り', text:'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。以下の記録から、わたしに力をくれたらしいもの（人・場所・行動）と、力をすり減らしたらしいものを、それぞれ記録の言葉を引きながら短く整理してください。助言はせず、並べて見せるだけにしてください。記録に無いことは書かないでください。'},
  {id:'d4', name:'人との関わり', text:'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。以下の記録に登場する人（名前や呼び名のある相手）ごとに、その人が現れる場面でわたしがどんな様子だったかを、やさしい日本語で短くまとめてください。関係への評価はしないでください。記録に無いことは書かないでください。'},
  {id:'d5', name:'捉え直しの癖（追伸）', text:'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。以下の記録には、その場で書いた呟きと、時間を置いてから書き足した「追伸」が含まれます。その時の見え方と、後からの見え方がどう変わったか（あるいは変わらなかったか）に注目し、わたしの「捉え直しの癖」をやさしく描写してください。記録に無いことは書かないでください。'},
  {id:'d6', name:'願いの粒', text:'あなたは、わたしの日記を読み解く、静かで思いやりのある観察者です。以下の記録から、「〜したい」「〜すればよかった」「〜が楽しみ」といった、望みや願いのかけらをそのまま拾い集めて、一覧にしてください。実現の方法は提案しないでください。集めるだけで結構です。記録に無いことは書かないでください。'},
  {id:'d7', name:'親友からの手紙', text:'あなたは、わたしの日記をこっそり読ませてもらった、古くからの親友です。以下の記録だけを手がかりに、わたしに宛てた短い手紙を書いてください。励ましの押しつけはせず、「読んでいてこう見えたよ」ということを、正直であたたかい言葉で。記録に無い出来事は書かないでください。'},
  {id:'d8', name:'問いだけ返す', text:'あなたは、わたしの日記を読み解く、静かな聞き手です。以下の記録を読んで、要約も分析もせず、わたしが自分で考えたくなる問いを3つだけ、やさしい日本語で返してください。問いは記録の中の具体的な言葉を引いて作ってください。答えの誘導はしないでください。'},
  {id:'d9', name:'カーネギーの視点', text:'あなたはデール・カーネギー（『人を動かす』『道は開ける』の著者）です。以下のわたしの日記を読み、そこに書かれた行動や人との関わり、悩みへの向き合い方を、あなたの原則（批判より理解、相手の立場に立つ、率直で誠実に認める、今日一日の区切りで生きる など）から、やさしい日本語で読み解いてください。まず、うまくできている行動を記録の言葉を引いて具体的に認めてください。そのうえで、試してみたくなる小さな工夫があれば1つだけ、押しつけずに添えてください。記録に無い出来事は創作しないでください。'}
];
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
// 選択日を表す2つの日付バー（呟き画面・振り返り画面）を同じ日にそろえる。
function syncDaybar(labelId, nextId, pickerId){
  const lbl=document.getElementById(labelId);
  if(lbl){ lbl.textContent=murmurDayLabel(); lbl.classList.toggle('past', murmurDay!==todayKey); }
  const nx=document.getElementById(nextId); if(nx) nx.disabled=(murmurDay===todayKey);
  const pk=document.getElementById(pickerId); if(pk) pk.value=murmurDay;
}
// 日付バーの下の内容（両画面）をフェードインさせる。クラスを付け直して再生する。
function replayDayFade(){
  ['murmurBody','reflectBody'].forEach(id=>{
    const el=document.getElementById(id); if(!el) return;
    el.classList.remove('fade');
    void el.offsetWidth;   // リフローを挟んでアニメーションを最初から再生
    el.classList.add('fade');
  });
}
function setMurmurDay(ds){
  if(ds>todayKey) ds=todayKey;
  const changed=ds!==murmurDay;
  murmurDay=ds;
  const isToday=murmurDay===todayKey;
  // 呟き画面の日付バー
  syncDaybar('dayLabel','dayNext','dayPicker');
  document.getElementById('feedLabel').textContent=(isToday?'きょう':murmurDayLabel())+'の呟き';
  document.getElementById('murmurInput').placeholder = isToday ? 'いま、なにを思ってる?' : 'この日のことを、おもいだして。';
  // 振り返り画面の日付バー（呟きと同じ日を共有）
  syncDaybar('dayLabelR','dayNextR','dayPickerR');
  updateReflectDayUI();
  renderFeed();
  renderDengon();
  // 振り返りタブを開いていれば、その日の内容に更新する
  if(document.getElementById('screen-reflect').classList.contains('active')){ renderGathered(); loadReflection(); }
  // 日付が変わったときだけ、日付バーの下の内容をフェードイン
  if(changed) replayDayFade();
}
// 振り返り画面の見出し・保存ボタンを、選択日に合わせて更新する。
function updateReflectDayUI(){
  const isToday=murmurDay===todayKey;
  const gt=document.getElementById('gTitle');
  if(gt) gt.textContent=(isToday?'きょう':murmurDayLabel())+'集めた呟き';
  const sb=document.getElementById('saveReflect');
  if(sb) sb.textContent=isToday?'あしたへ文を出す':'この日から文を出す';
  // あしたへの伝言は今日だけ（過去日には出さない）
  const dgWrap=document.getElementById('dengonWrap');
  if(dgWrap) dgWrap.style.display=isToday?'block':'none';
}
function shiftMurmurDay(n){
  const [y,m,d]=murmurDay.split('-').map(Number);
  const dt=new Date(y,m-1,d); dt.setDate(dt.getDate()+n);
  setMurmurDay(fmtKey(dt));
}

/* ============ render: murmur feed ============ */
// タイムラインの印と操作アイコン（線画）。呟き=フキダシ、こころみ=電球。
const ICO_BUBBLE='<svg viewBox="0 0 24 24"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>';
const ICO_BULB='<svg viewBox="0 0 24 24"><path d="M12 3a6 6 0 0 0-3.6 10.8c.8.6 1.1 1.4 1.1 2.2h5c0-.8.3-1.6 1.1-2.2A6 6 0 0 0 12 3z"/><path d="M9.8 19.5h4.4"/><path d="M10.6 22h2.8"/></svg>';
const ICO_BULB_TOGGLE='<svg viewBox="0 0 24 24"><path d="M12 5a5 5 0 0 0-3 9c.7.5.9 1.1.9 1.8h4.2c0-.7.2-1.3.9-1.8a5 5 0 0 0-3-9z"/><path d="M10.2 18.6h3.6"/><path d="M10.9 21h2.2"/><g class="rays"><path d="M12 1.2v1.6"/><path d="M4.6 4.6l1.2 1.2"/><path d="M19.4 4.6l-1.2 1.2"/><path d="M2.6 11h1.6"/><path d="M19.8 11h1.6"/></g></svg>';
const ICO_TSN='<svg viewBox="0 0 24 24"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/><path d="M12 8.6v5.8M9.1 11.5h5.8"/></svg>';
const ICO_EDIT='<svg viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>';
const ICO_DEL='<svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"/><path d="M6 6l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13"/><path d="M10 11v6M14 11v6"/></svg>';
// フィード内で別の呟きが編集中（なおす・追伸・結果の入力中）なら、その要素を返す。
// アクティブな呟きは常に1つ：編集中は他の呟きへの操作を受け付けない。
function feedEditingElsewhere(el){
  const f=document.getElementById('murmurFeed');
  const o=f && f.querySelector('.murmur.editing, .murmur.tsn-editing');
  return (o && o!==el) ? o : null;
}
// 編集を始めるとき、呟き・追伸の選択をすべて解除する
function clearFeedSelection(){
  document.querySelectorAll('#murmurFeed .murmur.selected').forEach(x=>x.classList.remove('selected'));
  document.querySelectorAll('#murmurFeed .echo.selected').forEach(x=>x.classList.remove('selected'));
}
// 追伸/結果の書いた時刻（HH:MM）。古いデータで ts が無ければ空。
function echoTime(e){
  if(!e.ts) return '';
  const d=new Date(e.ts);
  return String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
}
// 追伸/結果のメタ表示。同日は「追伸・14:22」、後日は「追伸・3日後 14:22」。
function echoMeta(e, baseDs){
  const head=e.result?'結果':'追伸';
  const el=elapsedLabel(baseDs, e.day);
  const t=echoTime(e);
  const parts=[el==='その日'?null:el, t].filter(Boolean);
  return head+(parts.length?'・'+parts.join(' '):'');
}
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
    const tv=timeValue(m);   // 時刻ピッカーの初期値（HH:MM）
    // 追伸（時間を置いてからの一言）と結果。呟きの下に連なる。タップで編集。
    const echoes=(m.echoes||[]).map(e=>`
      <div class="echo" data-eid="${e.id}"><div class="echo-meta">${echoMeta(e, murmurDay)}</div><div class="echo-text">${escapeHtml(e.text)}</div>
        <div class="echo-actions">
          <button class="act e-edit" type="button" aria-label="なおす" title="なおす">${ICO_EDIT}</button>
          <button class="act e-del" type="button" aria-label="消す" title="消す">${ICO_DEL}</button>
        </div>
      </div>`).join('');
    // こころみで結果がまだなら「どうだった?」の促し
    const hasResult=(m.echoes||[]).some(e=>e.result);
    const ask=(m.kokoromi&&!hasResult)?'<div class="ask-row"><button class="ask-btn" type="button">どうだった?</button></div>':'';
    // 時刻はタップで変更可。実体の time input を透明で重ね、どの環境でも
    // ネイティブの時刻ピッカーが開くようにする（iOS Safari 含む）。
    el.innerHTML=`
      <span class="time time-wrap"><span class="time-text">${m.time}</span><input type="time" class="time-picker" value="${tv}" aria-label="時刻を変更"></span>
      <div class="track"><span class="mark-wrap">${m.kokoromi?ICO_BULB:ICO_BUBBLE}</span></div>
      <div class="body">${escapeHtml(m.text)}${badge}${late}${echoes?`<div class="echoes">${echoes}</div>`:''}${ask}
        <div class="actions">
          <button class="act kk${m.kokoromi?' on':''}" type="button" aria-label="こころみの印" title="こころみ">${ICO_BULB_TOGGLE}</button>
          <button class="act tsn" type="button" aria-label="追伸を書く" title="追伸">${ICO_TSN}</button>
          <button class="act edit" type="button" aria-label="なおす" title="なおす">${ICO_EDIT}</button>
          <button class="act del" type="button" aria-label="消す" title="消す">${ICO_DEL}</button>
        </div>
      </div>`;
    const tpick=el.querySelector('.time-picker');
    tpick.onchange=()=>editMurmurTime(m.id, tpick.value);
    el.querySelector('.act.kk').onclick=(e)=>{
      e.stopPropagation();
      toggleKokoromi(m.id);
    };
    const askBtn=el.querySelector('.ask-btn');
    if(askBtn) askBtn.onclick=(e)=>{ e.stopPropagation(); startResult(m, el); };
    el.querySelector('.tsn').onclick=(e)=>{
      e.stopPropagation();
      startTsuishin(m, el);
    };
    // 追伸・結果のタップ→まず選択（ペン・ゴミ箱のアイコンを出すだけ）。編集はペンから。
    el.querySelectorAll('.echo').forEach(eEl=>{
      const echoOf=()=> (m.echoes||[]).find(x=>x.id===eEl.dataset.eid);
      eEl.addEventListener('click',(ev)=>{
        ev.stopPropagation();
        if(ev.target.closest('.echo-actions')||eEl.classList.contains('editing')) return;
        if(el.classList.contains('editing')||el.classList.contains('tsn-editing')||feedEditingElsewhere(el)) return;
        const was=eEl.classList.contains('selected');
        clearFeedSelection();
        if(!was) eEl.classList.add('selected');
      });
      eEl.querySelector('.e-edit').onclick=(ev)=>{
        ev.stopPropagation();
        const echo=echoOf();
        if(echo) startEditEcho(m, echo, el, eEl);
      };
      eEl.querySelector('.e-del').onclick=async(ev)=>{
        ev.stopPropagation();
        const echo=echoOf();
        if(!echo) return;
        await deleteEcho(m.id, echo.id);
        renderFeed();
        if(murmurDay===todayKey) renderGathered();
      };
    });
    el.querySelector('.edit').onclick=(e)=>{
      e.stopPropagation();
      startEditMurmur(m, el);
    };
    el.querySelector('.del').onclick=async(e)=>{
      e.stopPropagation();
      const d=await getDay(murmurDay);
      d.murmurs=d.murmurs.filter(x=>x.id!==m.id);
      await setDay(murmurDay,d);
      renderFeed(); refreshMeta();
    };
    // タップで選択（ハイライト）→操作アイコンが現れる。時刻・ボタン・入力欄のタップは除外。
    // 別の呟きが編集中の間は選択を受け付けない（書きかけを守り、アクティブを1つに保つ）。
    el.addEventListener('click',(e)=>{
      if(e.target.closest('.time-wrap')||e.target.closest('.actions')||e.target.closest('.ask-btn')||e.target.closest('.tsn-box')||el.classList.contains('editing')||el.classList.contains('tsn-editing')||feedEditingElsewhere(el)) return;
      const wasSel=el.classList.contains('selected');
      clearFeedSelection();   // 呟き・追伸の選択をひとつに保つ
      if(!wasSel) el.classList.add('selected');
    });
    feed.appendChild(el);
  });
}
function escapeHtml(s){ return s.replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }

// 呟きの時刻ピッカー初期値（HH:MM）。既に HH:MM ならそれ、そうでなければ ts から。
function timeValue(m){
  if(typeof m.time==='string' && /^\d{2}:\d{2}$/.test(m.time)) return m.time;
  const d=new Date(m.ts||Date.now());
  return String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
}
// 登録済みの呟きの時刻を変更（表示時刻と並び順の ts を更新）。
async function editMurmurTime(id, value){
  if(!/^\d{2}:\d{2}$/.test(value||'')) return;
  const d=await getDay(murmurDay);
  const it=(d.murmurs||[]).find(x=>x.id===id);
  if(!it) return;
  if(it.time===value) return;
  const [hh,mm]=value.split(':').map(Number);
  const base=new Date(murmurDay+'T00:00:00'); base.setHours(hh,mm,0,0);
  it.time=value;
  it.ts=base.getTime();          // 並び順も新しい時刻に合わせる
  await setDay(murmurDay,d);
  renderFeed();
  if(murmurDay===todayKey) renderGathered();
  toast('時刻を変更しました');
}

// 登録済みの呟きの本文を、その場で編集する（インライン編集）。
function startEditMurmur(m, el){
  if(el.classList.contains('editing')||feedEditingElsewhere(el)) return;
  clearFeedSelection();
  el.classList.add('editing');
  const body=el.querySelector('.body');
  body.innerHTML=`
    <textarea class="edit-input" aria-label="呟きを編集"></textarea>
    <div class="edit-actions">
      <button class="edit-cancel" type="button">やめる</button>
      <button class="edit-save" type="button">保存</button>
    </div>`;
  const ta=body.querySelector('.edit-input');
  ta.value=m.text;
  const grow=()=>{ ta.style.height='auto'; ta.style.height=ta.scrollHeight+'px'; };
  ta.addEventListener('input',grow);
  // 編集領域のタップで選択が解除されないように、内部のクリックは伝播させない。
  body.addEventListener('click',e=>e.stopPropagation());
  const finish=()=>{ el.classList.remove('editing'); renderFeed(); };
  body.querySelector('.edit-cancel').onclick=finish;
  body.querySelector('.edit-save').onclick=async()=>{
    const val=ta.value.trim();
    if(!val){ ta.focus(); return; }   // 空にはできない
    await saveMurmurText(m.id, val);
    finish();
  };
  grow();
  ta.focus();
  ta.setSelectionRange(ta.value.length, ta.value.length);
}
// 呟きの本文を保存する（本文のみ更新。時刻・並び順は変えない）。
async function saveMurmurText(id, text){
  const d=await getDay(murmurDay);
  const it=(d.murmurs||[]).find(x=>x.id===id);
  if(!it) return;
  if(it.text===text) return;
  it.text=text;
  await setDay(murmurDay,d);
  if(murmurDay===todayKey) renderGathered();
  refreshMeta();
  toast('呟きをなおしました');
}

/* ============ こころみ（1日内の行動とフィードバック） ============ */
// 呟きのこころみ印をつけ外しする
async function toggleKokoromi(id){
  const d=await getDay(murmurDay);
  const it=(d.murmurs||[]).find(x=>x.id===id);
  if(!it) return;
  if(it.kokoromi) delete it.kokoromi; else it.kokoromi=true;
  await setDay(murmurDay,d);
  renderFeed();
  if(murmurDay===todayKey) renderGathered();
  toast(it.kokoromi?'こころみの印をつけました':'こころみの印を外しました');
}
// 「どうだった?」→ その場で結果をのこす（追伸と同じ入力欄・見出しだけ変える）
function startResult(m, el){
  if(el.querySelector('.tsn-box')||el.classList.contains('editing')||feedEditingElsewhere(el)) return;
  clearFeedSelection();
  el.classList.add('tsn-editing');
  const body=el.querySelector('.body');
  const box=document.createElement('div');
  box.className='tsn-box';
  box.innerHTML=`
    <div class="tsn-cap">やってみて、どうだった?</div>
    <textarea class="tsn-input" aria-label="結果を書く" placeholder="できたこと、できなかったこと。気づいたこと"></textarea>
    <div class="edit-actions">
      <button class="edit-cancel" type="button">やめる</button>
      <button class="edit-save" type="button">結果をのこす</button>
    </div>`;
  const ask=el.querySelector('.ask-row'); if(ask) ask.style.display='none';
  body.insertBefore(box, el.querySelector('.actions'));
  const ta=box.querySelector('.tsn-input');
  const grow=()=>{ ta.style.height='auto'; ta.style.height=ta.scrollHeight+'px'; };
  ta.addEventListener('input',grow);
  box.addEventListener('click',e=>e.stopPropagation());
  const finish=()=>{ el.classList.remove('tsn-editing'); renderFeed(); };
  box.querySelector('.edit-cancel').onclick=finish;
  box.querySelector('.edit-save').onclick=async()=>{
    const val=ta.value.trim();
    if(!val){ ta.focus(); return; }
    const d=await getDay(murmurDay);
    const it=(d.murmurs||[]).find(x=>x.id===m.id);
    if(it){
      if(!Array.isArray(it.echoes)) it.echoes=[];
      it.echoes.push({id:'e'+Date.now(), text:val, ts:Date.now(), day:todayKey, result:true});
      await setDay(murmurDay,d);
      toast('こころみの結果をのこしました');
    }
    finish();
    if(murmurDay===todayKey) renderGathered();
  };
  grow();
  ta.focus();
}

/* ============ 追伸（時間を置いてからの一言） ============ */
// 呟きの日から書いた日までの経過ラベル（その日/翌日/◯日後/◯週間後/◯か月後/◯年後）。
function elapsedLabel(fromDs, toDs){
  const a=new Date(fromDs+'T00:00:00'), b=new Date(toDs+'T00:00:00');
  const d=Math.round((b-a)/86400000);
  if(d<=0) return 'その日';
  if(d===1) return '翌日';
  if(d<7) return d+'日後';
  if(d<30) return Math.floor(d/7)+'週間後';
  if(d<365) return Math.floor(d/30)+'か月後';
  return Math.floor(d/365)+'年後';
}
// 呟きの下に追伸の入力欄（明朝）を開く。
function startTsuishin(m, el){
  if(el.querySelector('.tsn-box')||el.classList.contains('editing')||feedEditingElsewhere(el)) return;
  clearFeedSelection();
  el.classList.add('tsn-editing');
  const body=el.querySelector('.body');
  const box=document.createElement('div');
  box.className='tsn-box';
  box.innerHTML=`
    <div class="tsn-cap">時間を置いて、いま思うこと</div>
    <textarea class="tsn-input" aria-label="追伸を書く" placeholder="あの時のことを、いまどう思う?"></textarea>
    <div class="edit-actions">
      <button class="edit-cancel" type="button">やめる</button>
      <button class="edit-save" type="button">追伸をのこす</button>
    </div>`;
  body.insertBefore(box, el.querySelector('.actions'));
  const ta=box.querySelector('.tsn-input');
  const grow=()=>{ ta.style.height='auto'; ta.style.height=ta.scrollHeight+'px'; };
  ta.addEventListener('input',grow);
  // 入力中のタップで選択が解除されないように、内部のクリックは伝播させない。
  box.addEventListener('click',e=>e.stopPropagation());
  const finish=()=>{ el.classList.remove('tsn-editing'); renderFeed(); };
  box.querySelector('.edit-cancel').onclick=finish;
  box.querySelector('.edit-save').onclick=async()=>{
    const val=ta.value.trim();
    if(!val){ ta.focus(); return; }
    await saveTsuishin(m.id, val);
    finish();
  };
  grow();
  ta.focus();
}
// 追伸をその場で編集する（保存・消すも可）。親の呟きの編集とは独立。
function startEditEcho(m, echo, murmurEl, echoEl){
  if(murmurEl.classList.contains('editing')||murmurEl.querySelector('.tsn-box')||echoEl.classList.contains('editing')||feedEditingElsewhere(murmurEl)) return;
  clearFeedSelection();
  murmurEl.classList.add('tsn-editing');   // 編集中は下部ボタンを隠す（追伸入力時と同じ）
  echoEl.classList.add('editing');
  const body=echoEl.querySelector('.echo-text');
  body.innerHTML=`
    <textarea class="tsn-input" aria-label="追伸を編集"></textarea>
    <div class="edit-actions">
      <button class="edit-cancel" type="button">やめる</button>
      <button class="edit-save" type="button">保存</button>
    </div>`;
  const ta=body.querySelector('.tsn-input');
  ta.value=echo.text;
  const grow=()=>{ ta.style.height='auto'; ta.style.height=ta.scrollHeight+'px'; };
  ta.addEventListener('input',grow);
  const finish=()=>{ murmurEl.classList.remove('tsn-editing'); renderFeed(); };
  body.querySelector('.edit-cancel').onclick=(e)=>{ e.stopPropagation(); finish(); };
  body.querySelector('.edit-save').onclick=async(e)=>{
    e.stopPropagation();
    const val=ta.value.trim();
    if(!val){ ta.focus(); return; }   // 空にはできない（消したいときはゴミ箱から）
    await saveEchoText(m.id, echo.id, val);
    finish();
  };
  grow();
  ta.focus();
  ta.setSelectionRange(ta.value.length, ta.value.length);
}
async function saveEchoText(mid, eid, text){
  const d=await getDay(murmurDay);
  const it=(d.murmurs||[]).find(x=>x.id===mid); if(!it) return;
  const ec=(it.echoes||[]).find(x=>x.id===eid); if(!ec) return;
  if(ec.text===text) return;
  ec.text=text;
  await setDay(murmurDay,d);
  toast('追伸をなおしました');
}
async function deleteEcho(mid, eid){
  const d=await getDay(murmurDay);
  const it=(d.murmurs||[]).find(x=>x.id===mid); if(!it) return;
  it.echoes=(it.echoes||[]).filter(x=>x.id!==eid);
  await setDay(murmurDay,d);
  toast('追伸を消しました');
}

// 追伸を保存する（元の呟きが属する日の記録に紐づけ、書いた日を持つ）。
async function saveTsuishin(id, text){
  const d=await getDay(murmurDay);
  const it=(d.murmurs||[]).find(x=>x.id===id);
  if(!it) return;
  if(!Array.isArray(it.echoes)) it.echoes=[];
  it.echoes.push({id:'e'+Date.now(), text, ts:Date.now(), day:todayKey});
  await setDay(murmurDay,d);
  toast('追伸をのこしました');
}

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

/* ============ 伝言（未来のわたしへ／過去のわたしから） ============
   宛先の日付を選んで文を結べる。保存は journal:letters（宛先日ごとに1通）：
   [{from:'YYYY-MM-DD', to:'YYYY-MM-DD', text, ts, readAt?}] */
let LETTERS=[];
let dengonState=null;      // 表示中の伝言 { to }
let dengonDest='tomorrow'; // 書く側のあて先（'tomorrow' | 'YYYY-MM-DD'）
let dengonKokoromi=false;  // 書く側の「こころみ」印（やってみることを託す文）
async function loadLetters(){ LETTERS=(await Store.get('journal:letters'))||[]; }
async function saveLetters(){ await Store.set('journal:letters', LETTERS); }
// 旧形式（day.toTomorrow）を journal:letters へ吸収する（初回・復元後）。
async function absorbToTomorrow(){
  const keys=await Store.listDays();
  let changed=false;
  for(const k of keys){
    const day=await Store.get(k);
    if(day && day.toTomorrow && day.toTomorrow.text){
      const ds=k.slice('journal:day:'.length);
      const [y,m,d]=ds.split('-').map(Number);
      const to=fmtKey(new Date(y,m-1,d+1));
      if(!LETTERS.some(l=>l.to===to)){
        const e={from:ds, to, text:day.toTomorrow.text, ts:day.toTomorrow.ts||0};
        if(day.toTomorrow.readAt) e.readAt=day.toTomorrow.readAt;
        LETTERS.push(e);
      }
      delete day.toTomorrow;
      await Store.set(k,day);
      changed=true;
    }
  }
  if(changed) await saveLetters();
}
function tomorrowKey(){ const d=new Date(today); d.setDate(d.getDate()+1); return fmtKey(d); }
function destKey(){ return dengonDest==='tomorrow'?tomorrowKey():dengonDest; }
function jpMD(ds){ const [y,m,d]=ds.split('-').map(Number); return m+'月'+d+'日'; }
// 書いた日から見た「◯前のわたし」ラベル
function agoLabel(fromDs){
  const a=new Date(fromDs+'T00:00:00'), b=new Date(todayKey+'T00:00:00');
  const d=Math.round((b-a)/86400000);
  if(d<=1) return 'きのう';
  if(d<7) return d+'日前';
  if(d<30) return Math.floor(d/7)+'週間前';
  if(d<365) return Math.floor(d/30)+'か月前';
  return Math.floor(d/365)+'年前';
}
// 今日から見た「◯後」ラベル（あて先表示用）
function aheadLabel(toDs){
  const d=Math.round((new Date(toDs+'T00:00:00')-new Date(todayKey+'T00:00:00'))/86400000);
  if(d<=1) return 'あした';
  if(d<7) return d+'日後';
  if(d<30) return Math.floor(d/7)+'週間後';
  if(d<365) return Math.floor(d/30)+'か月後';
  return Math.floor(d/365)+'年後';
}
// 呟き画面（今日）の伝言カードを描画。
// 宛先日が来た未読の文（複数あれば宛先の新しい順に1通ずつ）→ 水引。今日読んだ文 → 畳んだ一行。
function renderDengon(){
  const wrap=document.getElementById('dengonLetter');
  if(!wrap) return;
  wrap.style.display='none';
  wrap.classList.remove('open','folded','opening');
  dengonState=null;
  if(murmurDay!==todayKey) return;
  const arrived=LETTERS.filter(l=>l.to<=todayKey && l.text);
  let t=arrived.filter(l=>!l.readAt).sort((a,b)=>a.to<b.to?1:-1)[0];
  let folded=false;
  if(!t){
    t=arrived.find(l=>l.readAt && fmtKey(new Date(l.readAt))===todayKey);
    folded=!!t;
  }
  if(!t) return;
  dengonState={to:t.to};
  const ago=agoLabel(t.from);
  const who=(ago==='きのう'?'きのう':jpMD(t.from))+'のわたしから';
  const kk=!!t.kokoromi;
  document.getElementById('dgCap').innerHTML=escapeHtml(who)+'、'+(kk?'<b class="dg-kk">こころみ</b>の':'')+'文が届いています';
  document.getElementById('dgFrom').innerHTML='<span>'+escapeHtml(who+(ago==='きのう'?'':'（'+ago+'）'))+'</span>'+(kk?'<span class="badge-k">こころみ</span>':'');
  document.getElementById('dgMiniLabel').textContent=who+(t.reply&&t.reply.text?(kk?'（結果あり）':'（返事あり）'):'')+' — タップで読み返す';
  document.getElementById('dgText').textContent=t.text;
  refreshHenji(t);
  if(folded) wrap.classList.add('folded');
  wrap.style.display='block';
}
/* --- 返事（往復書簡） --- */
// 表示中の伝言（届いた文）を取り出す
function currentDengonLetter(){
  return dengonState ? LETTERS.find(l=>l.to===dengonState.to) : null;
}
// 便箋の返事まわり（表示・ボタン）を今の状態に合わせる
function refreshHenji(l){
  const view=document.getElementById('henjiView');
  const box=document.getElementById('henjiBox');
  const btn=document.getElementById('henjiBtn');
  const edit=document.getElementById('henjiEditBtn');
  if(!view) return;
  box.style.display='none';
  const has=!!(l && l.reply && l.reply.text);
  const kk=!!(l && l.kokoromi);
  view.style.display=has?'block':'none';
  if(has){
    document.getElementById('henjiMeta').textContent=(l.reply.day===todayKey?'きょう':jpMD(l.reply.day))+'のわたしより、'+(kk?'結果':'返事');
    document.getElementById('henjiText').textContent=l.reply.text;
  }
  // こころみの文には「結果」を訊く
  btn.textContent=kk?'結果を書く':'返事を書く';
  btn.style.display=has?'none':'inline-block';
  edit.style.display=has?'inline-block':'none';
  // こころみのリレー：結果を書いたあと、まだ結んでいなければ「つづきを結ぶ？」
  const relayRow=document.getElementById('relayRow');
  const relayDone=document.getElementById('relayDone');
  if(relayRow&&relayDone){
    const relayedTo=(l&&l.relayTo&&LETTERS.some(x=>x.to===l.relayTo))?l.relayTo:null;
    relayRow.style.display=(has&&kk&&!relayedTo)?'flex':'none';
    relayDone.style.display=relayedTo?'block':'none';
    if(relayedTo) relayDone.textContent='つづき：'+jpMD(relayedTo)+'のわたしへ結ばれています';
  }
}
// つづきを結ぶ：同じこころみの文を、n日後のわたしへもう一度
async function relayKokoromi(days){
  const l=currentDengonLetter();
  if(!l||!l.kokoromi||!l.reply) return;
  const dt=new Date(today); dt.setDate(dt.getDate()+days);
  const to=fmtKey(dt);
  if(LETTERS.some(x=>x.to===to)){ toast(jpMD(to)+'には、すでに文が結ばれています'); return; }
  LETTERS.push({from:todayKey, to, text:l.text, ts:Date.now(), kokoromi:true});
  l.relayTo=to;
  await saveLetters();
  refreshHenji(l);
  renderPending();
  toast(jpMD(to)+'のわたしへ、つづきを結びました');
}
// 返事の入力欄をひらく（書き直しにも使う）
function openHenjiBox(){
  const l=currentDengonLetter();
  if(!l) return;
  const ago=agoLabel(l.from);
  const kk=!!l.kokoromi;
  document.getElementById('henjiCap').textContent=kk?'やってみて、どうだった?':(ago==='きのう'?'きのう':jpMD(l.from))+'のわたしへ、返事';
  document.getElementById('henjiInput').placeholder=kk?'できたこと、できなかったこと。気づいたこと':'この文をくれたわたしに、いま伝えたいこと';
  document.getElementById('henjiSave').textContent=kk?'結果をのこす':'返事をのこす';
  document.getElementById('henjiView').style.display='none';
  document.getElementById('henjiBtn').style.display='none';
  document.getElementById('henjiEditBtn').style.display='none';
  const box=document.getElementById('henjiBox');
  box.style.display='block';
  const ta=document.getElementById('henjiInput');
  ta.value=(l.reply&&l.reply.text)||'';
  ta.style.height='auto'; ta.style.height=Math.max(52,ta.scrollHeight)+'px';
  ta.focus();
}
async function saveHenji(){
  const l=currentDengonLetter();
  if(!l) return;
  const val=document.getElementById('henjiInput').value.trim();
  if(!val){ document.getElementById('henjiInput').focus(); return; }
  l.reply={text:val, ts:Date.now(), day:todayKey};
  await saveLetters();
  refreshHenji(l);
  const ago=agoLabel(l.from);
  const who=(ago==='きのう'?'きのう':jpMD(l.from))+'のわたし';
  document.getElementById('dgMiniLabel').textContent=who+'から（'+(l.kokoromi?'結果':'返事')+'あり） — タップで読み返す';
  toast(l.kokoromi?'こころみの結果をのこしました':who+'へ、返事をのこしました');
}
// 開封：水引がほどける → 便箋。既読を保存。
async function openDengon(){
  const wrap=document.getElementById('dengonLetter');
  if(!wrap||wrap.classList.contains('opening')||wrap.classList.contains('open')) return;
  wrap.classList.add('opening');
  setTimeout(()=>{ wrap.classList.remove('opening'); wrap.classList.add('open'); }, 680);
  if(dengonState){
    const l=LETTERS.find(x=>x.to===dengonState.to);
    if(l && !l.readAt){ l.readAt=Date.now(); await saveLetters(); }
  }
}
/* --- 書く側（振り返り画面）：あて先と結ばれている文 --- */
function setDengonDest(v){
  dengonDest=v;
  const chipT=document.getElementById('destTomorrow');
  const chipP=document.getElementById('destPick');
  const note=document.getElementById('destNote');
  const isT=v==='tomorrow';
  chipT.classList.toggle('sel',isT);
  chipP.classList.toggle('sel',!isT);
  chipP.firstChild.textContent=isT?'日付をえらぶ':jpMD(v);
  if(isT){ note.style.display='none'; }
  else { note.textContent=jpMD(v)+'（'+aheadLabel(v)+'）のわたしへ'; note.style.display='block'; }
  // あて先の日に結ばれている文があれば呼び出す（こころみの印も一緒に戻す）
  const dgEl=document.getElementById('dengonInput');
  if(dgEl){
    const ex=LETTERS.find(l=>l.to===destKey());
    dgEl.value=ex?ex.text:'';
    dgEl.style.height='auto'; dgEl.style.height=dgEl.scrollHeight+'px';
    // 既にその日へ結んだ文があればその印を、無ければ書きかけの意思をそのまま残す
    setDengonKokoromi(ex?!!ex.kokoromi:dengonKokoromi);
  }
  updateSaveBtn();
}
// こころみ（やってみることを託す文）の印。見出し・書き出しの促しが変わる。
function setDengonKokoromi(on){
  dengonKokoromi=!!on;
  const chip=document.getElementById('destKokoromi');
  const label=document.getElementById('dengonLabel');
  const input=document.getElementById('dengonInput');
  const hint=document.getElementById('dengonHint');
  if(!chip) return;
  chip.classList.toggle('sel',dengonKokoromi);
  chip.setAttribute('aria-pressed',dengonKokoromi?'true':'false');
  if(dengonKokoromi){
    label.textContent='未来のわたしへ、こころみの文';
    input.placeholder='やってみること。そして、確かめたいこと';
    hint.innerHTML='えらんだ日の朝、届きます。その日の返事が、そのまま結果になります。<br>例）<em>今週は夜11時に寝てみる。朝がラクになったかな?</em>';
  }else{
    label.textContent='未来のわたしへ、ひとこと';
    input.placeholder='その日のわたしに、伝えておきたいこと';
    hint.innerHTML='えらんだ日の朝、呟き画面に結んで届きます。書かなくても文は出せます。';
  }
}
// 結ばれている文（今日以降に届く文）の一覧
function renderPending(){
  const wrap=document.getElementById('pendingWrap'), list=document.getElementById('pendingList');
  if(!wrap||!list) return;
  const pend=LETTERS.filter(l=>l.to>todayKey && l.text).sort((a,b)=>a.to<b.to?-1:1);
  wrap.style.display=pend.length?'block':'none';
  list.innerHTML='';
  pend.forEach(l=>{
    const el=document.createElement('div');
    el.className='p-item';
    el.innerHTML=`
      <svg class="p-knot" viewBox="0 0 22 14" aria-hidden="true"><path d="M1 7 H6 C8 7 8 3 11 3 C14 3 14 11 11 11 C8 11 8 7 10 7 H21"/></svg>
      <div class="p-body">
        <div class="p-to">${jpMD(l.to)}（${aheadLabel(l.to)}）のわたしへ${l.kokoromi?' <span class="badge-k">こころみ</span>':''}</div>
        <div class="p-text"></div>
      </div>
      <button class="p-undo" type="button">解く</button>`;
    el.querySelector('.p-text').textContent=l.text;
    el.querySelector('.p-undo').onclick=async()=>{
      LETTERS=LETTERS.filter(x=>x.to!==l.to);
      await saveLetters();
      renderPending(); setDengonDest(dengonDest);
      renderDengon();   // 呟き画面の便箋（つづきの表示など）も最新に
      toast('結びを解きました');
    };
    list.appendChild(el);
  });
}

/* ============ reflection ============ */
function updateSaveBtn(){
  const txt=document.getElementById('reflectInput').value.trim();
  // 今日なら「あしたへの伝言」だけでも文を出せる
  const dgEl=document.getElementById('dengonInput');
  const dgTxt=(murmurDay===todayKey && dgEl)?dgEl.value.trim():'';
  document.getElementById('saveReflect').disabled=!txt.length && !dgTxt.length;
}
async function renderGathered(){
  const day=await getDay(murmurDay);
  const list=document.getElementById('gList');
  document.getElementById('gCount').textContent=day.murmurs.length+' 件';
  const adb=document.getElementById('aiDraftBtn'); if(adb) adb.disabled=!day.murmurs.length;
  if(!day.murmurs.length){ list.innerHTML='<div class="g-empty">'+(murmurDay===todayKey?'今日はまだ呟きがありません。':'この日の呟きはありません。')+'</div>'; return; }
  list.innerHTML='';
  [...day.murmurs].sort((a,b)=>a.ts-b.ts).forEach(m=>{
    const el=document.createElement('div');
    el.className='g-item';
    // 追伸・結果も内省の素材として、呟きの下に添える
    const ech=(m.echoes||[]).map(e=>`<div class="g-echo"><span class="g-echo-meta">${echoMeta(e, murmurDay)}</span>${escapeHtml(e.text)}</div>`).join('');
    // 結果がまだのこころみには、そっと印を残す
    const hasResult=(m.echoes||[]).some(e=>e.result);
    const askNote=(m.kokoromi&&!hasResult)?'<div class="g-ask">— どうだった?（まだ結果がありません）</div>':'';
    el.innerHTML=`<span class="gd">${m.kokoromi?ICO_BULB:ICO_BUBBLE}</span><span>${escapeHtml(m.text)}${ech}${askNote}</span>`;
    list.appendChild(el);
  });
}
async function loadReflection(){
  const day=await getDay(murmurDay);
  const inp=document.getElementById('reflectInput');
  const note=document.getElementById('savedNote');
  const aiNote=document.getElementById('aiNote'); if(aiNote) aiNote.textContent='';
  if(day.reflection){
    inp.value=day.reflection.text;
    note.textContent='保存済み — いつでも書き直せます';
  } else {
    inp.value=''; note.textContent='';
  }
  // 伝言のあて先を「あした」に戻し、結ばれている文を読み込む
  // 画面に入るたび、あて先と印はまっさらから（結んだ文を選べば復元される）
  if(murmurDay===todayKey && document.getElementById('dengonInput')){ dengonKokoromi=false; setDengonDest('tomorrow'); renderPending(); }
  updateSaveBtn();
}
async function saveReflection(){
  const txt=document.getElementById('reflectInput').value.trim();
  const isToday=murmurDay===todayKey;
  const dgEl=document.getElementById('dengonInput');
  const dgTxt=(isToday && dgEl)?dgEl.value.trim():'';
  if(!txt && !dgTxt) return;
  const day=await getDay(murmurDay);
  if(txt){
    const prev=day.reflection||{};
    day.reflection={text:txt, savedAt:Date.now()};
    if(!isToday) day.reflection.late=true;   // 過去日にあとから挟んだ振り返り
    if(prev.source) day.reflection.source=prev.source;
  }
  await setDay(murmurDay,day);
  // 未来のわたしへの伝言（今日のみ・宛先日ごとに1通・上書き。空にして保存すると取り消し）
  let dgToast='';
  if(isToday && dgEl){
    const to=destKey();
    const had=LETTERS.some(l=>l.to===to);
    LETTERS=LETTERS.filter(l=>l.to!==to);
    if(dgTxt){
      const entry={from:todayKey, to, text:dgTxt, ts:Date.now()};
      if(dengonKokoromi) entry.kokoromi=true;
      LETTERS.push(entry);
      dgToast = dengonKokoromi ? jpMD(to)+'のわたしへ、こころみを結びました'
              : to===tomorrowKey() ? 'あしたへ伝言を結びました'
              : jpMD(to)+'のわたしへ、文を結びました';
    }
    if(dgTxt||had) await saveLetters();
    renderPending();
  }
  if(txt) document.getElementById('savedNote').textContent='保存済み — いつでも書き直せます';
  refreshMeta();
  toast(txt ? (isToday?'あしたへ文を出しました':murmurDayLabel()+'から文を出しました') : dgToast||'あしたへ文を出しました');
}

/* ============ ai reflection draft (share bridge) ============ */
async function draftReflection(){
  const day=await getDay(murmurDay);
  if(!day.murmurs.length) return;
  const lines=[...day.murmurs].sort((a,b)=>a.ts-b.ts)
    .map(m=>`- ${m.time} ${m.kokoromi?'【こころみ】':''}${m.text}`+((m.echoes||[]).length?`（${m.echoes.map(e=>(e.result?'結果: ':'追伸: ')+e.text).join(' / ')}）`:'')).join('\n');
  openBridge({
    title:'呟きからAI下書き',
    sub:'呟きをAIに送って、下書きを貼り付け',
    prompt:(settings.promptDraft||DEFAULT_PROMPTS.draft),
    contextText:`【${murmurDay===todayKey?'今日':murmurDayLabel()}の呟き】\n${lines}`,
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
  detailDay=ds;
  const day=await getDay(ds);
  document.getElementById('detailDate').textContent=jpDateShort(ds);
  document.getElementById('detailSub').textContent=(day.murmurs.length)+' 件の呟き'+(day.reflection?' ・ 振り返りあり':'');
  const body=document.getElementById('detailBody');
  let html='';
  // 振り返りを上、呟きを下に表示する。
  if(day.reflection){
    const rbadge=day.reflection.source==='hand'?'<span class="badge-hand">✎ 手書き</span>':'';
    const rlate=day.reflection.late?'<span class="badge-hand">あとから</span>':'';
    html+=`<div class="sb-section-label">振り返り${rbadge}${rlate}</div>`;
    html+=`<div class="sb-reflect">${escapeHtml(day.reflection.text)}</div>`;
  }
  const sentLetters=LETTERS.filter(l=>l.from===ds && l.text).sort((a,b)=>a.to<b.to?-1:1);
  if(sentLetters.length){
    const mt=html?' style="margin-top:22px"':'';
    html+=`<div class="sb-section-label"${mt}>伝言</div>`;
    sentLetters.forEach(l=>{
      const rep=(l.reply&&l.reply.text)?`<div class="sb-henji"><span class="sb-henji-meta">↩ ${jpMD(l.reply.day)}のわたしより、${l.kokoromi?'結果':'返事'}</span>${escapeHtml(l.reply.text)}</div>`:'';
      const kk=l.kokoromi?' <span class="badge-k">こころみ</span>':'';
      html+=`<div class="sb-dengon"><span class="sb-dengon-to">${jpMD(l.to)}のわたしへ${kk}</span>${escapeHtml(l.text)}${rep}</div>`;
    });
  }
  if(day.murmurs.length){
    const mt=html?' style="margin-top:22px"':'';
    html+=`<div class="sb-section-label"${mt}>呟き</div>`;
    [...day.murmurs].sort((a,b)=>a.ts-b.ts).forEach(m=>{
      const badge=m.source==='hand'?'<span class="badge-hand">✎ 手書き</span>':'';
      const ech=(m.echoes||[]).map(e=>`<div class="sb-echo"><span class="sb-echo-meta">${echoMeta(e, ds)}</span>${escapeHtml(e.text)}</div>`).join('');
      html+=`<div class="sb-murmur"><span class="t">${m.time}</span><span class="d">${m.kokoromi?ICO_BULB:ICO_BUBBLE}</span><span>${escapeHtml(m.text)}${badge}${ech}</span></div>`;
    });
  }
  if(!day.murmurs.length && !day.reflection && !sentLetters.length){ html='<div class="sb-empty">この日の記録はありません。</div>'; }
  body.innerHTML=html;
  body.scrollTop=0;
  document.getElementById('overlay').classList.add('show');
  document.getElementById('detailSheet').classList.add('show');
}
// 詳細シートを左右スワイプで前後の日に切り替える（未来日は表示しない）。
function shiftDetailDay(n){
  if(!detailDay) return;
  const [y,m,d]=detailDay.split('-').map(Number);
  const dt=new Date(y,m-1,d); dt.setDate(dt.getDate()+n);
  const ds=fmtKey(dt);
  if(ds>todayKey) return;
  openDetail(ds);
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
  // 角度プロンプト集が未導入の旧データには既定を種まき（全削除した場合は空のまま）
  if(!Array.isArray(settings.anglePrompts)){
    settings.anglePrompts=DEFAULT_ANGLES.map(a=>({...a}));
    settings.angleSeeded=DEFAULT_ANGLES.map(a=>a.id);
  }
  // どの既定角度を追加済みかを記録し、あとから増えた既定は既存ユーザーにも一度だけ
  // 追加する。追加済みリストにある id は（削除されていても）復活させない。
  // このリスト導入前のデータは d1〜d8 が追加済み。
  if(!Array.isArray(settings.angleSeeded)) settings.angleSeeded=['d1','d2','d3','d4','d5','d6','d7','d8'];
  let angleSeedChanged=false;
  for(const a of DEFAULT_ANGLES){
    if(!settings.angleSeeded.includes(a.id)){
      settings.anglePrompts.push({...a});
      settings.angleSeeded.push(a.id);
      angleSeedChanged=true;
    }
  }
  if(angleSeedChanged) await saveSettings();
  renderAngleList();
  const remTog=document.getElementById('remToggle');
  remTog.classList.toggle('on',settings.rem);
  remTog.setAttribute('aria-checked', settings.rem?'true':'false');
  document.getElementById('remTime').value=settings.remTime;
  document.getElementById('remTimeRow').style.opacity=settings.rem?'1':'.4';
  document.getElementById('setPromptDraft').value=settings.promptDraft;
  document.getElementById('setPromptWeek').value=settings.promptWeek;
  document.getElementById('setPromptMonth').value=settings.promptMonth;
  document.getElementById('setPromptCustom').value=settings.promptCustom;
}
async function saveSettings(){ await Store.set('journal:settings',settings); }

// 設定：読み解きの角度（プロンプト集）の一覧を組み立てる。名前・本文は変更確定時に保存。
function renderAngleList(){
  const wrap=document.getElementById('angleList');
  if(!wrap) return;
  wrap.innerHTML='';
  (settings.anglePrompts||[]).forEach(a=>{
    const div=document.createElement('div');
    div.className='prompt-field angle-item';
    div.innerHTML=`
      <div class="angle-head">
        <input class="pf-name" placeholder="角度の名前" aria-label="角度の名前">
        <button class="angle-del" type="button">削除</button>
      </div>
      <textarea class="pf-input" rows="4" placeholder="AIへのお願いを書く"></textarea>`;
    const nameEl=div.querySelector('.pf-name'), textEl=div.querySelector('.pf-input');
    nameEl.value=a.name;
    textEl.value=a.text;
    nameEl.addEventListener('change',async()=>{ a.name=nameEl.value.trim()||a.name; nameEl.value=a.name; await saveSettings(); toast('角度を保存しました'); });
    textEl.addEventListener('change',async()=>{ a.text=textEl.value.trim(); await saveSettings(); toast('角度を保存しました'); });
    div.querySelector('.angle-del').onclick=async()=>{
      settings.anglePrompts=(settings.anglePrompts||[]).filter(x=>x.id!==a.id);
      if(utsuroiAngle===a.id) utsuroiAngle='base';
      await saveSettings();
      renderAngleList();
      toast('角度を削除しました');
    };
    wrap.appendChild(div);
  });
}

/* ============ export ============ */
async function buildExportMd(fromDs,toDs){
  const [fy,fm,fd]=fromDs.split('-').map(Number);
  const [ty,tm,td]=toDs.split('-').map(Number);
  let d=new Date(fy,fm-1,fd); const end=new Date(ty,tm-1,td);
  let out=`# 文（ふみ）エクスポート\n\n期間: ${fromDs} 〜 ${toDs}\n`;
  let has=false;
  while(d<=end){
    const ds=fmtKey(d);
    const day=await getDay(ds);
    const sentL=LETTERS.filter(l=>l.from===ds && l.text).sort((a,b)=>a.to<b.to?-1:1);
    if((day.murmurs&&day.murmurs.length)||day.reflection||sentL.length){
      has=true;
      out+=`\n## ${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 (${WD[d.getDay()]})\n`;
      if(day.murmurs&&day.murmurs.length){
        out+=`\n### 呟き\n`;
        [...day.murmurs].sort((a,b)=>a.ts-b.ts).forEach(m=>{
          out+=`- ${m.time} ${m.kokoromi?'【こころみ】':''}${m.text}${m.source==='hand'?'（手書き）':''}\n`;
          (m.echoes||[]).forEach(e=>{
            const el=elapsedLabel(ds,e.day), t=echoTime(e);
            const inner=[el==='その日'?null:el, t].filter(Boolean).join(' ');
            out+=`  - ${e.result?'結果':'追伸'}${inner?'（'+inner+'）':''}: ${e.text}\n`;
          });
        });
      }
      if(day.reflection){
        out+=`\n### 振り返り${day.reflection.source==='hand'?'（手書き）':''}\n${day.reflection.text}\n`;
      }
      sentL.forEach(l=>{
        out+=`\n### ${jpMD(l.to)}のわたしへの${l.kokoromi?'こころみ':'伝言'}\n${l.text}\n`;
        if(l.reply&&l.reply.text) out+=`↩ ${l.kokoromi?'結果':'返事'}（${jpMD(l.reply.day)}）: ${l.reply.text}\n`;
      });
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
    a.href=url; a.download=`fumi_${from}_${to}.md`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
    toast('書き出しました');
  }catch(e){
    try{ await navigator.clipboard.writeText(res.text); toast('保存できない環境のためコピーしました'); }
    catch(e2){ toast('書き出しに失敗しました'); }
  }
}

/* ============ JSON backup / restore（§8.5・最後の砦） ============
   Markdown（§6）＝読む・共有する用。JSON＝画像も含めて丸ごと戻す用。
   schemaVersion で将来の移行に備える。 */
const SCHEMA_VERSION = 1;

async function buildBackup(){
  const keys = (await Store.listAll()).filter(k=>k.startsWith('journal:'));
  const data = {};
  for(const k of keys){ data[k] = await Store.get(k); }
  return { app:'shiori', schemaVersion:SCHEMA_VERSION, exportedAt:Date.now(), data };
}
async function saveBackup(){
  const backup = await buildBackup();
  if(!Object.keys(backup.data).length){ toast('バックアップする記録がありません'); return; }
  const json = JSON.stringify(backup);
  const fname = `fumi_backup_${fmtKey(new Date())}.json`;
  try{
    const blob=new Blob([json],{type:'application/json'});
    const url=URL.createObjectURL(blob);
    const a=document.createElement('a'); a.href=url; a.download=fname;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
    toast('バックアップを保存しました');
  }catch(e){
    try{ await navigator.clipboard.writeText(json); toast('保存できない環境のためコピーしました'); }
    catch(e2){ toast('バックアップに失敗しました'); }
  }
}

// 日ごとの記録を非破壊にマージ：呟きは id で重複を除いて合算、振り返りは新しい方を採用。
function mergeDay(a, b){
  const ids=new Set((a.murmurs||[]).map(m=>m.id));
  const murmurs=[...(a.murmurs||[])];
  for(const m of (b.murmurs||[])){ if(!ids.has(m.id)){ murmurs.push(m); ids.add(m.id); } }
  murmurs.sort((x,y)=>x.ts-y.ts);
  let reflection=a.reflection||null;
  if(b.reflection){
    if(!reflection || (b.reflection.savedAt||0)>(reflection.savedAt||0)) reflection=b.reflection;
  }
  // あしたへの伝言も新しい方を採用
  let toTomorrow=a.toTomorrow||null;
  if(b.toTomorrow){
    if(!toTomorrow || (b.toTomorrow.ts||0)>(toTomorrow.ts||0)) toTomorrow=b.toTomorrow;
  }
  const out={murmurs, reflection};
  if(toTomorrow) out.toTomorrow=toTomorrow;
  return out;
}

/* ============ 旧アプリ（感情記録版）バックアップの取り込み ============
   version/messages 形式（app:'shiori' ではない別アプリの書き出し）を、現行の
   栞バックアップ形状へ変換する。messages→呟き、dailyReflections→振り返り。
   感情・フラグ・睡眠・歩数（emotions/flags/dailyRecords）は現行アプリに置き場所が
   ないため取り込まない（睡眠・歩数の件数は確認画面で通知する）。変換後は通常の
   復元（マージ／上書き）フローにそのまま載る。id は元の値を残すので、同じファイルを
   再度取り込んでも mergeDay が重複を除く。 */
function isLegacyBackup(obj){
  return !!obj && obj.app!=='shiori' && Array.isArray(obj.messages);
}
const LEGACY_REFLECT_FIELDS=[
  ['wins','よかったこと'],
  ['difficulties','難しかったこと'],
  ['insights','気づき'],
  ['tomorrowFirstAction','明日の最初の一歩'],
  ['summary','まとめ'],
];
function composeLegacyReflection(r){
  const parts=[];
  for(const [key,label] of LEGACY_REFLECT_FIELDS){
    const v=((r&&r[key])||'').trim();
    if(v) parts.push(label+'\n'+v);
  }
  return parts.join('\n\n');
}
function convertLegacyBackup(obj){
  const data={};
  const dayOf=ds=>{ const k=dayKey(ds); if(!data[k]) data[k]={murmurs:[], reflection:null}; return data[k]; };
  // messages → 呟き（日付はタイムスタンプのローカル日で振り分ける）
  let mCount=0;
  (obj.messages||[]).forEach((m,i)=>{
    const text=((m&&m.text)||'').trim();
    if(!text) return;
    const ts=Number(m&&m.timestamp)||Date.now();
    const dt=new Date(ts);
    const time=String(dt.getHours()).padStart(2,'0')+':'+String(dt.getMinutes()).padStart(2,'0');
    const id=(m&&m.id!=null)?String(m.id):('leg'+ts+'-'+i);
    dayOf(fmtKey(dt)).murmurs.push({id, text, ts, time, source:'import'});
    mCount++;
  });
  for(const k of Object.keys(data)) data[k].murmurs.sort((a,b)=>a.ts-b.ts);
  // dailyReflections → 振り返り
  let rCount=0;
  (obj.dailyReflections||[]).forEach(r=>{
    const ds=r&&r.date;
    if(!ds || !/^\d{4}-\d{2}-\d{2}$/.test(ds)) return;
    const text=composeLegacyReflection(r);
    if(!text) return;
    dayOf(ds).reflection={text, savedAt:Number(r.updatedAt)||0, source:'import'};
    rCount++;
  });
  return {
    app:'shiori', schemaVersion:SCHEMA_VERSION, exportedAt:obj.exportedAt||Date.now(),
    data,
    _legacy:{messages:mCount, reflections:rCount, skippedRecords:Array.isArray(obj.dailyRecords)?obj.dailyRecords.length:0}
  };
}

let pendingRestore=null;
async function onRestoreFile(file){
  let obj;
  try{ obj=JSON.parse(await file.text()); }
  catch(e){ toast('JSON を読み取れませんでした'); return; }
  let legacy=null;
  if(isLegacyBackup(obj)){ obj=convertLegacyBackup(obj); legacy=obj._legacy; }
  if(!obj || obj.app!=='shiori' || !obj.data || typeof obj.data!=='object'){
    toast('文のバックアップではないようです'); return;
  }
  if(typeof obj.schemaVersion==='number' && obj.schemaVersion>SCHEMA_VERSION){
    toast('新しいバージョンのバックアップです。アプリを更新してください'); return;
  }
  pendingRestore=obj;
  const keys=Object.keys(obj.data);
  const days=keys.filter(k=>k.startsWith('journal:day:')).length;
  const when=obj.exportedAt?('（'+jpDateShort(fmtKey(new Date(obj.exportedAt)))+' 書き出し）'):'';
  let desc;
  if(legacy){
    desc=`別アプリのバックアップを変換して、${days} 日分（呟き ${legacy.messages} 件・振り返り ${legacy.reflections} 件）${when}を取り込みます。`;
    if(legacy.skippedRecords) desc+=` 睡眠・歩数の記録 ${legacy.skippedRecords} 件は対応する項目がないため取り込みません。`;
  } else {
    desc=`${days} 日分の記録${when}を取り込みます。`;
  }
  document.getElementById('restoreDesc').textContent=desc;
  document.getElementById('confirmOverlay').hidden=false;
}
function hideRestoreConfirm(){ document.getElementById('confirmOverlay').hidden=true; pendingRestore=null; }
async function applyRestore(mode){
  const obj=pendingRestore;
  if(!obj){ hideRestoreConfirm(); return; }
  const data=obj.data;
  if(mode==='overwrite'){
    const existing=(await Store.listAll()).filter(k=>k.startsWith('journal:'));
    for(const k of existing) await Store.del(k);
    for(const k of Object.keys(data)) await Store.set(k, data[k]);
  } else { // merge（非破壊）
    for(const k of Object.keys(data)){
      if(k.startsWith('journal:day:')){
        const cur=await Store.get(k);
        await Store.set(k, cur?mergeDay(cur,data[k]):data[k]);
      } else if(k==='journal:letters'){
        // 伝言は宛先日ごとに新しい方を採用
        const cur=(await Store.get(k))||[];
        const map=new Map(cur.map(l=>[l.to,l]));
        for(const l of (data[k]||[])){
          const e=map.get(l.to);
          if(!e || (l.ts||0)>(e.ts||0)) map.set(l.to,l);
        }
        await Store.set(k,[...map.values()]);
      } else {
        const cur=await Store.get(k);           // 設定・読み解きキャッシュは既存を尊重
        if(cur==null) await Store.set(k, data[k]);
      }
    }
  }
  hideRestoreConfirm();
  closeSheets();
  // 画面を作り直す（伝言は旧形式の吸収も含めて読み直す）
  await loadLetters();
  await absorbToTomorrow();
  await loadSettings();
  setMurmurDay(murmurDay);
  await refreshMeta();
  const active=document.querySelector('.screen.active');
  if(active){
    if(active.id==='screen-history') renderCalendar();
    if(active.id==='screen-reflect'){ renderGathered(); loadReflection(); }
    if(active.id==='screen-utsuroi') renderUtsuroi();
  }
  toast(mode==='overwrite'?'上書きで復元しました':'マージして復元しました');
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
    const text=payload();
    // §6: 共有時もお願いは常にクリップボードへ（共有先がテキストを落としても貼れるように）
    try{ await navigator.clipboard.writeText(text); }catch(e){}
    try{ if(navigator.share){ await navigator.share({text}); } else { toast('まとめてコピーしました'); } }catch(e){}
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
    const sentL=LETTERS.filter(l=>l.from===o.ds && l.text);
    const day=o.day; if(!day.murmurs.length && !day.reflection && !sentL.length) return null;
    const mur=day.murmurs.map(m=>{
      const ech=(m.echoes||[]).map(e=>(e.result?'結果: ':'追伸: ')+e.text).join(' / ');
      return (m.kokoromi?'【こころみ】':'')+m.text+(ech?`（${ech}）`:'');
    }).join(' / ');
    const ref=day.reflection?day.reflection.text:'';
    let s=`${o.date.getMonth()+1}/${o.date.getDate()}(${WD[o.date.getDay()]})`;
    if(mur) s+=` 呟き:${mur}`;
    if(ref) s+=` 振り返り:${ref}`;
    if(sentL.length) s+=' 伝言:'+sentL.map(l=>`（${jpMD(l.to)}へ${l.kokoromi?'・こころみ':''}）${l.text}`+(l.reply&&l.reply.text?`（${l.kokoromi?'結果':'返事'}:${l.reply.text}）`:'')).join(' / ');
    return s;
  }).filter(Boolean).join('\n');
}
// 選択中の角度（'base' のときは null）。削除済みの角度が残っていたら base に戻す。
function currentAngle(){
  if(utsuroiAngle==='base') return null;
  const a=(settings.anglePrompts||[]).find(x=>x.id===utsuroiAngle);
  if(!a) utsuroiAngle='base';
  return a||null;
}
function cacheKey(){
  // 角度ごとに読み解きを別キャッシュにする（きほんは従来キーのまま＝後方互換）
  const a=utsuroiAngle==='base'?'':`:a:${utsuroiAngle}`;
  if(utsuroiPeriod==='custom'){
    const r=customRange();
    return r?`journal:insight:custom:${r.from}:${r.to}${a}`:null;
  }
  return `journal:insight:${utsuroiPeriod}:${todayKey}${a}`;
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
  // 角度が選ばれていればそのプロンプト、きほんは期間ごとの既定プロンプト
  const angle=currentAngle();
  if(angle && !angle.text.trim()){ toast('この角度のプロンプトが空です。設定で書いてください'); return; }
  const promptText = angle ? angle.text
                   : utsuroiPeriod==='week' ? (settings.promptWeek||DEFAULT_PROMPTS.week)
                   : utsuroiPeriod==='month' ? (settings.promptMonth||DEFAULT_PROMPTS.month)
                   : (settings.promptCustom||DEFAULT_PROMPTS.custom);
  openBridge({
    title:label+'の読み解き'+(angle?'（'+angle.name+'）':''),
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
// 「読み解きの角度」セレクトを組み立てる。角度が未登録なら行ごと隠す。
function renderAngleSelect(){
  const row=document.getElementById('uAngleRow'), sel=document.getElementById('uAngle');
  if(!row||!sel) return;
  const list=settings.anglePrompts||[];
  if(!list.length){ row.style.display='none'; utsuroiAngle='base'; return; }
  row.style.display='flex';
  currentAngle();   // 削除済みの角度なら base に戻す
  sel.innerHTML='<option value="base">きほんの読み解き</option>'+
    list.map(a=>`<option value="${a.id}">${escapeHtml(a.name)}</option>`).join('');
  sel.value=utsuroiAngle;
}
async function renderUtsuroi(){
  const seg=document.getElementById('uSeg');
  [...seg.children].forEach(b=>b.classList.toggle('sel',b.dataset.p===utsuroiPeriod));
  document.getElementById('uRange').style.display = utsuroiPeriod==='custom' ? 'flex' : 'none';
  renderAngleSelect();
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
  document.querySelectorAll('.nav button').forEach(b=>{
    const on=b.dataset.screen===name;
    b.classList.toggle('active',on);
    if(on) b.setAttribute('aria-current','page'); else b.removeAttribute('aria-current');
  });
  document.getElementById('screenTitle').textContent=titles[name];
  if(name==='reflect'){ renderGathered(); loadReflection(); }
  if(name==='history'){ renderCalendar(); }
  if(name==='utsuroi'){ renderUtsuroi(); }
  document.querySelector('.screen.active').scrollTop=0;
}

/* ============ sample seed ============ */
async function seedIfEmpty(){
  // 種まきは「初回のみ」。一度でも種まき済み（＝journal:meta あり）なら二度としない。
  // これにより「まっさらに」で消してもサンプルは復活しない。
  const meta=await Store.get('journal:meta');
  if(meta && meta.seeded) return;
  // 旧データ（このフラグ導入前から記録がある人）は種まき済みとみなし、消さない。
  const keys=await Store.listDays();
  if(keys.length>0){ await Store.set('journal:meta',{seeded:true, seededAt:Date.now()}); return; }
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
  await Store.set('journal:meta',{seeded:true, seededAt:Date.now()});
}

/* ============ init ============ */
async function init(){
  await Store.init();
  await seedIfEmpty();
  await loadLetters();
  await absorbToTomorrow();   // 旧形式（day.toTomorrow）を journal:letters へ
  document.getElementById('todayDate').textContent=jpDate(today);
  await loadSettings();
  await renderFeed();
  await refreshMeta();

  // compose input
  const inp=document.getElementById('murmurInput');
  inp.addEventListener('input',()=>{ inp.style.height='auto'; inp.style.height=Math.min(inp.scrollHeight,160)+'px'; updatePostBtn(); });
  document.getElementById('postBtn').onclick=postMurmur;

  // day bar (past-day murmurs)
  // 日付ピッカーを開く／閉じるときの pointer-events 制御を共通化する。
  // 開いた後に auto のままだと、透明の input が日付バー全体を覆って
  // 前後ボタンのタップを奪ってしまうため、閉じたら必ず none に戻す。
  function wirePicker(labelId, pickerId){
    const pk=document.getElementById(pickerId);
    pk.max=todayKey;
    const arm=()=>{ pk.style.pointerEvents='none'; };
    document.getElementById(labelId).onclick=()=>{ pk.style.pointerEvents='auto'; pk.showPicker?pk.showPicker():pk.click(); };
    pk.onchange=e=>{ arm(); if(e.target.value) setMurmurDay(e.target.value); };
    pk.oncancel=arm;   // ピッカーを選ばず閉じたとき
    pk.onblur=arm;     // フォーカスが外れたとき
  }
  document.getElementById('dayPrev').onclick=()=>shiftMurmurDay(-1);
  document.getElementById('dayNext').onclick=()=>shiftMurmurDay(1);
  wirePicker('dayLabel','dayPicker');

  // 振り返り画面の日付バー（呟きと同じ選択日を操作する）
  document.getElementById('dayPrevR').onclick=()=>shiftMurmurDay(-1);
  document.getElementById('dayNextR').onclick=()=>shiftMurmurDay(1);
  wirePicker('dayLabelR','dayPickerR');

  setMurmurDay(todayKey);

  // reflect
  document.getElementById('reflectInput').addEventListener('input',updateSaveBtn);
  document.getElementById('saveReflect').onclick=saveReflection;
  document.getElementById('aiDraftBtn').onclick=draftReflection;

  // 伝言（未来のわたしへ／過去のわたしから）
  const dgIn=document.getElementById('dengonInput');
  dgIn.addEventListener('input',()=>{ dgIn.style.height='auto'; dgIn.style.height=dgIn.scrollHeight+'px'; updateSaveBtn(); });
  // あて先の選択（あした／日付をえらぶ）
  document.getElementById('destTomorrow').onclick=()=>setDengonDest('tomorrow');
  const destDate=document.getElementById('destDate');
  const dMin=new Date(today); dMin.setDate(dMin.getDate()+1);
  destDate.min=fmtKey(dMin);
  document.getElementById('destPick').onclick=()=>{ destDate.showPicker?destDate.showPicker():destDate.click(); };
  destDate.onchange=()=>{ if(destDate.value && destDate.value>todayKey) setDengonDest(destDate.value); };
  // こころみの印
  document.getElementById('destKokoromi').onclick=()=>setDengonKokoromi(!dengonKokoromi);
  // こころみのリレー（つづきを結ぶ）
  document.querySelectorAll('#relayRow .relay-chip').forEach(b=>{
    b.onclick=()=>relayKokoromi(parseInt(b.dataset.d,10));
  });
  document.getElementById('dgClosed').onclick=openDengon;
  document.getElementById('dgClosed').addEventListener('keydown',e=>{ if(e.key===' '||e.key==='Enter'){ e.preventDefault(); openDengon(); } });
  // たたむ→再描画（次の未読の文があれば、続けて水引で届く）
  document.getElementById('dgFold').onclick=()=>renderDengon();
  // 返事（往復書簡）
  document.getElementById('henjiBtn').onclick=openHenjiBox;
  document.getElementById('henjiEditBtn').onclick=openHenjiBox;
  document.getElementById('henjiCancel').onclick=()=>refreshHenji(currentDengonLetter());
  document.getElementById('henjiSave').onclick=saveHenji;
  const hjIn=document.getElementById('henjiInput');
  hjIn.addEventListener('input',()=>{ hjIn.style.height='auto'; hjIn.style.height=hjIn.scrollHeight+'px'; });
  document.getElementById('dgMini').onclick=()=>{ const w=document.getElementById('dengonLetter'); w.classList.remove('folded'); w.classList.add('open'); };

  // nav
  document.querySelectorAll('.nav button').forEach(b=>b.onclick=()=>switchScreen(b.dataset.screen));

  // calendar nav
  document.getElementById('prevMonth').onclick=()=>{ calMonth.setMonth(calMonth.getMonth()-1); renderCalendar(); };
  document.getElementById('nextMonth').onclick=()=>{ calMonth.setMonth(calMonth.getMonth()+1); renderCalendar(); };

  // 左右スワイプで前後の日へ（縦スクロールは邪魔しない）。fn に +1/-1 を渡す。
  // skip が true を返すタッチは無視する（テキスト入力中など）。
  function wireSwipeNav(el, fn, skip){
    let sx=0, sy=0, tracking=false;
    el.addEventListener('touchstart',e=>{
      if(e.touches.length!==1 || (skip&&skip(e))){ tracking=false; return; }
      sx=e.touches[0].clientX; sy=e.touches[0].clientY; tracking=true;
    },{passive:true});
    el.addEventListener('touchend',e=>{
      if(!tracking) return; tracking=false;
      if(skip&&skip(e)) return;
      const t=e.changedTouches[0];
      const dx=t.clientX-sx, dy=t.clientY-sy;
      if(Math.abs(dx)<45 || Math.abs(dx)<Math.abs(dy)*1.4) return;  // 横方向が明確なときだけ
      fn(dx<0?1:-1);   // 左へスワイプ=次の日、右へスワイプ=前の日
    },{passive:true});
  }
  // 詳細シート
  wireSwipeNav(document.getElementById('detailSheet'), shiftDetailDay);
  // 呟き・振り返り画面（同じ選択日を共有）。入力欄の上や、呟きの編集・追伸の
  // 入力中は反応させない（書きかけを守る）。
  const daySwipeSkip=e=>
    !!(e.target.closest && e.target.closest('textarea,input')) ||
    !!document.querySelector('.murmur.editing,.murmur.tsn-editing');
  wireSwipeNav(document.getElementById('screen-murmur'), shiftMurmurDay, daySwipeSkip);
  wireSwipeNav(document.getElementById('screen-reflect'), shiftMurmurDay, daySwipeSkip);

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

  // image viewer
  document.getElementById('imgViewer').onclick=closeImg;

  // settings interactions
  const remTog=document.getElementById('remToggle');
  const toggleRem=async()=>{
    settings.rem=!settings.rem;
    remTog.classList.toggle('on',settings.rem);
    remTog.setAttribute('aria-checked', settings.rem?'true':'false');
    document.getElementById('remTimeRow').style.opacity=settings.rem?'1':'.4';
    await saveSettings();
  };
  remTog.onclick=toggleRem;
  remTog.addEventListener('keydown',e=>{ if(e.key===' '||e.key==='Enter'){ e.preventDefault(); toggleRem(); } });
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
    settings.anglePrompts=DEFAULT_ANGLES.map(a=>({...a}));
    settings.angleSeeded=DEFAULT_ANGLES.map(a=>a.id);
    renderAngleList();
    await saveSettings(); toast('プロンプトを既定に戻しました');
  };

  // 読み解きの角度（プロンプト集）：追加
  document.getElementById('addAngle').onclick=async()=>{
    settings.anglePrompts=settings.anglePrompts||[];
    settings.anglePrompts.push({id:'u'+Date.now(), name:'あたらしい角度', text:''});
    await saveSettings();
    renderAngleList();
    const items=document.querySelectorAll('#angleList .angle-item');
    const last=items[items.length-1];
    if(last){ last.scrollIntoView({block:'center'}); last.querySelector('.pf-name').focus(); }
  };
  // うつろいの角度セレクト
  document.getElementById('uAngle').onchange=e=>{ utsuroiAngle=e.target.value; renderUtsuroi(); };

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

  // backup / restore（JSON・丸ごと）
  document.getElementById('backupBtn').onclick=saveBackup;
  const restoreFile=document.getElementById('restoreFile');
  document.getElementById('restoreBtn').onclick=()=>restoreFile.click();
  restoreFile.onchange=e=>{ const f=e.target.files[0]; e.target.value=''; if(f) onRestoreFile(f); };
  document.getElementById('restoreMerge').onclick=()=>applyRestore('merge');
  document.getElementById('restoreOverwrite').onclick=()=>applyRestore('overwrite');
  document.getElementById('restoreCancel').onclick=hideRestoreConfirm;
  document.getElementById('confirmBackdrop').onclick=hideRestoreConfirm;

  // PWA: インストール導線（初回のみ・控えめ）
  setupInstallHint();
}

/* ============ PWA: install hint（ホーム画面に追加） ============ */
function isStandalone(){
  return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone===true;
}
function setupInstallHint(){
  const hint=document.getElementById('installHint');
  const addBtn=document.getElementById('installAdd');
  const closeBtn=document.getElementById('installClose');
  const textEl=document.getElementById('installHintText');
  const SEEN='shiori:installHintSeen';   // 再表示しないための UI フラグ（記録データではない）
  let deferredPrompt=null, shown=false;

  const dismiss=()=>{ hint.hidden=true; try{ localStorage.setItem(SEEN,'1'); }catch(e){} };
  const seen=()=>{ try{ return !!localStorage.getItem(SEEN); }catch(e){ return false; } };
  closeBtn.onclick=dismiss;

  if(isStandalone() || seen()) return;   // インストール済み or 既に案内済みなら出さない

  // 落ち着いたタイミングで一度だけ、そっと出す
  const showSoon=()=>{ if(shown) return; shown=true; setTimeout(()=>{ if(!isStandalone() && !seen()) hint.hidden=false; }, 4000); };

  // Android / Chrome 系：beforeinstallprompt を捕まえてボタンで実行
  window.addEventListener('beforeinstallprompt',(e)=>{
    e.preventDefault(); deferredPrompt=e;
    addBtn.style.display='';
    textEl.textContent='ホーム画面に追加すると、文をアプリのように開けます。';
    showSoon();
  });
  addBtn.onclick=async()=>{
    if(!deferredPrompt){ dismiss(); return; }
    deferredPrompt.prompt();
    try{ await deferredPrompt.userChoice; }catch(e){}
    deferredPrompt=null; dismiss();
  };

  // iOS Safari：beforeinstallprompt が無いので、共有シート経由の手順をそっと案内
  const ua=navigator.userAgent||'';
  const isIOS=/iphone|ipad|ipod/i.test(ua) || (/(macintosh)/i.test(ua) && 'ontouchend' in document);
  const isSafari=isIOS && !/crios|fxios|edgios/i.test(ua);
  if(isIOS && isSafari){
    addBtn.style.display='none';
    textEl.innerHTML='ホーム画面に追加すると、文をアプリのように開けます。<br>共有 <span aria-hidden="true">⬆︎</span> から「ホーム画面に追加」を選んでください。';
    showSoon();
  }
}

/* ============ 可視ビューポート高さを反映（インストール時の下タブ見切れ対策） ============
   standalone PWA では 100vh/100dvh が実際の可視高さと一致しない端末があり、
   下タブ（position:absolute; bottom:0）が画面外へ押し出されることがある。
   実測の window.innerHeight を --app-h に入れて確実に収める。 */
function setAppHeight(){
  const h = (window.visualViewport && window.visualViewport.height) || window.innerHeight;
  if(h) document.documentElement.style.setProperty('--app-h', Math.round(h) + 'px');
}
setAppHeight();
window.addEventListener('resize', setAppHeight);
window.addEventListener('orientationchange', ()=>setTimeout(setAppHeight, 200));
if(window.visualViewport) window.visualViewport.addEventListener('resize', setAppHeight);

/* ============ 入力中は下タブを畳む（入力欄がキーボードに隠れる対策） ============
   下タブ（.nav）は position:absolute で本文の上に重なる。画面下部の入力欄に
   フォーカスするとキーボードの上に下タブが残り、入力欄を覆ってしまう。
   文字入力欄にフォーカスしている間だけ .kb-open を付けて下タブを隠す。
   日付ピッカーやボタンはキーボードを出さないので対象外。 */
function isTextField(el){
  if(!el) return false;
  if(el.tagName === 'TEXTAREA') return true;
  if(el.tagName === 'INPUT'){
    const skip = ['button','checkbox','radio','file','submit','reset','date','time','color','range'];
    return !skip.includes((el.type||'text').toLowerCase());
  }
  return false;
}
document.addEventListener('focusin', (e)=>{
  if(isTextField(e.target)) document.querySelector('.app').classList.add('kb-open');
});
document.addEventListener('focusout', (e)=>{
  if(isTextField(e.target)) document.querySelector('.app').classList.remove('kb-open');
});

/* ============ PWA: service worker 登録 ============ */
if('serviceWorker' in navigator){
  window.addEventListener('load', ()=>{
    navigator.serviceWorker.register('service-worker.js', {updateViaCache:'none'})
      .then(reg=>console.log('[shiori] SW registered:', reg.scope))
      .catch(err=>console.warn('[shiori] SW register failed:', err));
  });
  // 新しいSWが有効になったら一度だけ再読み込みして、その場で新版に切り替える。
  // （初回インストール時は再読み込みしない）
  let hadController=!!navigator.serviceWorker.controller;
  navigator.serviceWorker.addEventListener('controllerchange',()=>{
    if(!hadController){ hadController=true; return; }
    location.reload();
  });
}

/* ============ 同期（Firebase・任意） ============
   仕組み：保存の正は従来どおり端末の IndexedDB（Store）。Googleログイン中だけ
   - 書き込み：Store.set/del をフックして Firestore（users/{uid}/journal/{key}）へも送る
   - 受け取り：onSnapshot で他端末の変更を受け、Store に書き戻して画面を作り直す
   - 初回ログイン：ローカルとリモートを非破壊マージ（復元マージと同じ考え方）
   未ログインなら一切動かない（完全ローカル）。設定値は公開識別子で秘密ではない。 */
const FIREBASE_CONFIG={
  apiKey:"AIzaSyDkOZKSdQzEDsHeIpLuXf5XQeKsOqyFsgk",
  authDomain:"fumi-862e6.firebaseapp.com",
  projectId:"fumi-862e6",
  storageBucket:"fumi-862e6.firebasestorage.app",
  messagingSenderId:"674804307316",
  appId:"1:674804307316:web:be269cb9637a5c7489c821"
};
let fb=null;                 // {auth, db, authM, fsM, user, unsub}
let applyingRemote=false;    // リモート→ローカル反映中は押し返さない
const _storeSet=Store.set.bind(Store);
const _storeDel=Store.del.bind(Store);
Store.set=async(k,v)=>{ await _storeSet(k,v); syncPush(k,v); };
Store.del=async(k)=>{ await _storeDel(k); syncPush(k,null); };

async function syncPush(k,v){
  if(!fb||!fb.user||applyingRemote||!k.startsWith('journal:')) return;
  try{
    const {fsM,db,user}=fb;
    const ref=fsM.doc(db,'users',user.uid,'journal',k);
    if(v==null) await fsM.deleteDoc(ref);
    else await fsM.setDoc(ref,{v:JSON.parse(JSON.stringify(v)), at:Date.now()});
  }catch(e){ console.warn('[fumi] sync push failed:',k,e); }
}
// 伝言リストのマージ（宛先日ごとに新しい方）。復元マージと同じ規則。
function mergeLettersArr(a,b){
  const map=new Map((a||[]).map(l=>[l.to,l]));
  for(const l of (b||[])){
    const e=map.get(l.to);
    if(!e||(l.ts||0)>(e.ts||0)) map.set(l.to,l);
  }
  return [...map.values()];
}
function mergeKeyValue(k, lv, rv){
  if(lv==null) return rv;
  if(rv==null) return lv;
  if(k.startsWith('journal:day:')) return mergeDay(lv,rv);
  if(k==='journal:letters') return mergeLettersArr(lv,rv);
  return lv;   // 設定・読み解きキャッシュ等はこの端末を優先
}
// 初回ログイン：リモート全件とローカル全件を非破壊マージし、両側へ書き戻す
async function syncFirstMerge(){
  const {fsM,db,user}=fb;
  const snap=await fsM.getDocs(fsM.collection(db,'users',user.uid,'journal'));
  const remote=new Map();
  snap.forEach(d=>{ if(d.id.startsWith('journal:')) remote.set(d.id,(d.data()||{}).v); });
  const localKeys=(await Store.listAll()).filter(k=>k.startsWith('journal:'));
  const keys=new Set([...remote.keys(), ...localKeys]);
  applyingRemote=true;
  try{
    for(const k of keys){
      const mv=mergeKeyValue(k, await Store.get(k), remote.has(k)?remote.get(k):null);
      if(mv!=null) await _storeSet(k,mv);
    }
  } finally { applyingRemote=false; }
  for(const k of keys){
    const v=await Store.get(k);
    if(v!=null) await syncPush(k,v);
  }
  await syncRefreshUI();
}
// 他端末の変更を受けて画面を作り直す
async function syncRefreshUI(){
  await loadLetters();
  await loadSettings();
  await refreshMeta();
  setMurmurDay(murmurDay);
  const act=document.querySelector('.screen.active');
  if(act&&act.id==='screen-history') renderCalendar();
  if(act&&act.id==='screen-utsuroi') renderUtsuroi();
}
function syncWatch(){
  const {fsM,db,user}=fb;
  fb.unsub=fsM.onSnapshot(fsM.collection(db,'users',user.uid,'journal'), async(snap)=>{
    let changed=false;
    for(const ch of snap.docChanges()){
      if(ch.doc.metadata.hasPendingWrites) continue;   // 自分の書き込みのエコーは無視
      const k=ch.doc.id;
      if(!k.startsWith('journal:')) continue;
      applyingRemote=true;
      try{
        if(ch.type==='removed') await _storeDel(k);
        else await _storeSet(k,(ch.doc.data()||{}).v);
      } finally { applyingRemote=false; }
      changed=true;
    }
    if(changed) await syncRefreshUI();
  }, e=>console.warn('[fumi] sync watch error:',e));
}
function updateSyncUI(){
  const st=document.getElementById('syncStatus');
  const dt=document.getElementById('syncDetail');
  const btn=document.getElementById('syncLoginBtn');
  if(!st||!dt||!btn) return;
  if(fb&&fb.user){
    st.textContent='同期中';
    dt.textContent=fb.user.email||'ログイン済み';
    btn.textContent='ログアウト';
  } else {
    st.textContent='未ログイン';
    dt.textContent='この端末のみに保存';
    btn.textContent='Googleでログイン';
  }
}
async function initSync(){
  const btn=document.getElementById('syncLoginBtn');
  const dt=document.getElementById('syncDetail');
  if(dt) dt.textContent='同期の準備中…';
  try{
    const V='10.12.2';
    const [appM,authM,fsM]=await Promise.all([
      import(`https://www.gstatic.com/firebasejs/${V}/firebase-app.js`),
      import(`https://www.gstatic.com/firebasejs/${V}/firebase-auth.js`),
      import(`https://www.gstatic.com/firebasejs/${V}/firebase-firestore.js`)
    ]);
    const app=appM.initializeApp(FIREBASE_CONFIG);
    const auth=authM.getAuth(app);
    auth.languageCode='ja';
    let db;
    try{ db=fsM.initializeFirestore(app,{localCache:fsM.persistentLocalCache()}); }
    catch(e){ db=fsM.getFirestore(app); }   // プライベートモード等で永続キャッシュ不可なら通常モード
    fb={auth, db, authM, fsM, user:null, unsub:null};
    authM.getRedirectResult(auth).catch(e=>{
      console.warn('[fumi] redirect result:',e);
      toast('ログインできませんでした。ブラウザ側の保護機能でブロックされた可能性があります');
    });
    authM.onAuthStateChanged(auth, async(user)=>{
      const wasOut=!(fb&&fb.user);
      fb.user=user||null;
      if(fb.unsub){ fb.unsub(); fb.unsub=null; }
      updateSyncUI();
      if(user){
        try{
          if(wasOut) toast('同期をはじめます…');
          await syncFirstMerge();
          syncWatch();
          toast('同期しました');
        }catch(e){ console.warn('[fumi] sync merge failed:',e); toast('同期に失敗しました'); }
      }
    });
  }catch(e){
    console.warn('[fumi] sync unavailable:',e);
    if(btn){ btn.disabled=true; btn.textContent='同期を準備できません'; }
    if(dt) dt.textContent='読み込みに失敗：'+String(e&&e.message||e).slice(0,80);
  }
}
// インストール版（スタンドアロン表示）かどうか。ポップアップが塞がれやすい環境。
function isStandaloneApp(){
  return (window.matchMedia && matchMedia('(display-mode: standalone)').matches) || navigator.standalone===true;
}
function wireSyncUI(){
  const btn=document.getElementById('syncLoginBtn');
  if(!btn) return;
  btn.onclick=async()=>{
    if(!fb){ toast('同期の準備ができていません。少し待つか、開き直してください'); initSync(); return; }
    if(fb.user){
      await fb.authM.signOut(fb.auth);
      toast('ログアウトしました（記録はこの端末に残ります）');
      return;
    }
    const prov=new fb.authM.GoogleAuthProvider();
    prov.setCustomParameters({prompt:'select_account'});
    try{
      if(isStandaloneApp()){
        // インストール版はポップアップが開けないことが多い → 最初からリダイレクト
        toast('Googleへ移動します…');
        await fb.authM.signInWithRedirect(fb.auth, prov);
      } else {
        try{ await fb.authM.signInWithPopup(fb.auth, prov); }
        catch(e){
          // ポップアップが塞がれたらリダイレクトで再挑戦
          toast('Googleへ移動します…');
          await fb.authM.signInWithRedirect(fb.auth, prov);
        }
      }
    }catch(e2){
      console.warn('[fumi] login failed:',e2);
      toast('ログインできませんでした。ブラウザで開いてお試しください');
    }
  };
}

// ログインボタンの配線と同期の初期化は、アプリ初期化の成否に関わらず必ず走らせる
// （initのどこかで転けても、同期だけは生かす）
wireSyncUI();
init()
  .catch(e=>console.warn('[fumi] init error:',e))
  .finally(()=>{ initSync(); });
