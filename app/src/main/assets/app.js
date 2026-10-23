const $=id=>document.getElementById(id);let fileMode=false;let selectedAudioUri=null;
function update(){$('linkBox').hidden=fileMode;$('fileBox').hidden=!fileMode;$('actionBox').hidden=fileMode;$('sourceLink').classList.toggle('active',!fileMode);$('sourceFile').classList.toggle('active',fileMode);$('start').textContent=fileMode?'Transcrever arquivo':$('mode').value==='video'?'Baixar MP4':'Transcrever e gerar TXT/SRT';$('langBox').hidden=!fileMode&&$('mode').value==='video';}
$('sourceLink').onclick=()=>{fileMode=false;update()};$('sourceFile').onclick=()=>{fileMode=true;update()};$('mode').onchange=update;
function notice(message,isError=false){$('notice').textContent=message;$('notice').className='notice '+(isError?'error':'');}
$('start').onclick=()=>{if(fileMode){if(!selectedAudioUri){notice('Escolha um arquivo de áudio.',true);return;}$('start').disabled=true;resetProgress();$('files').textContent='';$('transcriptBox').hidden=true;AndroidBridge.transcribeLocal(selectedAudioUri,$('lang').value);return;}let url=$('url').value.trim();if(!/^https?:\/\//i.test(url)){notice('Cole um link válido.',true);return;}if(!window.AndroidBridge){notice('Abra pelo aplicativo Android.',true);return;}$('start').disabled=true;resetProgress();$('files').textContent='';$('transcriptBox').hidden=true;notice('Iniciando download...');AndroidBridge.download(url,$('mode').value,$('lang').value);};
let blockStartedAt=0,watchdog=null;
function resetProgress(){clearInterval(watchdog);$('progressBox').hidden=true;$('bar').style.width='0%';$('progressText').textContent='';}
function showProgress(pct,label){$('progressBox').hidden=false;$('bar').style.width=pct+'%';$('progressText').textContent=label;}
window.nativeEvent=(type,message)=>{
if(type==='stage'){notice(message);}
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
if(type==='picked'){selectedAudioUri=message;notice('Áudio selecionado. Pronto para transcrever.');}if(type==='ready'||type==='progress'||type==='warning')notice(message,type==='warning');if(type==='video_saved'){$('files').textContent='MP4 salvo em Downloads/MediaDownloader: '+message;}if(type==='transcript_saved'){$('transcriptFiles').textContent=message;}if(type==='text'){$('transcriptText').value=message;$('transcriptBox').hidden=false;}if(type==='error'||type==='done'){clearInterval(watchdog);$('start').disabled=false;notice(message,type==='error');}};
$('copyText').onclick=()=>{let text=$('transcriptText').value;if(window.AndroidBridge)AndroidBridge.copy(text);else navigator.clipboard?.writeText(text)};
notice('Inicializando bibliotecas Android...');update();




