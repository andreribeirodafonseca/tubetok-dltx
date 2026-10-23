# Media Downloader Android v1.2 — Build 12

Correção da API de atualização baseada diretamente no código-fonte da biblioteca `io.github.junkfood02.youtubedl-android:library:0.18.1`.

`YoutubeDL.UpdateChannel` é uma classe aninhada de `YoutubeDL`, e o canal estável é disponibilizado como `YoutubeDL.UpdateChannel._STABLE`. A chamada agora usa diretamente `YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._STABLE)`; não usa reflexão.

O aplicativo mostra o resultado e a versão após a atualização. Compilação e funcionamento do TikTok ainda não foram verificados em dispositivo. Whisper local continua não implementado.

Código-fonte verificado: https://github.com/yausername/youtubedl-android/blob/0.18.1/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt
