package com.codecircle.ws;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {
  final ObjectMapper json=new ObjectMapper();
  final Map<String,Set<WebSocketSession>> rooms=new ConcurrentHashMap<>();
  final Map<String,String> names=new ConcurrentHashMap<>();

  public void afterConnectionEstablished(WebSocketSession s)throws Exception{
    String room=(String)s.getAttributes().get("room"),name=(String)s.getAttributes().get("name");
    names.put(s.getId(),name);
    Set<WebSocketSession> members=rooms.computeIfAbsent(room,x->ConcurrentHashMap.newKeySet());
    List<Map<String,String>> existing=new ArrayList<>();
    for(WebSocketSession p:members) existing.add(Map.of("id",p.getId(),"name",names.getOrDefault(p.getId(),"Coder")));
    members.add(s);
    s.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","room-ready","id",s.getId(),"room",room,"users",existing))));
    broadcast(room,Map.of("type","user-joined","id",s.getId(),"name",name),s);
  }

  protected void handleTextMessage(WebSocketSession s,TextMessage m)throws Exception{
    String room=(String)s.getAttributes().get("room");
    JsonNode n=json.readTree(m.getPayload());
    String type=n.path("type").asText();
    if(!Set.of("chat","code","signal").contains(type))return;
    if(type.equals("chat")){
      String text=n.path("text").asText("");
      if(text.isBlank()||text.length()>1000)return;
      broadcast(room,Map.of("type","chat","name",names.getOrDefault(s.getId(),"Coder"),"text",text),null);
    }else if(type.equals("code")){
      String text=n.path("text").asText("");
      if(text.length()>50000)return;
      broadcast(room,Map.of("type","code","text",text),s);
    }else{
      String to=n.path("to").asText();
      JsonNode data=n.get("data");
      if(to.isBlank()||data==null)return;
      rooms.getOrDefault(room,Set.of()).stream().filter(x->x.getId().equals(to)).findFirst().ifPresent(t->{
        try{t.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","signal","from",s.getId(),"name",names.getOrDefault(s.getId(),"Coder"),"data",data))));}catch(Exception ignored){}
      });
    }
  }

  public void afterConnectionClosed(WebSocketSession s,CloseStatus st){
    String room=(String)s.getAttributes().get("room");
    Set<WebSocketSession>x=rooms.get(room);
    if(x!=null){x.remove(s);if(x.isEmpty())rooms.remove(room);}
    if(room!=null)broadcast(room,Map.of("type","user-left","id",s.getId()),null);
    names.remove(s.getId());
  }

  void broadcast(String room,Object payload,WebSocketSession except){
    try{String msg=json.writeValueAsString(payload);for(WebSocketSession s:rooms.getOrDefault(room,Set.of()))if(s.isOpen()&&s!=except)s.sendMessage(new TextMessage(msg));}catch(Exception ignored){}
  }
}
