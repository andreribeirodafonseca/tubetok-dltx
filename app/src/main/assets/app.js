const $=id=>document.getElementById(id);
let fileMode=false;
function update(){ $('linkBox').hidden=fileMode;$('fileBox').hidden=!fileMode;$('actionBox').hidden=fileMode;$('sourceLink').classList.toggle('active',!fileMode);$('sourceFile').classList.toggle('active',fileMode);$('start').textContent=fileMode?'Transcrever arquivo':$('mode').value==='video'?'Baixar vídeo':'Baixar vídeo + transcrever'; }
$('sourceLink').onclick=()=>{fileMode=false;update()};$('sourceFile').onclick=()=>{fileMode=true;update()};$('mode').onchange=update;
$('start').onclick=()=>{$('notice').textContent='Versão Android em desenvolvimento: o mecanismo nativo de download e Whisper ainda não está integrado. Nenhum arquivo foi processado.';$('notice').className='notice error'};
$('copyText').onclick=()=>{const text=$('transcriptText').value;if(window.AndroidBridge)AndroidBridge.copy(text);else navigator.clipboard?.writeText(text)};
$('notice').textContent='Interface Android pronta para integração do mecanismo nativo de download e transcrição.';update();
