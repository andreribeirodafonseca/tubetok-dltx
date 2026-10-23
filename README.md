# Media Downloader Android v1.1 — Build 11

Correção pontual do atualizador: `UpdateChannel` na biblioteca youtubedl-android 0.18.1 é uma classe Kotlin, não um enum Java. Agora o app localiza o canal STABLE por campo estático ou Companion, sem assumir que seja enum.

## Compilação
Substitua o conteúdo do repositório pelo conteúdo deste pacote e rode o workflow Codemagic. Não foi possível executar a compilação Android neste ambiente; a correção requer validação no aparelho.

## Limitações
A atualização ainda pode falhar por rede, compatibilidade ou restrições do motor. O erro de extração do TikTok não está comprovadamente resolvido. Whisper local e transcrição de arquivos de áudio ainda não foram implementados.
