package com.kob.backend.consumer;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.config.filter.RestTemplateConfig;
import com.kob.backend.consumer.utils.Game;
import com.kob.backend.consumer.utils.JwtAuthentication;
import com.kob.backend.mapper.BotMapper;
import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/websocket/{token}")  // 注意不要以'/'结尾
public class WebSocketServer {

    final public static ConcurrentHashMap<Integer, WebSocketServer> users=new ConcurrentHashMap<>();//线程安全的哈希表，可以将userid映射到websocket 2.static静态变量，websocket需要对实例每一个成员都可见3.用户信息连接，被外面调用改成public
    private User user;
    private Session session=null;
    public static RecordMapper recordMapper;
    private static BotMapper botMapper;
    public Game game=null;

    public static UserMapper userMapper;//每建立一个连接都是一个实例，一个类有多个实例，不是单例，定义成独一份的变量再通过set注入
    public static RestTemplate restTemplate;//用来在两个springboot间通信
    private final static String addPlayerUrl = "http://127.0.0.1:3001/player/add/";
    private final static String removePlayerurl = "http://127.0.0.1:3001/player/remove/";
    @Autowired
    public void setUserMapper(UserMapper userMapper){
        WebSocketServer.userMapper=userMapper;
    }
    @Autowired
    public void setRecordMapper(RecordMapper recordMapper){
        WebSocketServer.recordMapper=recordMapper;
    }

    @Autowired
    public void setBotMapper(BotMapper botMapper){
        WebSocketServer.botMapper=botMapper;
    }
    @Autowired
    public void setRestTemplate(RestTemplate restTemplate){
        WebSocketServer.restTemplate=restTemplate;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) throws IOException {
        this.session = session;
        System.out.println("connected!");
        Integer userId= JwtAuthentication.getUserId(token);
        this.user = userMapper.selectById(userId);

        if (this.user != null) {
            users.put(userId, this);
        } else {
            this.session.close();
        }

        System.out.println(users);
    }


    @OnClose
    public void onClose() {
        // 关闭链接
        System.out.println("disconnected!");
        if(this.user !=null){
            users.remove(this.user.getId());
        }
    }

    public static void startGame(Integer aId,Integer aBotId,Integer bId,Integer bBotId){//为了能在外面调用它带上 public static
        User a=userMapper.selectById(aId),b=userMapper.selectById(bId);
        Bot botA = botMapper.selectById(aBotId),botB=botMapper.selectById(bBotId);

        Game game = new Game(13,
                14,
                20,
                a.getId(),
                botA,
                b.getId(),
                botB
        );
        game.createMap();
        if (users.get(a.getId())!=null){
            users.get(a.getId()).game=game;
        }
        if (users.get(b.getId())!=null){
            users.get(b.getId()).game=game;
        }
        game.start();//游戏开启多线程

        JSONObject respGame = new JSONObject();
        respGame.put("a_id",game.getPlayerA().getId());
        respGame.put("a_sx",game.getPlayerA().getSx());
        respGame.put("a_sy",game.getPlayerA().getSy());
        respGame.put("b_id",game.getPlayerB().getId());
        respGame.put("b_sx",game.getPlayerB().getSx());
        respGame.put("b_sy",game.getPlayerB().getSy());
        respGame.put("map",game.getG());

        JSONObject respA= new JSONObject();
        respA.put("event","start-matching");
        respA.put("opponent_username",b.getUsername());
        respA.put("opponent_photo",b.getPhoto());//上面是给a发b的消息
        respA.put("game",respGame);
        if (users.get(a.getId())!=null){
            users.get(a.getId()).sendMessage(respA.toJSONString());//拿到a的链接后给a的前端发送
        }

        JSONObject respB= new JSONObject();
        respB.put("event","start-matching");
        respB.put("opponent_username",a.getUsername());
        respB.put("opponent_photo",a.getPhoto());
        respB.put("game",respGame);
        if (users.get(b.getId())!=null){
            users.get(b.getId()).sendMessage(respB.toJSONString());
        }

    }

    private void startMatching(Integer botId){
        System.out.println("start matching!");
        MultiValueMap<String,String> data=new LinkedMultiValueMap<>();
        data.add("user_id",this.user.getId().toString());
        data.add("rating",this.user.getRating().toString());
        data.add("bot_id",botId.toString());
        restTemplate.postForObject(addPlayerUrl,data,String.class);//向微服务发送请求
    }

    private void stopMatching(){
        System.out.println("stop matching!");
        MultiValueMap<String,String> data = new LinkedMultiValueMap<>();
        data.add("user_id",this.user.getId().toString());
        data.add("rating",this.user.getRating().toString());
        restTemplate.postForObject(removePlayerurl,data,String.class);
    }

    private void move(int direction){
        if (game.getPlayerA().getId().equals(user.getId())){
            if(game.getPlayerA().getBotId().equals(-1)){//亲自出去 如果不是人，就不要接收键盘
                game.setNextStepA(direction);
            }
            game.setNextStepA(direction);
        }else if(game.getPlayerB().getId().equals(user.getId())){
            if(game.getPlayerB().getBotId().equals(-1)){
                game.setNextStepB(direction);
            }

        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {//当作路由
        // 从Client接收消息
        System.out.println("receive message!");
        JSONObject data = JSONObject.parseObject(message);//解析数据
        String event = data.getString("event");
        if("start-matching".equals(event)){
            startMatching(data.getInteger("bot_id"));
        }else if ("stop-matching".equals(event)) {
            stopMatching();
        }else if ("move".equals(event)){
            move(data.getInteger("direction"));
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    public void sendMessage(String message){
        synchronized (this.session){//发送消息要加锁
            try{
                this.session.getBasicRemote().sendText(message);
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}
