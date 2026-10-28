const $=id=>document.getElementById(id);let fileMode=false;let selectedAudioUri=null;
function update(){$('linkBox').hidden=fileMode;$('fileBox').hidden=!fileMode;$('actionBox').hidden=fileMode;$('sourceLink').classList.toggle('active',!fileMode);$('sourceFile').classList.toggle('active',fileMode);$('instagramPanel').hidden=fileMode||!/^https?:\/\/(www\.)?instagram\.com\//i.test($('url').value.trim());$('threadsPanel').hidden=fileMode||!/^https?:\/\/(www\.)?threads\.(com|net)\//i.test($('url').value.trim());$('start').textContent=fileMode?'Transcrever arquivo':$('mode').value==='video'?'Baixar MP4':'Transcrever e gerar';$('langBox').hidden=!fileMode&&$('mode').value==='video';}
$('sourceLink').onclick=()=>{fileMode=false;update()};$('sourceFile').onclick=()=>{fileMode=true;update()};$('mode').onchange=update;
function notice(message,isError=false){$('notice').textContent=message;$('notice').className='notice '+(isError?'error':'');}
$('start').onclick=()=>{if(fileMode){if(!selectedAudioUri){notice('Escolha um arquivo de áudio.',true);return;}$('start').disabled=true;resetProgress();$('files').textContent='';$('transcriptBox').hidden=true;$('translationBox').hidden=true;AndroidBridge.transcribeLocal(selectedAudioUri,$('lang').value);return;}let url=$('url').value.trim();if(!/^https?:\/\//i.test(url)){notice('Cole um link válido.',true);return;}if(!window.AndroidBridge){notice('Abra pelo aplicativo Android.',true);return;}$('start').disabled=true;resetProgress();$('files').textContent='';$('transcriptBox').hidden=true;$('translationBox').hidden=true;notice('Iniciando download...');AndroidBridge.download(url,$('mode').value,$('lang').value);};
let blockStartedAt=0,watchdog=null;
function resetProgress(){clearInterval(watchdog);$('progressBox').hidden=true;$('bar').style.width='0%';$('progressText').textContent='';}
function showProgress(pct,label){$('progressBox').hidden=false;$('bar').style.width=pct+'%';$('progressText').textContent=label;}
window.nativeEvent=(type,message)=>{
if(type==='stage'){notice(message);}
if(type==='conversion_progress'){const [pct,elapsed,total]=message.split('|');showProgress(Number(pct)>=0?Number(pct):0,`Convertendo áudio: ${elapsed} / ${total}${Number(pct)>=0?' • '+pct+'%':''}`);}
if(type==='block_start'){
 const [b,n,attempt]=message.split('|').map(Number);blockStartedAt=Date.now();
 clearInterval(watchdog);watchdog=setInterval(()=>{
   const elapsed=Math.floor((Date.now()-blockStartedAt)/1000);
   const completed=Math.round((b-1)*100/n);
   showProgress(completed,`Bloco ${b} de ${n} • ${elapsed}s neste bloco${attempt>1?' • tentativa '+attempt:''}${elapsed>=180?' • processamento demorado, verificando...':''}`);
 },1000);
}
if(type==='chunk_progress'){
 clearInterval(watchdog);const [b,n,pct,seconds]=message.split('|').map(Number);
 showProgress(pct,`${pct}% concluído • ${b} de ${n} blocos • ${Math.floor(seconds/60)}min ${seconds%60}s`);
}
if(type==='partial_text'){$('transcriptText').value=message;$('transcriptBox').hidden=!message;}
if(type==='picked'){selectedAudioUri=message;notice('Áudio selecionado. Pronto para transcrever.');}if(type==='ready'||type==='progress'||type==='warning')notice(message,type==='warning');if(type==='video_saved'){$('files').textContent='Arquivo salvo em Downloads/TubTok: '+message;}if(type==='text'){$('transcriptText').value=message;$('transcriptBox').hidden=false;}if(type==='translation_status'){$('translationStatus').textContent=message;}if(type==='translation_done'){$('translatedText').value=message;$('translationStatus').textContent='Tradução concluída.';$('translateText').disabled=false;}if(type==='translation_error'){$('translationStatus').textContent=message;$('translateText').disabled=false;}if(type==='error'||type==='done'){clearInterval(watchdog);$('start').disabled=false;notice(message,type==='error');}};
$('copyText').onclick=()=>{let text=$('transcriptText').value;if(window.AndroidBridge)AndroidBridge.copy(text);else navigator.clipboard?.writeText(text)};
notice('Inicializando bibliotecas Android...');update();





$('translateText').onclick=()=>{const text=$('transcriptText').value.trim();if(!text){notice('Não há texto para traduzir.',true);return;}if(!window.AndroidBridge){notice('Tradução disponível no aplicativo Android.',true);return;}$('translationBox').hidden=false;$('translatedText').value='';$('translateText').disabled=true;const lang=$('translationRoute').value;$('translationDirection').textContent=lang==='pt'?'(para inglês)':'(para português)';$('translationStatus').textContent='Preparando tradução...';AndroidBridge.translateTranscript(text,lang);};
$('copyTranslation').onclick=()=>{const text=$('translatedText').value;if(window.AndroidBridge)AndroidBridge.copy(text);else navigator.clipboard?.writeText(text);};

function refreshTranslationRoute(){const source=$('translationRoute').value; $('translateText').textContent=source==='pt'?'🌐 Traduzir PT → EN':'🌐 Traduzir EN → PT';$('translationBox').hidden=true;}
$('translationRoute').onchange=refreshTranslationRoute; $('lang').addEventListener('change',()=>{if($('lang').value==='pt'||$('lang').value==='en'){$('translationRoute').value=$('lang').value;refreshTranslationRoute();}}); refreshTranslationRoute();

// Fonte única de versão: metadados do APK, sem números divergentes na interface.
(function(){try{if(window.AndroidBridge && typeof AndroidBridge.versionInfo === "function"){const parts=String(AndroidBridge.versionInfo()).split("|");if(parts.length===2){const h=document.getElementById("headerVersion"),f=document.getElementById("footerVersion");if(h)h.textContent="v"+parts[0];if(f)f.textContent="v"+parts[0]+" • Build "+parts[1];document.title="TubTok v"+parts[0];}}}catch(e){console.warn("Versão nativa indisponível",e);}})();

// Instagram: abas para URLs individuais, sem fingir que um perfil privado foi consultado.
let instagramTab='reels';
function updateInstagram(){
 const url=$('url').value.trim();const isIG=/^https?:\/\/(www\.)?instagram\.com\//i.test(url);
 $('instagramPanel').hidden=fileMode||!isIG;if(!isIG)return;
 const reel=/\/(reel|reels)\//i.test(url),post=/\/p\//i.test(url),story=/\/stories\//i.test(url);
 const profile=/^https?:\/\/(www\.)?instagram\.com\/[^/?#]+\/?(?:[?#].*)?$/i.test(url);
 $('igItems').textContent='';
 if(profile){$('igStatus').textContent='Perfil detectado. A listagem completa de Reels, imagens e Stories ainda não está disponível nesta versão; não é possível afirmar que o perfil esteja vazio ou privado.';return;}
 const matches=instagramTab==='reels'?(reel||post):instagramTab==='images'?post:story;
 $('igStatus').textContent=matches?'Publicação identificada. Toque em Baixar para tentar obter a mídia disponível.':'Nenhum item desta categoria identificado no link informado. Isso não indica que o perfil esteja vazio.';
 if(matches){const b=document.createElement('button');b.type='button';b.textContent='⬇️ Baixar conteúdo deste link';b.onclick=()=>{if(!window.AndroidBridge){notice('Abra no Android.',true);return;}$('start').disabled=true;notice('Consultando publicação...');AndroidBridge.download(url,'video','all');};$('igItems').appendChild(b);}
}
$('url').addEventListener('input',updateInstagram);
document.querySelectorAll('[data-ig]').forEach(b=>b.addEventListener('click',()=>{instagramTab=b.dataset.ig;document.querySelectorAll('[data-ig]').forEach(x=>x.classList.toggle('active',x===b));updateInstagram();}));
$('sourceLink').addEventListener('click',updateInstagram);$('sourceFile').addEventListener('click',updateInstagram);

// Pré-visualização consultada pelo Android. Erros não são tratados como perfil vazio.
let inspectTimer=null;
function inspectLink(){const u=$('url').value.trim();if(!/^https?:\/\/(?:www\.)?(instagram\.com|threads\.(?:com|net))\//i.test(u))return;
 const isThreads=/^https?:\/\/(?:www\.)?threads\.(?:com|net)\//i.test(u);const panel=$(isThreads?'threadsPanel':'instagramPanel');panel.hidden=false;$(isThreads?'threadsStatus':'igStatus').textContent='Consultando mídias públicas...';$(isThreads?'threadsItems':'igItems').replaceChildren();
 if(window.AndroidBridge && AndroidBridge.inspectMedia)AndroidBridge.inspectMedia(u);else $('igStatus').textContent='Consulta disponível no aplicativo Android.';
}
$('url').addEventListener('blur',inspectLink);
$('url').addEventListener('input',()=>{clearTimeout(inspectTimer);inspectTimer=setTimeout(inspectLink,1100);});
const previousNativeEvent=window.nativeEvent;
window.nativeEvent=(type,message)=>{
 if(type==='media_error'){const th=/threads\.(com|net)/i.test($('url').value);const panel=$(th?'threadsPanel':'instagramPanel');panel.hidden=false;$(th?'threadsStatus':'igStatus').textContent='Falha na consulta: '+message+' — Isso não significa que a publicação esteja vazia.';return;}
 if(type==='media_result'){
   try{const data=JSON.parse(message);if(data.url!==$('url').value.trim())return;const th=/threads\.(com|net)/i.test(data.url);const panel=$(th?'threadsPanel':'instagramPanel');const status=$(th?'threadsStatus':'igStatus');const items=$(th?'threadsItems':'igItems');panel.hidden=false;items.replaceChildren();const arr=data.items||[];
     status.textContent=(data.notice||'Consulta concluída.')+' • '+arr.length+' prévia(s) encontrada(s).';
     for(const item of arr){const card=document.createElement('div');card.className='media-card';if(item.thumb){const img=document.createElement('img');img.src=item.thumb;img.alt='Miniatura';img.loading='lazy';card.appendChild(img);}const label=document.createElement('span');label.textContent=item.type==='video'?'🎬 Vídeo':item.type==='image'?'🖼️ Imagem':'📸 Publicação';card.appendChild(label);const b=document.createElement('button');b.type='button';b.textContent='⬇ Baixar';b.onclick=()=>{if(AndroidBridge?.download)AndroidBridge.download(item.type==='post'?item.url:item.url,'video','all');};card.appendChild(b);items.appendChild(card);}
   }catch(e){notice('Falha ao interpretar prévias: '+e.message,true);}return;
 }
 previousNativeEvent(type,message);
};
