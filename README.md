# TubTok v2.6 — correções do TikTok e diagnóstico

- Removida desde v2.5 a dependência `youtubedl-android:ffmpeg` (confirmado no Gradle).
- Conversão de áudio permanece no Android MediaExtractor/MediaCodec.
- yt-dlp tenta atualização estável na inicialização; se a rede falhar, o aplicativo continua com a versão instalada. Atualização manual continua disponível.
- Erros de TikTok exibem explicação clara; logs completos permanecem no Logcat.
- Avisos WARNING não são tratados como erro por si só; a falha real continua sendo capturada.
- Tela cinematográfica, Whisper e tradução preservados.

**Limitação importante:** alguns vídeos TikTok exigem TLS browser impersonation (`curl_cffi`), que a biblioteca atual não oferece. Nenhum ajuste de mensagens ou atualização simples garante o download desses vídeos. Isso requer uma solução compatível com Android e teste real. Não foi implementado bypass.

**Compilação:** ainda não executada neste ambiente. Testar no Codemagic com um link YouTube e um TikTok público.
