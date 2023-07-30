## 1. Overview

In this tutorial, we'll introduce Jedis, a client library in Java for Redis. This popular in-memory data structure store can persist on a disk as well. It's driven by a keystore-based data structure to persist data and can be used as a database, cache, message broker, etc.

We'll begin by discussing what Jedis is all about, and what kind of situations it's useful in. Then we'll elaborate on the various data structures, and explain transactions, pipelining, and the publish/subscribe feature. Finally, we'll learn about connection pooling and the Redis Cluster.

## 2. Why Jedis?

Redis lists the most well-known client libraries on its official site. There are multiple alternatives to Jedis, but only two are currently worthy of their recommendation star, lettuce, and Redisson.

These two clients do have some unique features, like thread safety, transparent reconnection handling, and an asynchronous API, all features that Jedis lacks.

However, Jedis is small and considerably faster than the other two. Moreover, it's the Spring Framework developers' client library of choice, and it has the biggest community of all three.


## 3. Maven Dependencies

We'll start by declaring the necessary dependency in the pom.xml:

```
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>4.3.2</version>
</dependency>
```

The latest version of the library is available on this page.

## 4. Redis Installation

Then we'll install and fire up one of the latest versions of Redis. For this tutorial, we're running the latest stable version (3.2.1), but any post 3.x version should be okay.

For more information about Redis for Linux and Macintosh, check out this link; they have very similar basic installation steps. Windows isn't officially supported, and this redis for windows is archived.

Now we can dive in directly, and connect to it from our Java code:

```
Jedis jedis = new Jedis();
```

The default constructor will work just fine unless we started the service on a non-default port or a remote machine, in which case, we can configure it correctly by passing the correct values as parameters into the constructor.

## 5. Redis Data Structures

Most of the native operation commands are supported, and conveniently enough, they normally share the same method name.

### 5.1. Strings

Strings are the most basic kind of Redis value, useful for when we need to persist simple key-value data types:

```
jedis.set("events/city/rome", "32,15,223,828");
String cachedResponse = jedis.get("events/city/rome");
```

The variable cachedResponse will hold the value 32,15,223,828. Coupled with expiration support, which we'll discuss later, it can work as a lightning-fast and simple-to-use cache layer for HTTP requests received at our web application, along with other caching requirements.

### 5.2. Lists

Redis Lists are simply lists of strings sorted by insertion order. This makes them an ideal tool to implement message queues, for instance:


```
jedis.lpush("queue#tasks", "firstTask");
jedis.lpush("queue#tasks", "secondTask");

String task = jedis.rpop("queue#tasks");
```

The variable task will hold the value firstTask. Remember that we can serialize any object and persist it as a string, so messages in the queue can carry more complex data when required.

### 5.3. Sets

Redis Sets are an unordered collection of Strings that come in handy when we want to exclude repeated members:

```
jedis.sadd("nicknames", "nickname#1");
jedis.sadd("nicknames", "nickname#2");
jedis.sadd("nicknames", "nickname#1");

Set<String> nicknames = jedis.smembers("nicknames");
boolean exists = jedis.sismember("nicknames", "nickname#1");
```

The Java Set nicknames will have a size of 2, as the second addition of nickname#1 was ignored. Also, the exists variable will have a value of true. The method sismember enables us to check for the existence of a particular member quickly.

5.4. Hashes

Redis Hashes are mapping between String fields and String values:

```
jedis.hset("user#1", "name", "Peter");
jedis.hset("user#1", "job", "politician");

String name = jedis.hget("user#1", "name");

Map<String, String> fields = jedis.hgetAll("user#1");
String job = fields.get("job");
```

As we can see, hashes are a very convenient data type when we want to access an object's properties individually, since we don't need to retrieve the whole object.

### 5.5. Sorted Sets

Sorted Sets are like a Set, where each member has an associated ranking that's used for sorting them:

```
Map<String, Double> scores = new HashMap<>();

scores.put("PlayerOne", 3000.0);
scores.put("PlayerTwo", 1500.0);
scores.put("PlayerThree", 8200.0);

scores.entrySet().forEach(playerScore -> {
jedis.zadd(key, playerScore.getValue(), playerScore.getKey());
});

String player = jedis.zrevrange("ranking", 0, 1).iterator().next();
long rank = jedis.zrevrank("ranking", "PlayerOne");
```

The variable player will hold the value PlayerThree because we're retrieving the top 1 player and he's the one with the highest score. The rank variable will have a value of 1 because PlayerOne is second in the ranking and the ranking is zero-based.

## 6. Transactions

Transactions guarantee atomicity and thread safety operations, which means that requests from other clients will never be handled concurrently during Redis transactions:

```
String friendsPrefix = "friends#";
String userOneId = "4352523";
String userTwoId = "5552321";

Transaction t = jedis.multi();
t.sadd(friendsPrefix + userOneId, userTwoId);
t.sadd(friendsPrefix + userTwoId, userOneId);
t.exec();
```

We can even make a transaction's success dependent on a specific key by “watching” it right before we instantiate our Transaction:

