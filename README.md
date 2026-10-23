# Media Downloader Android v0.2 — tentativa de integração nativa

Esta versão integra **yt-dlp Android** e **FFmpeg Android** para tentar baixar YouTube/TikTok no próprio aparelho e salvar em Downloads/MediaDownloader, sem Cloudflare, sem banco e sem senhas.

**Limitações importantes:** não foi possível compilar ou testar aqui; a resolução das dependências e a compatibilidade dos sites precisam ser verificadas pelo Codemagic. A opção vídeo + transcrever tenta extrair legendas existentes e gerar TXT/SRT; **Whisper local para áudio sem legendas e transcrição de arquivos ainda não estão implementados**. Não é um clone completo.

No GitHub, substitua os arquivos antigos pelos deste ZIP (conteúdo da pasta Media_Downloader_Android_Projeto, não a pasta externa). Codemagic usará codemagic.yaml. Caso falhe, envie o log completo para correção.

Bibliotecas configuradas via JitPack: com.github.yausername.youtubedl-android:library:0.18.1 e :ffmpeg:0.18.1. Essas versões não foram validadas neste ambiente.
