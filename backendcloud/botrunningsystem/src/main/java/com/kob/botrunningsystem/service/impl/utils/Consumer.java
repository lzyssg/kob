package com.kob.botrunningsystem.service.impl.utils;

import com.kob.botrunningsystem.utils.BotInterface;
import com.sun.javaws.jnl.IconDesc;
import org.joor.Reflect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class Consumer extends Thread{
    private Bot bot;
    private static RestTemplate restTemplate;
    private final static String receiveBotMoveUrl = "http://127.0.0.1:3000/pk/receive/bot/move/";

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate){
        Consumer.restTemplate = restTemplate;
    }

    public void startTimeout(long timeout,Bot bot){
        this.bot=bot;
        this.start();//执行下面的run

        try {
            this.join(timeout);//最多等待timeout秒，然后执行join后面的
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            this.interrupt();//终端用户线程
        }

    }

    private String addUid(String code,String uid){//这个函数实现了在某段代码中插入随机uid
        int k = code.indexOf(" implements com.kob.botrunningsystem.utils.BotInterface");//implements前面要带上空格，要和utils文件bot类对应
        return code.substring(0,k)+uid+ code.substring(k);
    }

    @Override
    public void run() {
        UUID uuid = UUID.randomUUID();//随机字符串，放在下面保证每次类名不同(类名相同只编译一次
        String uid = uuid.toString().substring(0,8);//返回前八位

        BotInterface botInterface = Reflect.compile(//后台编译代码的类 再joor依赖中
                "com.kob.botrunningsystem.utils.Bot"+uid,
                addUid(bot.getBotCode(),uid)
        ).create().get();

        Integer direction = botInterface.nextMove(bot.getInput());
        System.out.println("move-direction: "+ bot.getUserId()+ " " + direction);

        System.out.println(botInterface.nextMove(bot.getInput()));

        MultiValueMap<String,String> data = new LinkedMultiValueMap<>();
        data.add("user_id",bot.getUserId().toString());
        data.add("direction",direction.toString());

        restTemplate.postForObject(receiveBotMoveUrl,data,String.class);
    }
}
