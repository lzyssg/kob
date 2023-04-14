package com.kob.backend.consumer.utils;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class Game extends Thread{
    private final Integer rows;
    private final Integer cols;
    private final Integer inner_walls_count;
    private final int[][] g;
    private final static int[] dx={-1,0,1,0},dy={0,1,0,-1};
    private final Player playerA,playerB;
    private ReentrantLock lock=new ReentrantLock();

    private Integer nextStepA=null;//玩家上下左右的操作
    private Integer nextStepB=null;
    private String status="playing";//playing->finished
    private String loser="";//all:平局 a：a输 b：b输
    private final static String addBotUrl = "http://127.0.0.1:3002/bot/add/";

    public Game(Integer rows,
                Integer cols,
                Integer inner_walls_count,
                Integer idA,
                Bot botA,
                Integer idB,
                Bot botB
    ) {
        this.rows=rows;
        this.cols=cols;
        this.inner_walls_count = inner_walls_count;
        this.g=new int[rows][cols];

        Integer botIdA=-1,botIdB=-1;
        String botCodeA="",botCodeB="";
        if(botA!=null){//在botmapper.selectbyid(-1)中，-1会返回空，如果非空说明是选择了ai
            botIdA = botA.getId();
            botCodeA = botA.getContent();
        }
        if(botB!=null){
            botIdB = botB.getId();
            botCodeB = botB.getContent();
        }
        playerA=new Player(idA,botIdA,botCodeA,rows-2,1,new ArrayList<>());
        playerB=new Player(idB,botIdB,botCodeB,1,cols-2,new ArrayList<>());
    }

    public Player getPlayerA(){
        return playerA;
    }

    public Player getPlayerB(){
        return playerB;
    }

    public void setNextStepA(Integer nextStepA){//这里是对两个变量进行写入操作，而server会读取这两个变量，不能同时读写，所以写入时候加锁
        lock.lock();
        try{
            this.nextStepA=nextStepA;
        }finally {
            lock.unlock();
        }
    }

    public void setNextStepB(Integer nextStepB){
        lock.lock();
        try{
            this.nextStepB=nextStepB;
        }finally {
            lock.unlock();
        }

    }

    public int[][] getG() {
        return g;
    }

    private boolean check_connectivity(int sx,int sy,int tx,int ty){//深度搜索联通两个起点
        if(sx == tx && sy== ty) return true;
        g[sx][sy]=1;

        for(int i=0;i<4;i++){
            int x=sx+dx[i],y=sy+dy[i];
            if(x>=0 && x<this.rows && y>=0 && y<this.cols && g[x][y]==0){//这个点没走过并且符合条件，继续搜索
                if(check_connectivity(x,y,tx,ty)){
                    g[sx][sy]=0;
                    return true;
                }
            }
        }
        g[sx][sy]=0;//走完把标记清0
        return false;//没走完说明此地图是错的
    }

    private boolean draw(){//画地图
        for(int i=0;i<this.rows;i++){
            for(int j=0;j<this.cols;j++){
                g[i][j]=0;
            }
        }

        for(int r=0;r<this.rows;r++){
            g[r][0]=g[r][this.cols-1]=1;//把第0列和最后一列上色
        }
        for(int c=0;c<this.cols;c++){
            g[0][c]=g[this.rows-1][c]=1;//把第0行和最后一行上色
        }

        Random random = new Random();
        for (int i=0;i<this.inner_walls_count/2;i++){//创建随机障碍物
            for (int j=0;j<1000;j++){
                int r=random.nextInt(this.rows);//0-rows-1随机
                int c=random.nextInt(this.cols);

                if(g[r][c]==1 || g[this.rows-1-r][this.cols-1-c] == 1)//已经是障碍物就退出循环
                    continue;
                if(r==this.rows-2 && c==1 || r==1 && c==this.cols-2)//不要覆盖左下右上两个方块
                    continue;

                g[r][c]=g[this.rows-1-r][this.cols-1-c] = 1;//实现左右对称
                break;
            }
        }
        return check_connectivity(this.rows-2,1,1,this.cols-2);
    }

    public void createMap() {
        for (int i=0;i<1000;i++) {
            if(draw())
                break;
        }
    }

    private String getInput(Player player){
        Player me,you;
        if (playerA.getId().equals(player.getId())){
            me = playerA;
            you = playerB;
        }else{
            me=playerB;
            you=playerA;
        }

        return getMapString() + "#" +
                me.getSx() + "#" +
                me.getSy() + "#(" +
                me.getStepsString() + ")#" +
                you.getSx() + "#" +
                you.getSy() + "#(" +
                you.getStepsString() + ")" ;
    }

    private void sendBotCode(Player player){
        if (player.getBotId().equals(-1)) return ;//-1是人来操作
        MultiValueMap<String,String> data = new LinkedMultiValueMap<>();
        data.add("user_id",player.getId().toString());
        data.add("bot_code",player.getBotCode());
        data.add("input",getInput(player));
        WebSocketServer.restTemplate.postForObject(addBotUrl,data,String.class);
    }

    private boolean nextStep(){//等待两名玩家的下一步操作
        try {
            Thread.sleep(200);//前端渲染蛇，1s走五步，下面休眠五秒，应多加0.2秒，不然前端渲染不过来，中间读入的数据会被覆盖
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        sendBotCode(playerA);
        sendBotCode(playerB);

        for (int i=0;i<50;i++){//线程休眠一秒，循环五次休眠五秒 下面100 这里50 循环越多服务器压力越大、但是用户体验好
            try{
                Thread.sleep(100);//用户延迟：每次执行完操作要随机等睡一秒才动 改成100
                lock.lock();
                try{
                    if(nextStepA !=null && nextStepB != null){
                        playerA.getSteps().add(nextStepA);
                        playerB.getSteps().add(nextStepB);
                        return true;
                    }
                }finally {
                    lock.unlock();
                }
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean check_valid(List<Cell> cellsA,List<Cell> cellsB){
        int n=cellsA.size();
        Cell cell=cellsA.get(n-1);//拿到墙对象a的n-1
        if (g[cell.x][cell.y]==1) return false;//四周是墙壁

        for(int i=0;i<n-1;i++){//判断a有没有撞墙
            if (cellsA.get(i).x== cell.x && cellsA.get(i).y==cell.y){
                return false;
            }
        }

        for (int i=0;i<n-1;i++){
            if (cellsB.get(i).x == cell.x && cellsB.get(i).y== cell.y){
                return false;
            }
        }
        return true;

    }

    private void judge(){//判断两名玩家下一步操作是否合法
        List<Cell> cellsA=playerA.getCells();
        List<Cell> cellsB=playerB.getCells();

        boolean validA=check_valid(cellsA,cellsB);
        boolean validB=check_valid(cellsB,cellsA);
        if(!validA || !validB){
            status="finished";
            if(!validA && !validB){
                loser="all";
            }else if(!validA){
                loser="A";
            }else{
                loser="B";
            }
        }
    }

    private void sendAllMessage(String message){
        if (WebSocketServer.users.get(playerA.getId()) != null){
            WebSocketServer.users.get(playerA.getId()).sendMessage(message);
        }
        if (WebSocketServer.users.get(playerB.getId()) != null){
            WebSocketServer.users.get(playerB.getId()).sendMessage(message);
        }

    }

    private void sendMove(){//向两个client传递移动信息
        lock.lock();
        try{
            JSONObject resp=new JSONObject();
            resp.put("event","move");
            resp.put("a_direction",nextStepA);
            resp.put("b_direction",nextStepB);
            sendAllMessage(resp.toJSONString());
            nextStepA=nextStepB=null;//发送消息后清空
        }finally {
            lock.unlock();
        }
    }

    private String getMapString(){
        StringBuilder res = new StringBuilder();
        for(int i=0;i<rows;i++){
            for (int j=0;j<cols;j++){
                res.append(g[i][j]);
            }
        }
        return res.toString();
    }

    private void updateUserRating(Player player,Integer rating){
        User user = WebSocketServer.userMapper.selectById(player.getId());
        user.setRating(rating);
        WebSocketServer.userMapper.updateById(user);
    }

    private void saveToDatabase(){

        Integer ratingA = WebSocketServer.userMapper.selectById(playerA.getId()).getRating();
        Integer ratingB = WebSocketServer.userMapper.selectById(playerB.getId()).getRating();

        if ("A".equals(loser)){
            ratingA-=2;
            ratingB+=5;
        }else if ("B".equals(loser)){
            ratingA+=5;
            ratingB-=2;
        }

        updateUserRating(playerA,ratingA);
        updateUserRating(playerB,ratingB);

        Record record=new Record(
                null,
                playerA.getId(),
                playerA.getSx(),
                playerA.getSy(),
                playerB.getId(),
                playerB.getSx(),
                playerB.getSy(),
                playerA.getStepsString(),
                playerB.getStepsString(),
                getMapString(),
                loser,
                new Date()
        );
        WebSocketServer.recordMapper.insert(record);
    }

    private void sendResult(){//向两个client公布结果
        JSONObject resp = new JSONObject();
        resp.put("event","result");
        resp.put("loser",loser);
        saveToDatabase();
        sendAllMessage(resp.toJSONString());
    }

    @Override
    public void run() {
        for(int i=0;i<1000;i++){
            if(nextStep()){//是否获取了下一步操作
                judge();
                if (status.equals("playing")){
                    sendMove();
                }else{
                    sendResult();
                    break;
                }

        }else{
                status = "finished";
                lock.lock();
                try{
                    if(nextStepA == null && nextStepB==null){
                        loser = "all";
                    }else if(nextStepA==null){
                        loser="A";
                    }else{
                        loser="B";
                    }
                }finally {
                    lock.unlock();
                }
                sendResult();
                break;
                }
            }
    }
}