```
jedis.watch("friends#deleted#" + userOneId);
```

If the value of that key changes before the transaction is executed, the transaction won't be completed successfully.

## 7. Pipelining

When we have to send multiple commands, we can pack them together in one request and save connection overhead by using pipelines. It's essentially a network optimization. As long as the operations are mutually independent, we can take advantage of this technique:

```
String userOneId = "4352523";
String userTwoId = "4849888";

Pipeline p = jedis.pipelined();
p.sadd("searched#" + userOneId, "paris");
p.zadd("ranking", 126, userOneId);
p.zadd("ranking", 325, userTwoId);
Response<Boolean> pipeExists = p.sismember("searched#" + userOneId, "paris");
Response<Set<String>> pipeRanking = p.zrange("ranking", 0, -1);
p.sync();

String exists = pipeExists.get();
Set<String> ranking = pipeRanking.get();
```

Notice we don't get direct access to the command responses. Instead, we're given a Response instance from which we can request the underlying response after the pipeline has been synced.

## 8. Publish/Subscribe

We can use the Redis messaging broker functionality to send messages between the different components of our system. We just need to make sure the subscriber and publisher threads don't share the same Jedis connection.

### 8.1. Subscriber

We can subscribe and listen to messages sent to a channel:

```
Jedis jSubscriber = new Jedis();
jSubscriber.subscribe(new JedisPubSub() {
@Override
public void onMessage(String channel, String message) {
// handle message
}
}, "channel");
```

Subscribe is a blocking method; we'll need to unsubscribe from the JedisPubSub explicitly. Here we've overridden the onMessage method, but there are many more useful methods available to override.

### 8.2. Publisher

Then we can simply send messages to that same channel from the publisher's thread:

```
Jedis jPublisher = new Jedis();
jPublisher.publish("channel", "test message");
```

## 9. Connection Pooling

It's important to know that the way we've been dealing with our Jedis instance is naive. In a real-world scenario, we don't want to use a single instance in a multi-threaded environment, as a single instance isn't thread-safe.

Luckily enough, we can easily create a pool of connections to Redis for us to reuse on demand. This pool is thread-safe and reliable, as long as we return the resource to the pool when we're done with it.

Let's create the JedisPool:
```
final JedisPoolConfig poolConfig = buildPoolConfig();
JedisPool jedisPool = new JedisPool(poolConfig, "localhost");

private JedisPoolConfig buildPoolConfig() {
final JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(128);
poolConfig.setMaxIdle(128);
poolConfig.setMinIdle(16);
poolConfig.setTestOnBorrow(true);
poolConfig.setTestOnReturn(true);
poolConfig.setTestWhileIdle(true);
poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
poolConfig.setNumTestsPerEvictionRun(3);
poolConfig.setBlockWhenExhausted(true);
return poolConfig;
}
```

Since the pool instance is thread-safe, we can store it somewhere statically, but we should take care of destroying the pool to avoid leaks when the application is shut down.

Now we can make use of our pool from anywhere in the application when needed:
```
try (Jedis jedis = jedisPool.getResource()) {
// do operations with jedis resource
}
```

We used the Java try-with-resources statement to avoid having to manually close the Jedis resource, but if we can't use this statement, we can also close the resource manually in the finally clause.

It's important to use a pool like we've described in our application if we don't want to face nasty multi-threading issues. We can also play with the pool configuration parameters to adapt it to the best setup for our system.

### 10. Redis Cluster

This Redis implementation provides easy scalability and high availability. To gain more familiarity with it, we can check out their official specification. We won't cover the Redis cluster setup, since that's a bit out of the scope of this article, but we shouldn't have any problems with it when we're done with the documentation.

Once we have it ready, we can start using it from our application:

```
try (JedisCluster jedisCluster = new JedisCluster(new HostAndPort("localhost", 6379))) {
// use the jedisCluster resource as if it was a normal Jedis resource
} catch (IOException e) {}
```

We only need to provide the host and port details from one of our master instances, and it will auto-discover the rest of the instances in the cluster.

This is certainly a very powerful feature, but it's not a silver bullet. When using Redis Cluster, we can't perform transactions or use pipelines, two important features on which many applications rely for ensuring data integrity.

Transactions are disabled because, in a clustered environment, keys will be persisted across multiple instances. Operation atomicity and thread safety can't be guaranteed for operations that involve command execution in different instances.

Some advanced key creation strategies will ensure that the data we want to be persisted in the same instance will get persisted that way. In theory, that should enable us to perform transactions successfully using one of the underlying Jedis instances of the Redis Cluster.

Unfortunately, we can't currently find out which Redis instance a particular key is saved in using Jedis (which is actually supported natively by Redis), so we don't know which of the instances we must perform the transaction operation on. If we want to learn more, more information is available here.

## 11. Conclusion

The vast majority of the features from Redis are already available in Jedis, and its development moves forward at a good pace.

It gives us the ability to integrate a powerful in-memory storage engine into our application with very little hassle. We just can't forget to set up connection pooling to avoid thread safety issues.


Info found on: https://www.baeldung.com/jedis-java-redis-client-library