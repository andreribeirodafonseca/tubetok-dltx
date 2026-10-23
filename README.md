# Media Downloader Android v1.4 — Build 14

Integração real de Whisper on-device via `dev.ffmpegkit-maintained:whisper-android:1.0.0` (whisper.cpp, arm64-v8a).

- Preserva downloader yt-dlp da v1.2 confirmada pelo usuário.
- Dois modos para links: vídeo ou vídeo + transcrição Whisper.
- Arquivo local selecionado pelo Android e transcrito.
- Campo de texto e copiar; TXT/SRT salvos em Downloads/MediaDownloader.
- Modelo multilíngue ggml-base.bin (~142 MB) baixado na primeira utilização, depois roda offline.
- Biblioteca Whisper tem suporte documentado para MP3/WAV/FLAC; OGG e M4A dependem do decoder embutido. 
- Extração de áudio dos links usa yt-dlp + FFmpeg; se a instalação FFmpeg não disponibilizar executável, essa etapa poderá falhar.

**Não foi possível compilar nem testar APK neste ambiente.** Rodar Codemagic e testes no celular. Se falhar, encaminhar log.
Fonte: https://github.com/ffmpegkit-maintained/whisper
