package com.kob.botrunningsystem.service.impl.utils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BotPool extends Thread{
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private final Queue<Bot> bots = new LinkedList<>();

    private void comsume(Bot bot){
        Consumer consumer = new Consumer();
        consumer.startTimeout(2000,bot);//等待两秒 再久掐断
    }

    public void addBot(Integer userId,String botCode,String input){
        lock.lock();
        try {
            bots.add(new Bot(userId,botCode,input));
            condition.signalAll();//唤醒所有线程
        }finally {
            lock.unlock();
        }
    }

    @Override
    public void run(){
        while (true){
            lock.lock();
            if (bots.isEmpty()){
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }else{
                Bot bot = bots.remove();
                lock.unlock();
                comsume(bot);//执行几秒很慢
            }
        }
    }
}
