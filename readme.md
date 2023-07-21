# redex-sm

An improved Tomcat SessionManager that uses Redis via Jedis, with native encryption support

## Motivation

This project is an alternative to other Redis based Tomcat managers. We feel that Redis is a perfect backend for Tomcat sessions, given the KVP nature of Redis and Sessions.

We've tried other Redis Based Tomcat Managers. We decided to write our own as we weren't super happy with the others.

* memcached-session-manager
    - https://github.com/magro/memcached-session-manager
    - Venerable
    - The OG
    - Simple to use
    - Not really updated anymore, forks contain updates but are scattered
    - No encryption
* Redisson
    - https://redisson.org/articles/redis-based-tomcat-session-management.html
    - Commercially supported
    - Well documented
    - Very complicated
    - Tons of dependencies
    - Tons of threads
    - Does not shade the `redisson-all` jar, so will cause major headaches and classpath conflicts (Jackson, SnakeYaml, Slf4j, others)
    - No encryption


## Theory of operation

This project operates much in the way of `after-request` mode of the `memcached-session-manager` project.

This project installs a Tomcat Valve `SessionReplicationValve` into Tomcat's stack.

At the same two Redis listeners are activated, one for session eviction `SessionEvictionListener` (meaning another Tomcat server updated the session), and one for session destruction `SessionDestructionListener` (meaning another server destroyed the session).

When the sessionManager is asked to load a session, it first checks it's locally stored sessions. If it can't find the session, it attempts to retrieve it from Redis.

At the end of the request this valve invokes `ImprovedRedissonSessionManager.requestComplete()`. The manager checks to see if the URI is on the ignore list. If it is not ignored, and a session is active, or a session creation cookie is being sent to the client, it creates a Redis transaction and sends all of the attributes to Redis to be stored as a `Hash`. This batch includes a session eviction event to let other Tomcat servers know they need to evict their in-memory map of the user's session and so they'll be forced to retrieve a fresh copy of the session from Redis.

## Scalability

With sticky sessions and plenty of Tomcat servers, this should scale to hundreds of thousands of users. Eventually you'll hit limits with Redis events eviction/destruction, but that's dependent on your application's usage patterns.

Right now, all session destruction and cache eviction notices are directed all all nodes in the cluster. This should be fine for most sane workloads. If a workload has very frequent session expiration (10s of thousands of destruction/eviction events per second), we probably need to write an enhancement to only have the managers subscribe to events for sessions they have cached. Let me know if you reach that limit. I'd be very interested to check it out.

## Major Changes

-  1.0.3
    - Minor code improvements
    - Refuse to start if `keyPrefix` config parameter ends up being blank, `null`, or `ROOT`

-  1.0.2
    - Changed connection pool parameters to slow down idle connection eviction (need to make these customizable)

-  1.0.1
    - Initial Release
    - Batches up Redis operations to be executed as single Redis operation
    - Encryption Support (for non-basic types)
    - Ignore URI pattern Support

## Important Notes

- Session Attributes that are Java basic types (long, boolean, int, String, etc) are serialized, **but are not encrypted when stored in Redis**. _So don't put the user's password as a session attribute!_
- Any other object type is encrypted after being serialized. So the user's `Principal` object is encrypted when stored in Redis and is encrypted at rest.

## Application Requirements

- All objects in the object graph of an object being put into the session must implement `Serializable`
    - Therefore, all `@SessionScoped` and `@ViewScoped` beans must implement `Serializable`
- redex-sm **ONLY** supports session tracking mode `cookie`

## HTTP Environment Requirements

- redex-sm has _only_ been tested with Sticky Sessions using a load balancer
    - Theoretically it should work without them, but this is an untested, and an unnecessary complication for no real-world gains
- Sticky Sessions do not mean that when a server goes down, the users attached to that server lose their session forever
    - Think of sticky sessions as "session affinity", not "session super glue"
    - If your load balancer notices a server is down, it will route the person to a working server and rebalance the load automatically
    - Hence the term "load balancer"
- This is an important performance optimization :) You will likely see far worse performacnce and your users will experience downtime when you deploy if you don't use sticky sessions!

## Usage

### Required Libraries

See the pom.xml. Any library marked as `compile` scope must be present on the classpath at runtime at the same level as redex-sm
    - So if you're putting redex-sm in your `tomcat/lib` directory, you must include the compile dependencies in `tomcat/lib`

- `org.apache.commons:commons-pool2:2.11.1`
- `org.apache.commons:commons-lang3:3.12.0`
- `redis.clients:jedis:4.4.3`
- `org.slf4j:slf4j-api:1.7.36`

#### Example TomEE build configuration

```
<plugin>
	<groupId>org.apache.tomee.maven</groupId>
	<artifactId>tomee-maven-plugin</artifactId>
	<executions>
		<execution>
			<id>tomee-exec</id>
			<phase>package</phase>
			<goals>
				<goal>exec</goal>
			</goals>
		</execution>
	</executions>
	<configuration>
		<attach>false</attach>
		<tomeeClassifier>plus</tomeeClassifier>
		<tomeeVersion>8.0.15</tomeeVersion>
		<context>${project.artifactId}</context>
		<runtimeWorkingDir>${project.build.finalName}-exec</runtimeWorkingDir>
		<libs>
			<!-- Note TomEE includes commons-lang3 by default; here for completeness -->
			<!-- <lib>org.apache.commons:commons-lang3:3.12.0</lib> -->
			<lib>com.github.exabrial:redex-sm:1.0.3</lib>
			<lib>redis.clients:jedis:4.4.3</lib>
		</libs>
	</configuration>
</plugin>
```

### Tomcat Configuration

- Tell Tomcat you want to use a custom SessionManager
    - To do this, create the following file: `src/main/webapp/META-INF/context.xml`
    - See Tomcat documentation for other ways to configure a session manager on an application level
    - Notice the ignorePattern below ignores a lot of things you may not want it to ignore depending on your use case

```
<?xml version="1.0" encoding="UTF-8"?>
<Context>
	<Manager
		className="com.github.exabrial.redexsm.ImprovedRedisSessionManager"
		redisUrl="${redex.redisUrl}"
		keyPassword="${redex.keyPassword}"
		ignorePattern="(?:^.*\/javax\.faces\.resource\/.*$)|(?:^.*\.(ico|svg|png|gif|jpg|jpeg|css|js|tts|otf|woff|woff2|eot)$)" />
</Context>
```

### Environment Configuration

You must have two pieces of configuration:

- `redisUrl`: A Redis URL
    - example: `rediss://default:aPassword@redis-0.prod.example.com:6380`
    - Best to use TLS
    - See the Jedis documentation for URL format
- `keyPassword`: An encryption key
    - Goto https://random.org and generate 20ish mixed case characters
    - The keyPassword is ran through a KDF with a fixed salt

In the above `context.xml` in lieu of compiling values into the application, we are deferring the configuration to the following system properties so they can be changed at runtime:

* `redex.redisUrl`
* `redex.keyPassword`

This substitution is performed by the Tomcat container itself. Feel free to use any system property you desire. You can further defer these system properties to environment variables by reading the Tomcat documentation on creating a `tomcat/bin/setenv.sh` file and setting these system properties in said script:

Example `setenv.sh`

```
#!/bin/bash
export JAVA_OPTS="$JAVA_OPTS\
 -Dredex.redisUrl=$REDEX_REDIS_URL\
 -Dredex.keyPassword=$REDEX_KEY_PASSWORD\
"
```

#### Other configuration

- `keyPrefix` : Override the keyPrefix. Default is the context name. Used to differentiate Redis entries and events.
- `nodeId`: Override the nodeId. Default is `hostname + keyprefix + a UUID`. This should be unique so the sessionManager can filter out inbound events.
- `ignorePattern`: Compiled to a Java Pattern. If the URL matches the pattern, the session will not be replicated to Redis. It's recommended your static assets match this pattern, but this is also useful for things like REST Apis.

### Example backend Haproxy Configuration

The environment load balancer will insert a `sticky` cookie:

```
retry-on 503 0rtt-rejected conn-failure
option httpchk
cookie sticky insert nocache httponly secure attr "SameSite=Strict"
server apps-1.staging.example.com apps-1.staging.example.com:443 check cookie apps-1 ssl verify required
```

### License and other boring legal notes

- All files in this project are copyrighted
- All files in this project are licensed under EUPL-1.2
    - This license allows you to safely use this code in closed-source commercial projects, without ever having to reveal your company's proprietary application code
    - However: note that if you modify/extend redex-sm, and offer online access to apps through a modified/extended redex-sm, it is required by law that the source code for your redex-sm changeset be made available _first_, before offering said access to your app
    - Again, this does not include your proprietary application source code, just the changeset to redex-sm
- Redis, Apache, Tomcat, Redisson, Jedis, and other names are trademarks; this project is not endorsed by nor affiliated with them
