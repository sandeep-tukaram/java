# Slimming Down Java Docker Images: A Friendly Guide

Hey there, fellow Docker enthusiast! 👋 Today I want to chat about something that had bothered me in the past - those hefty Java Docker images. You know what I'm talking about, right? You create a simple "Hello World" app and somehow end up with nearly half a TB of Docker image! For the sake perspective, Apple sells an upgrade of 250MB for about INR 20,000. Half a TB, if it were for Apple, would cost one INR 40,000. That big an amount for working on a helloworld program. Crazy!!! Let's fix, optimize as much as possible, that together.

## Our Little Java App

We'll start with this super simple Java program:

```java
package hello1;

public class hello {
    public static void main(String[] args) {
        System.out.println("Greetings World! Let's optimize our java packaging today.");
        System.out.flush();
    }
}
```

Nothing fancy here - just greeting the world and letting everyone know we're on an optimization mission today! That `flush()` call makes sure our message appears immediately in container logs.

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

This works! But... yikes... we're looking at a 350MB+ image. That's like using a moving truck to deliver a postcard. 🚚

## Let's Trim Some Fat: Alpine Version

A quick win is switching to Alpine Linux. It's like the difference between checking a suitcase for a flight versus just bringing a backpack:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app
COPY hello.jar /app/

ENTRYPOINT ["java", "-jar", "hello.jar"]
```

Just like that, we've shaved off about 150MB! Our image is now around 200MB. Not bad for a one-line change, right?

## Why Bring Tools We Don't Need?

Think about it - we're just *running* Java code, not *writing* it. So why bring the whole development kit? Let's switch from JDK to JRE:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY hello.jar /app/

ENTRYPOINT ["java", "-jar", "hello.jar"]
```

Another 30MB gone! We're down to about 170MB now. It's like realizing you don't need to bring your entire toolbox when you just need a screwdriver. 🔧

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

This doesn't reduce our image size, but hey, security matters too!

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

This two-stage approach is like cooking in the kitchen but only bringing the finished dish to the table. We end up with just 127MB - a whopping 64% reduction from where we started! 🎉

## Oops! Things I Learned the Hard Way

This journey wasn't without a few facepalm moments:

* **Compression Confusion**: I initially tried `--compress=9` with jlink (more is better, right?). Turns out it only accepts values 0-2. Whoops!

* **The Silent Container**: Our container ran but didn't say anything! Adding that `System.out.flush()` fixed it. Sometimes containers need a little nudge to be chatty.

* **VM Option Mishap**: I tried adding `--vm=server` to jlink because I thought it would optimize performance, but this option caused the build to fail in the Alpine environment. The server VM isn't compatible with all platforms, especially minimal ones like Alpine.

* **No-Fallback Fiasco**: Another attempt was adding `--no-fallback` to prevent jlink from including extra modules "just in case." Unfortunately, this made our tiny app unable to start because it was being too strict about dependencies. Sometimes being too minimal backfires!

* **The Mystery of Commented Code**: At one point, our print statement was actually commented out in the source code. No wonder it was quiet! Always check the basics first.

## The Before & After

Let's see what we accomplished:

| Approach | Size | What We Saved |
|----------|------|---------------|
| Standard JDK | ~350MB | Our starting point |
| Alpine JDK | ~200MB | 150MB (43% smaller!) |
| Alpine JRE | ~170MB | 180MB (51% smaller!) |
| Custom JRE | ~127MB | 223MB (64% smaller!) |

That's like going from sending an email with a massive attachment to just sending a text message. So much more efficient!

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
* Adding security scanning to catch vulnerabilities
* Exploring distroless containers

What optimization tricks have you discovered? Drop me a comment - I'd love to hear about your container-slimming adventures!

---

*This post was written after a fun afternoon of Docker optimization. Coffee consumption: high. Image size: low. Just how I like it!* ☕

