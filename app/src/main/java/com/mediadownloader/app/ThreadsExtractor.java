package com.mediadownloader.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Public Threads post media discovery; no account access or external services. */
public final class ThreadsExtractor {
  public static final class Media { public final String url, extension; Media(String u,String e){url=u;extension=e;} }
  private static final Pattern CDN=Pattern.compile("https?(?:\\\\/|/){2}[^\\\"\\s<>]+?\\.(?:mp4|jpg|jpeg|png|webp)(?:\\?[^\\\"\\s<>]*)?",Pattern.CASE_INSENSITIVE);
  public static List<Media> resolve(String link) throws Exception {
    URL u=new URL(link);String host=u.getHost().toLowerCase(Locale.ROOT);
    if(!(host.equals("threads.com")||host.endsWith(".threads.com")||host.equals("threads.net")||host.endsWith(".threads.net")))throw new IOException("Endereço não é do Threads");
    HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(18000);c.setReadTimeout(20000);
    c.setRequestProperty("User-Agent","Googlebot/2.1 (+http://www.google.com/bot.html)");c.setRequestProperty("Accept","text/html,application/xhtml+xml");
    int status=c.getResponseCode();if(status!=200)throw new IOException("Threads respondeu HTTP "+status);
    String canonical=c.getURL().toString();String html;
    try(InputStream in=c.getInputStream()){byte[] bytes=readLimited(in,8_000_000);html=new String(bytes,StandardCharsets.UTF_8);}
    String shortcode="";Matcher sm=Pattern.compile("/post/([A-Za-z0-9_-]+)").matcher(canonical);if(sm.find())shortcode=sm.group(1);
    if(shortcode.isEmpty()) {Matcher m=Pattern.compile("/post/([A-Za-z0-9_-]+)").matcher(html);if(m.find())shortcode=m.group(1);}
    // Prefer a JSON record that mentions the requested post to avoid downloading media from replies.
    String decoded=html.replace("\\\\/","/").replace("\\\\u0026","&").replace("&amp;","&");
    LinkedHashMap<String,Media> found=new LinkedHashMap<>();
    Matcher matcher=CDN.matcher(decoded);
    while(matcher.find()){
      String candidate=matcher.group().replace("\\\\/","/");
      int end=candidate.indexOf("\\u003c");if(end>=0)candidate=candidate.substring(0,end);
      String ext=candidate.replaceAll("(?i)^.*\\.(mp4|jpg|jpeg|png|webp)(?:\\?.*)?$","$1").toLowerCase(Locale.ROOT);
      if(!Arrays.asList("mp4","jpg","jpeg","png","webp").contains(ext))continue;
      if(!(candidate.contains("cdninstagram.com")||candidate.contains("fbcdn.net")))continue;
      // When post code is known, prioritize candidates close to its occurrence in page JSON.
      if(!shortcode.isEmpty()){
        int position=matcher.start();int nearest=decoded.lastIndexOf(shortcode,position);
        if(nearest<0 || position-nearest>250000)continue;
      }
      found.putIfAbsent(candidate,new Media(candidate,ext));if(found.size()>=25)break;
    }
    if(found.isEmpty())throw new IOException("Nenhuma mídia pública encontrada no Threads. A publicação pode exigir login ou a estrutura da página mudou.");
    return new ArrayList<>(found.values());
  }
  private static byte[] readLimited(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Página do Threads muito grande");out.write(b,0,n);}return out.toByteArray();}
  public static void download(Media media,File file)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(media.url).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");c.setRequestProperty("Referer","https://www.threads.com/");
    if(c.getResponseCode()!=200)throw new IOException("CDN respondeu HTTP "+c.getResponseCode());
    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  }
}
