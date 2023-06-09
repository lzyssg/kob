package com.kob.matchingsystem.service.impl.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class MatchingPool extends Thread{
    private static List<Player> players = new ArrayList<>();//存储用户信息
    private final ReentrantLock lock = new ReentrantLock();//读写可能有冲突 要枷锁
    private static RestTemplate restTemplate;
    private final static String startGameUrl = "http://127.0.0.1:3000/pk/start/game/";

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate){
        MatchingPool.restTemplate=restTemplate;
    }

    public void addPlayer(Integer userId, Integer rating, Integer botId){
        lock.lock();
        try{
            players.add(new Player(userId,rating,botId,0));
        }finally {
            lock.unlock();
        }
    }

    public void removePlayer(Integer userId){
        lock.lock();
        try {
            List<Player> newPlayers = new ArrayList<>();
            for (Player player : players){
                if(!player.getUserId().equals(userId)){//如果要删的玩家id和用户id不匹配，保留 匹配就丢掉
                    newPlayers.add(player);
                }
            }
            players=newPlayers;
        }finally {
            lock.unlock();
        }
    }

    private void increaseWaitingTime(){//将所有玩家的等待时间加1
        for (Player player:players){
            player.setWaitingTime(player.getWaitingTime()+1);
        }
    }

    private boolean checkMatched(Player a,Player b){//判断两名玩家是否匹配
        int ratingDelta = Math.abs(a.getRating() - b.getRating());//两名玩家的分差 1600和1500 下面会等待十秒
        int waittingTime = Math.min(a.getWaitingTime(),b.getWaitingTime());//你情我愿 比如我只等20秒，假设每秒一人分差为1，那么分差不能大于我最小时间所匹配的最大分数 max是有需求就匹配
        return ratingDelta <= waittingTime*10;

    }

    private void sendResult(Player a,Player b){//返回匹配结果
        System.out.println("send result: "+a +" "+ b);
        MultiValueMap<String,String> data = new LinkedMultiValueMap<>();
        data.add("a_id",a.getUserId().toString());
        data.add("a_bot_id",a.getBotId().toString());
        data.add("b_id",b.getUserId().toString());
        data.add("b_bot_id",b.getBotId().toString());
        restTemplate.postForObject(startGameUrl,data,String.class);
    }

    private void matchPlayers(){//尝试匹配所有玩家
        System.out.println("match players: " + players.toString());
        boolean[] used = new boolean[players.size()];
        for (int i=0;i<players.size();i++){
            if(used[i]) continue;
            for (int j=i+1;j<players.size();j++){
                if (used[j]) continue;
                Player a=players.get(i),b=players.get(j);
                if (checkMatched(a,b)){
                    used[i]=used[j]=true;
                    sendResult(a,b);
                    break;
                }
            }
        }

        List<Player> newPlayers = new ArrayList<>();
        for (int i=0;i<players.size();i++){
            if (!used[i]){
                newPlayers.add(players.get(i));
            }
        }
        players=newPlayers;

    }

    @Override
    public void run() {
        while(true){
            try {
                Thread.sleep(1000);
                lock.lock();//千万要带锁 下面两个会读写数据库 和其他线程冲撞
                try {
                    increaseWaitingTime();
                    matchPlayers();
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
