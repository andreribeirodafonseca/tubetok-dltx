# Media Downloader Android v1.7 — Build 17

Corrige erro `ffprobe and ffmpeg not found` na transcrição por link removendo o pós-processador `-x` / `--audio-format mp3` do yt-dlp. Em vez disso, tenta baixar a trilha de áudio diretamente (preferência M4A/AAC).

**Limitação importante:** isso corrige a dependência ausente na etapa de download de áudio, mas a biblioteca Whisper ainda precisa aceitar o formato recebido. Se receber WebM/Opus ou AAC não suportado, é necessária conversão para PCM com decodificador Android/FFmpeg integrado, que ainda não foi implementada.

Downloads de MP4 preservados; transcrição Whisper, TXT/SRT e áudio local continuam experimentais. Não houve compilação nem teste Android nesta entrega.
