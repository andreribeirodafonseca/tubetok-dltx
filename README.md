Media Downloader Android v0.6 — correção de compilação Java.

Alterações: remove import/inicialização FFmpeg com classe inexistente; corrige assinatura YoutubeDL.execute(request, processId, callback com 3 parâmetros).

A biblioteca ffmpeg permanece como dependência Gradle para operações de merge do yt-dlp.

Não compilado/testado em Android neste ambiente. Whisper local ainda não integrado.
