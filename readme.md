# redex-sm

An improved Tomcat SessionManager that uses Redis via Redisson, with native encryption support

## Motivation

This is an alternative to https://redisson.org/articles/redis-based-tomcat-session-management.html and offers several improvements


## Theory of operation

This project operates much in the way of `after-request` mode of the `memcached-session-manager` project.

This project installs a Tomcat Valve `SessionReplicationValve` into Tomcat's stack.

At the same two Redis listeners are activated, one for session eviction `SessionEvictionListener` (meaning another Tomcat server updated the session), and one for session destruction `SessionDestructionListener` (meaning another server destroyed the session).

When the sessionManager is asked to load a session, it first checks it's locally stored sessions. If it can't find the session, it attempts to retreieve it from Redis.

At the end of the request this valve invokes `ImprovedRedissonSessionManager.requestComplete()`. The manager checks to see if the URI is on the ignore list. If it is not ignored, and a session is active, or a session creation cookie is being sent to the client, it creates a Redission Batch and sends all of the attributes to Redis to be stored as a `Hash`. This batch includes a session eviction event to let other Tomcat servers know they need to evict their in-memory map of the user's session and so they'll be forced to retrieve a fresh copy of the session from Redis.

## Scalability

With sticky sessions and plenty of Tomcat servers, this should scale to hundreds of thousands of users. Eventually you'll hit limits with Redis events eviction/destruction, but that's dependant on your application's usage patterns.

## Major Changes

-  1.0.0
    - Initial Release
    - Batches up Redis operations to be executed as single Redis operation
    - Encryption Support
    - Ignore URI pattern Support

## Important Notes

- Session Attributes that are Java basic types are serialized, **but are not encrypted when stored in Redis**. So don't put the user's password as a session attribute!
- Any other object type is encrypted after being serialized. So the user's `Principal` object is encrypted when stored in Redis

## Application Requirements

- All objects in the object graph of an object being put into the session must implement `Serializable`
    - Therefore, all `@SessionScoped` and `@ViewScoped` beans must implement `Serializable`
- redex-sm **ONLY** supports session tracking mode `cookie`

## HTTP Environment Requirements

- redex-sm has _only_ been tested with Sticky Sessions using a load balancer
    - Theoretically it should work without them, but this is an untested, and an unnecessary complication for no real-world gains

## Usage

### Required Libraries

See the pom.xml. Any library marked as `compile` scope must be present on the classpath at runtime at the same level as redex-sm
    - So if you're putting redex-sm in your `tomcat/lib` directory, you must include the compile dependencies in `tomcat/lib`
    
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
			<lib>org.redisson:redisson-all:3.19.3</lib>
			<lib>com.github.exabrial:redex-sm:1.0.0</lib>
		</libs>
	</configuration>
</plugin>
```

### Tomcat Configuration

- Tell Tomcat you want to use a custom SessionManager
    - To do this, create the following file: `src/main/webapp/META-INF/context.xml`

```
<?xml version="1.0" encoding="UTF-8"?>
<Context>
	<Manager
		className="com.github.exabrial.redexsm.ImprovedRedisSessionManager"
		configPath="${catalina.base}/conf/redisson.conf.yaml"
		ignorePattern="(?:^.*\/javax\.faces\.resource\/.*$)|(?:^.*\.(ico|svg|png|gif|jpg|jpeg|css|js|tts|otf|woff|woff2|eot)$)" />
</Context>
```

- Next, supply the Redisson library with connection information
- Create the following file: `tomcat/conf/redisson.conf.yaml`
    - change `YOUR-APP-NAME-HERE` to your app name

```
singleServerConfig:
  idleConnectionTimeout: 300000
  connectTimeout: 5000
  timeout: 5000
  retryAttempts: 300000
  retryInterval: 1500
  password: "${REDIS_PASSWORD}"
  subscriptionsPerConnection: 16
  clientName: "YOUR-APP-NAME-HERE-${server.hostname}"
  address: "${REDIS_URL}"
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 10
  connectionMinimumIdleSize: 1
  connectionPoolSize: 10
  dnsMonitoringInterval: 300000
threads: 0
nettyThreads: 0
```

### Environment Configuration

You must have three pieces of configuration:

   - A Redis URL: `REDIS_URL` 
       - Use TLS
   - A Redis password: `REDIS_PASSWORD`
   - An encryption key: `REDEX_KEY_PASSWORD` (`redex.keyPassword` System Property)
       - Goto https://random.org and generate 20 mixed case characters

### Example backend Haproxy Configuration

The environment load balancer will insert a `sticky` cookie:

```
cookie sticky insert nocache httponly secure attr "SameSite=Strict"
server apps-1.staging.example.com apps-1.staging.example.com:443 check cookie apps-1 ssl verify required
```

### License notes

- All files in this project are copyrighted 2023 - Jonathan S. Fisher
- All files in this project are licensed under EUPL-1.2
    - This license allows you to safely use this code in closed-source commercial projects, without having to reveal your company's proprietary application code
    - However, note that if you modify/extended redex-sm, and offer online access to apps through a modified/extended redex-sm, it is required by the license that the source code for your redex-sm changeset be made available first before offering said access
    - Again, this does not include your proprietary application source code, just the changeset to redex-sm
