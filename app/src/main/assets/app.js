const $=id=>document.getElementById(id);let fileMode=false;
function update(){$('linkBox').hidden=fileMode;$('fileBox').hidden=!fileMode;$('actionBox').hidden=fileMode;$('sourceLink').classList.toggle('active',!fileMode);$('sourceFile').classList.toggle('active',fileMode);$('start').textContent=fileMode?'Transcrever arquivo':$('mode').value==='video'?'Baixar vídeo':'Baixar vídeo + transcrever';$('langBox').hidden=!fileMode&&$('mode').value==='video';}
$('sourceLink').onclick=()=>{fileMode=false;update()};$('sourceFile').onclick=()=>{fileMode=true;update()};$('mode').onchange=update;
function notice(message,isError=false){$('notice').textContent=message;$('notice').className='notice '+(isError?'error':'');}
$('start').onclick=()=>{if(fileMode){notice('A transcrição local de arquivos por Whisper ainda não está integrada nesta versão.',true);return;}let url=$('url').value.trim();if(!/^https?:\/\//i.test(url)){notice('Cole um link válido.',true);return;}if(!window.AndroidBridge){notice('Abra pelo aplicativo Android.',true);return;}$('start').disabled=true;$('files').textContent='';$('transcriptBox').hidden=true;notice('Iniciando download...');AndroidBridge.download(url,$('mode').value,$('lang').value);};
window.nativeEvent=(type,message)=>{if(type==='ready'||type==='progress'||type==='warning')notice(message,type==='warning');if(type==='saved'){$('files').textContent='Vídeo salvo em Downloads/MediaDownloader: '+message;}if(type==='text'){$('transcriptText').value=message;$('transcriptBox').hidden=false;}if(type==='error'||type==='done'){$('start').disabled=false;notice(message,type==='error');}};
$('copyText').onclick=()=>{let text=$('transcriptText').value;if(window.AndroidBridge)AndroidBridge.copy(text);else navigator.clipboard?.writeText(text)};
notice('Inicializando bibliotecas Android...');update();

$("updateEngine").onclick=()=>{if(window.AndroidBridge){notice("Atualizando yt-dlp...");AndroidBridge.updateEngine();}else notice("Abra no Android.",true);};
