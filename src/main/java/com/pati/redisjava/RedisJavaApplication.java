package com.pati.redisjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;

@SpringBootApplication
public class RedisJavaApplication {

    public static void main(String[] args) {
//        Jedis jedis = new Jedis("redis", 6379);
        //		jedis.set("events/city/rome", "32,15,223,828");
//		String cachedResponse = jedis.get("events/city/rome");
//		System.out.println(cachedResponse);

//        jedis.lpush("queue#tasks", "firstTask");
//        jedis.lpush("queue#tasks", "secondTask");
//
//        String task = jedis.rpop("queue#tasks");
//        System.out.println("1" + task);

        System.out.println("" + (1==1 ? true : false));

        SpringApplication.run(RedisJavaApplication.class, args);
    }

}
