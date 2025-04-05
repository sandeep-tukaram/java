# Slimming Down Java Docker Images: A Friendly Guide

Hey there, fellow Docker enthusiast! 👋 Today I want to chat about something that had bothered me in the past - those hefty Java Docker images. You know what I'm talking about, right? You create a simple "Hello World" app and somehow end up with nearly half a TB of Docker image! To put it in perspective, Apple sells an upgrade of 250MB for about INR 20,000. Half a TB, if it were for Apple, would cost whooping INR 40,000. That's crazy cost to print a helloworld!!! Let's fix that together -  optimize as much as possible. 

- [Jump to the results](#the-before--after)
- [Code](https://github.com/sandeep-tukaram/java)

## Our Little Java App

We'll start with this super simple Java program:

```java
package hello1;

public class hello {
    public static void main(String[] args) {
        System.out.println("Greetings World! Let's optimize our java packaging today.");
    }
}
```

Nothing fancy here - just greeting the world and letting everyone know we're on an optimization mission today!

Let's check the actual size of our compiled app:

```
$ ls -la
total 16
drwxr-xr-x  5 sandeep  staff   160 Apr  5 08:39 .
drwxr-xr-x  3 sandeep  staff    96 Apr  5 00:16 ..
-rw-r--r--@ 1 sandeep  staff    25 Apr  5 00:50 MANIFEST.MF
-rw-r--r--  1 sandeep  staff  1015 Apr  5 08:39 hello.jar
drwxr-xr-x  4 sandeep  staff   128 Apr  5 08:39 hello1

$ ls -lh hello1 
total 16
-rw-r--r--  1 sandeep  staff   262B Apr  5 08:39 hello.class
-rw-r--r--@ 1 sandeep  staff   147B Apr  5 01:03 hello.java
```

Our entire application is only a few hundred bytes. The class file is 262 bytes and the source code is 147 bytes. Here comes the packaging overheads. The jar, native java packaging, makes it 1000 bytes. Still reasonable compared to what follows. This tiny app will end up in a container hundreds of megabytes in size. Talk about overhead! 😮

## The "I Just Need It Working" Approach

Most of us start here - grab a standard Java image and get things running:

```dockerfile
FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY hello.jar /app/

ENTRYPOINT ["java", "-jar", "hello.jar"]
```

Lets build an image

```
$ docker build . -t hello1-java
[+] Building 180.7s (8/8) FINISHED                                                              docker:desktop-linux
...
 => [1/3] FROM docker.io/library/eclipse-temurin:21-jdk@sha256:6634936b2e8d90ee16eeb94420d71cd5e36ca677a4cf795a9ee1ee6e94379988  177.3s

$ docker images 
REPOSITORY    TAG       IMAGE ID       CREATED              SIZE
hello1-java   latest    7104f6490ae9   About a minute ago   704MB

```

Yikes! We're looking at a 704MB+ image and more than 3 minutes of build time. That's like using a moving truck to deliver a postcard.  🚚  

## Trim Some Fat: Alpine Version

A quick win is switching to Alpine Linux. It's like the difference between checking a suitcase for a flight versus just bringing a backpack:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine              

WORKDIR /app
COPY hello.jar /app/

ENTRYPOINT ["java", "-jar", "hello.jar"]
```

The results:

```
$ docker build . -t hello1-java                                  
[+] Building 129.9s (8/8) FINISHED                                                              docker:desktop-linux


$ docker images
REPOSITORY    TAG       IMAGE ID       CREATED         SIZE
hello1-java   latest    c6d19d8c8076   2 minutes ago   550MB

```

Just like that, we've shaved off about 150MB! Our image is now around 550MB. Not bad for a one-line change, right?

## Why Bring Tools We Don't Need?

Think about it - we're just *running* Java code, not *writing* it. So why bring the whole development kit? Let's switch from JDK to JRE:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY hello.jar /app/

ENTRYPOINT ["java", "-jar", "hello.jar"]
```

Drumroll results! 
```
$ docker build . -t hello1-java      
[+] Building 39.2s (8/8) FINISHED                                                             docker:desktop-linux

$ docker images
REPOSITORY    TAG       IMAGE ID       CREATED         SIZE
hello1-java   latest    9c43612a462f   4 seconds ago   282MB

```

Almost half gone - 2X ! We're down to about 282MB now. You don't need to bring your entire toolbox when all that is needed is a screwdriver. 🔧 Great! 

## Safety Break: Let's Not Run as Root

Before we go further with optimizations, let's make our container safer. Running as root in containers is a bit like leaving your car unlocked with the keys inside:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY hello.jar /app/

# Create a regular user
RUN addgroup -S javauser && adduser -S -G javauser javauser && \
    chown -R javauser:javauser /app

# Switch to that user
USER javauser

ENTRYPOINT ["java", "-jar", "hello.jar"]
```

This doesn't reduce our image size and more importantly doesn't add to the image size. But hey, security matters too!

## The Magic Trick: Custom JRE with jlink

Now for my favorite part! What if I told you we could create a custom Java runtime with *just* the parts our app actually needs? That's exactly what `jlink` does:

```dockerfile
# Stage 1: Build our custom JRE
FROM eclipse-temurin:21-jdk-alpine AS builder

# Create a minimal JRE with just what we need
RUN jlink \
    --add-modules java.base \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /customjre

# Stage 2: Create our final slim image
FROM alpine:3.19

# Copy only the custom JRE
COPY --from=builder /customjre /opt/java

# Set up Java environment
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app
COPY hello.jar /app/

# Create a non-root user
RUN addgroup -S javauser && adduser -S -G javauser javauser && \
    chown -R javauser:javauser /app /opt/java

USER javauser

# Run our app
ENTRYPOINT ["java", "-jar", "hello.jar"]
```

Drumrollss please... 
```
$ docker images
REPOSITORY    TAG       IMAGE ID       CREATED         SIZE
hello1-java   latest    ad42d514d4b7   7 seconds ago   132MB


$ docker build . -t hello1-java      
[+] Building 44.9s (13/13) FINISHED                                                   docker:desktop-linux

```

This two-stage approach is like cooking in the kitchen but only bringing the finished dish to the table. We end up with just 132MB - a whopping 5X reduction from where we started! 🎉

## I feel what you feel  
Did we go back to jdk? Yes we did! However, we still managed to end up with a small application image. How did that happen? Magic!

## Oops! Things I Learned the Hard Way
This journey wasn't without a few facepalm moments:

* **Compression Confusion**: I initially tried `--compress=9` with jlink (more is better, right?). Turns out it only accepts values 0-2,  compress=<0,1,2>. Whoops! 

```
 > [builder 2/2] RUN jlink     --add-modules java.base     --strip-debug     --no-man-pages     --no-header-files     --compress=9     --vm=server     --output /customjre:
0.156 Error: Invalid compression level 9
```

* **No-Fallback Fiasco**: Another attempt was adding `--no-fallback` to prevent jlink from including extra modules "just in case." Threw unknown option. Not everything found on internet works. Check command line help or documentation before applying options.

```
 > [builder 2/2] RUN jlink     --add-modules java.base     --strip-debug     --no-man-pages     --no-header-files     --compress=2     --vm=server     --no-fallback     --output /customjre:
0.131 Error: unknown option: --no-fallback
```

* **innocuous and useless **: attmepts adding `--vm=server` ,  "--no-man-pages" didin't result in anything different. I expected the application image to be smaller though.


## The Before & After

Let's see what we accomplished:

| Approach | Size | What We Saved |
|----------|------|---------------|
| Standard JDK | ~700MB | Our starting point |
| Alpine JDK | ~550MB | 20% smaller! |
| Alpine JRE | ~280MB | 60% smaller! |
| Custom JRE | ~130MB | 80% smaller or 5X! |

Finally, from a truck to a two-wheeler to send the postcard. Sounds right!

## Takeaways for Your Own Projects

Here's what I'll remember for next time:

1. **Alpine is your friend** - Almost always a good choice for base images
2. **JRE beats JDK** for runtime - Don't bring tools you don't need
3. **Custom JREs are amazing** - jlink is like magic for trimming size
4. **Security is non-negotiable** - Always run as a non-root user
5. **Be careful with advanced options** - Not all JVM options work everywhere
6. **Check your outputs** - Sometimes the simplest things trip us up

## What's Next on My Optimization Journey?

I'm thinking about:
* Trying GraalVM native images (they can be even tinier!)
* Setting up multi-arch builds (arm64 and amd64)
* Adding security scanning to catch vulnerabilities - is it really safe?
* Exploring distroless containers

What optimization tricks have you discovered? Drop me a comment (github) - I'd love to hear about your container-slimming adventures!

---

*This post was written after a fun afternoon of Docker optimization. Coffee consumption: high. Image size: low. Just how I like it!* ☕

