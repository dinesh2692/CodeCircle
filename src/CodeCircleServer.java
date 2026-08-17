import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** CodeCircle realtime backend: HTTP assets + WebSocket rooms/chat/code/WebRTC signaling + Java compiler API. */
public class CodeCircleServer {
  static final int PORT=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
  static final Map<String,Client> clients=new ConcurrentHashMap<>();
  static final Map<String,Set<String>> rooms=new ConcurrentHashMap<>();
  static final Pattern STRING=Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
  static Path publicDir=Paths.get("public").toAbsolutePath().normalize();

  public static void main(String[] args)throws Exception{
    if(args.length>0)publicDir=Paths.get(args[0]).toAbsolutePath().normalize();
    try(ServerSocket server=new ServerSocket(PORT)){
      System.out.println("CodeCircle backend listening on "+PORT);
      while(true){Socket s=server.accept();Thread.ofVirtual().start(()->handle(s));}
    }
  }
  static void handle(Socket s){
    try(s){InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();String req=readLine(in);if(req==null)return;
      Map<String,String> h=new HashMap<>();String l;while((l=readLine(in))!=null&&!l.isEmpty()){int p=l.indexOf(':');if(p>0)h.put(l.substring(0,p).toLowerCase(),l.substring(p+1).trim());}
      if("websocket".equalsIgnoreCase(h.get("upgrade")))websocket(s,in,out,h);else http(req,h,in,out);
    }catch(Exception e){System.err.println("Connection error: "+e);}
  }
  static void http(String req,Map<String,String> h,InputStream in,OutputStream out)throws Exception{
    String[] p=req.split(" ");String uri=p.length>1?p[1].split("\\?")[0]:"/";
    if(uri.equals("/compile")&&"POST".equalsIgnoreCase(p[0])){compile(h,in,out);return;}
    if(!"GET".equalsIgnoreCase(p[0])){writeHttp(out,405,"text/plain","Method not allowed".getBytes());return;}
    if(uri.equals("/"))uri="/index.html";Path f=publicDir.resolve(uri.substring(1)).normalize();
    if(!f.startsWith(publicDir)||!Files.isRegularFile(f)){writeHttp(out,404,"text/plain","Not found".getBytes());return;}
    String t=f.toString().endsWith(".html")?"text/html; charset=utf-8":f.toString().endsWith(".js")?"text/javascript; charset=utf-8":f.toString().endsWith(".css")?"text/css; charset=utf-8":"application/octet-stream";
    writeHttp(out,200,t,Files.readAllBytes(f));
  }
  static void compile(Map<String,String> h,InputStream in,OutputStream out)throws Exception{
    int n=0;try{n=Integer.parseInt(h.getOrDefault("content-length","0"));}catch(Exception ignored){}
    if(n<=0||n>20000){json(out,400,"{\"ok\":false,\"message\":\"Code must be 1-20000 bytes.\"}");return;}
    byte[] b=in.readNBytes(n);String body=new String(b,StandardCharsets.UTF_8);String code=field(body,"code");
    if(code==null){json(out,400,"{\"ok\":false,\"message\":\"Missing code.\"}");return;}
    Path dir=Files.createTempDirectory("codecircle-");Path java=dir.resolve("Main.java");Files.writeString(java,code,StandardCharsets.UTF_8);
    Process q=null;String output="";boolean timed=false;try{
      q=new ProcessBuilder("javac","-encoding","UTF-8","-Xlint:none","Main.java").directory(dir.toFile()).redirectErrorStream(true).start();
      if(!q.waitFor(6,TimeUnit.SECONDS)){timed=true;q.destroyForcibly();q.waitFor(1,TimeUnit.SECONDS);}output=new String(q.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
      boolean ok=!timed&&q.exitValue()==0;String msg=ok?"Compilation successful":"Compilation failed";
      json(out,200,"{\"ok\":"+ok+",\"timedOut\":"+timed+",\"message\":\""+escape(msg)+"\",\"output\":\""+escape(output.length()>10000?output.substring(0,10000):output)+"\"}");
    }finally{deleteTree(dir);}
  }
  static void json(OutputStream out,int status,String s)throws IOException{writeHttp(out,status,"application/json; charset=utf-8",s.getBytes(StandardCharsets.UTF_8));}
  static void writeHttp(OutputStream out,int status,String type,byte[] body)throws IOException{String text="HTTP/1.1 "+status+" "+(status==200?"OK":status==400?"Bad Request":status==404?"Not Found":"Error")+"\r\nContent-Type: "+type+"\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";out.write(text.getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
  static void websocket(Socket s,InputStream in,OutputStream out,Map<String,String> h)throws Exception{
    String key=h.get("sec-websocket-key");if(key==null)return;String a=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
    out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+a+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
    Client c=new Client(UUID.randomUUID().toString(),s,out);try{while(!s.isClosed()){String m=readFrame(in,out);if(m==null)break;dispatch(c,m);}}finally{leave(c);}
  }
  static void dispatch(Client c,String j)throws IOException{
    String type=field(j,"type");if(type==null)return;
    if(type.equals("join")&&c.room==null){String r=safeCode(field(j,"room"));String n=safeName(field(j,"name"));if(r.length()!=6){c.send("{\"type\":\"error\",\"message\":\"Room code must be 6 characters.\"}");return;}Set<String> m=rooms.computeIfAbsent(r,x->ConcurrentHashMap.newKeySet());StringBuilder old=new StringBuilder();for(String id:m){Client p=clients.get(id);if(p!=null){if(old.length()>0)old.append(',');old.append("{\"id\":\"").append(p.id).append("\",\"name\":\"").append(escape(p.name)).append("\"}");}}c.room=r;c.name=n;clients.put(c.id,c);m.add(c.id);c.send("{\"type\":\"room-ready\",\"id\":\""+c.id+"\",\"room\":\""+r+"\",\"users\":["+old+"]}");broadcast(r,"{\"type\":\"user-joined\",\"id\":\""+c.id+"\",\"name\":\""+escape(n)+"\"}",c.id);return;}
    if(c.room==null)return;
    if(type.equals("chat")){String t=safeText(field(j,"text"));if(!t.isBlank())broadcast(c.room,"{\"type\":\"chat\",\"name\":\""+escape(c.name)+"\",\"text\":\""+escape(t)+"\"}",null);}
    else if(type.equals("code")){String t=safeCodeText(field(j,"text"));broadcast(c.room,"{\"type\":\"code\",\"text\":\""+escape(t)+"\"}",c.id);}
    else if(type.equals("signal")){String to=field(j,"to");Client target=clients.get(to);int i=j.indexOf("\"data\"");if(target!=null&&c.room.equals(target.room)&&i>=0){int colon=j.indexOf(':',i);String data=j.substring(colon+1,j.lastIndexOf('}')).trim();target.send("{\"type\":\"signal\",\"from\":\""+c.id+"\",\"name\":\""+escape(c.name)+"\",\"data\":"+data+"}");}}
  }
  static void leave(Client c){if(c.room==null)return;Set<String>s=rooms.get(c.room);if(s!=null){s.remove(c.id);if(s.isEmpty())rooms.remove(c.room);}clients.remove(c.id);try{broadcast(c.room,"{\"type\":\"user-left\",\"id\":\""+c.id+"\"}",null);}catch(Exception ignored){}c.room=null;}
  static void broadcast(String r,String m,String ex)throws IOException{for(String id:rooms.getOrDefault(r,Set.of()))if(!id.equals(ex)){Client p=clients.get(id);if(p!=null)p.send(m);}}
  static String field(String j,String k){Matcher m=STRING.matcher(j);while(m.find())if(m.group(1).equals(k))return unescape(m.group(2));return null;}
  static String safeCode(String v){if(v==null)return "";String s=v.toUpperCase().replaceAll("[^A-Z0-9]","");return s.substring(0,Math.min(6,s.length()));}
  static String safeName(String v){return v==null||v.isBlank()?"Coder":v.substring(0,Math.min(32,v.length()));}
  static String safeText(String v){return v==null?"":v.substring(0,Math.min(600,v.length()));}
  static String safeCodeText(String v){return v==null?"":v.substring(0,Math.min(12000,v.length()));}
  static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
  static String unescape(String s){return s.replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\");}
  static String readLine(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int x;while((x=in.read())!=-1){if(x=='\n')break;if(x!='\r')b.write(x);}return x==-1&&b.size()==0?null:b.toString(StandardCharsets.ISO_8859_1);}
  static String readFrame(InputStream in,OutputStream out)throws IOException{int a=in.read(),b=in.read();if(a<0||b<0)return null;int op=a&15;long n=b&127;if(n==126){n=((in.read()&255)<<8)|(in.read()&255);}else if(n==127)return null;boolean masked=(b&128)!=0;byte[] mask=masked?in.readNBytes(4):null;byte[] d=in.readNBytes((int)n);if(d.length!=n)return null;if(masked)for(int i=0;i<d.length;i++)d[i]^=mask[i%4];if(op==8)return null;if(op==9){out.write(new byte[]{(byte)0x8A,(byte)d.length});out.write(d);out.flush();return "";}return op==1?new String(d,StandardCharsets.UTF_8):"";}
  static void deleteTree(Path p){try{if(Files.exists(p))try(var s=Files.walk(p)){s.sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}}catch(Exception ignored){}}
  static class Client{final String id;final Socket socket;final OutputStream out;String room,name;Client(String i,Socket s,OutputStream o){id=i;socket=s;out=o;}synchronized void send(String s)throws IOException{byte[]d=s.getBytes(StandardCharsets.UTF_8);if(d.length<126){out.write(new byte[]{(byte)129,(byte)d.length});}else if(d.length<65536){out.write(new byte[]{(byte)129,126,(byte)(d.length>>>8),(byte)d.length});}else throw new IOException("Frame too large");out.write(d);out.flush();}}
}
