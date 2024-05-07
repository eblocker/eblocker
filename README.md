# eBlocker backend and UI

## Prerequisites

You need Java 11, Maven and Redis.

In the following sections it is assumed that you install everything below a source directory, e.g.

    export SRC=/home/user/src

However, some files must be installed unter `/opt`.

## Dependencies

This project is dependent on these projects:

* eblocker-top
* eblocker-crypto
* eblocker-registration-api
* netty-icap
* restexpress

These can be cloned as follows: https://github.com/eblocker.

    cd $SRC
    git clone https://github.com/eblocker/eblocker-top.git
    git clone https://github.com/eblocker/eblocker-crypto.git
    git clone https://github.com/eblocker/eblocker-registration-api.git
    git clone https://github.com/eblocker/netty-icap.git
    git clone https://github.com/eblocker/RestExpress.git


## Build and test backend and frontend

    cd $SRC
    git clone https://github.com/eblocker/eblocker.git
    cd eblocker
    mvn test

### Known problems
#### eblocker-ui tests fail

If the tests of the eblocker-ui module fail and the log (not at the end) says something like this:

    [launcher]: Starting browser PhantomJS
    [phantomjs.launcher]: Auto configuration failed
    ... Cannot open the shared object file: File or directory not found

Then the following environment variable could be missing in the system:

    OPENSSL_CONF=/etc/ssl/

To solve the problem, the variable can be entered in the /etc/environment file

## Configure backend

Create these directories and make sure you can write to them:

    mkdir /opt/eblocker-icap
    mkdir /opt/eblocker-icap/conf
    mkdir /opt/eblocker-icap/conf/eblocker
    mkdir /opt/eblocker-icap/conf/filter
    mkdir /opt/eblocker-icap/keys
    mkdir /opt/eblocker-icap/network
    mkdir /opt/eblocker-icap/registration
    mkdir /opt/eblocker-icap/tmp
    mkdir /opt/eblocker-network
    mkdir /opt/eblocker-network/bin
    mkdir /opt/eblocker-lists
    mkdir /opt/eblocker-lists/lists

    mkdir /var/cache/eblocker-icap
    mkdir /var/cache/eblocker-icap/domainblacklist
    mkdir /var/cache/eblocker-icap/domainblacklist/lists
    mkdir /var/cache/eblocker-icap/domainblacklist/profiles

Linux: Make sure you can write to them

    chown $USER /opt/eblocker-icap -R
    chown $USER /opt/eblocker-network -R
    chown $USER /opt/eblocker-lists -R

    chown $USER /var/cache/eblocker-icap -R

Create the following configuration file:

    /opt/eblocker-icap/conf/configuration.properties

Content:

    documentRoot=/home/user/src/eblocker/eblocker-ui/build
    redis.snapshot.directory=/home/user/src/redis

    # only if your network interface is not eth0:
    network.interface.name=ethXYZ

    # only necessary on macOS and Linux:
    system.path.cpu.info=/home/user/src/eblocker/eblocker-icapserver/src/test/resources/test-data/cpuinfo.txt

Create the following shell script and make it executable:

    /opt/eblocker-network/bin/script_wrapper

Content:

    #!/bin/sh

    exit 0

Linux: Make it executable

    chmod +x /opt/eblocker-network/bin/script_wrapper

### IPv6

IPV6 must be activated to be able to test the full range of functions locally. Otherwise, exceptions are thrown at startup.

## Start Redis DB

### With docker

    docker run --name redisEblocker -v /home/user/src/redis:/data -d -p 127.0.0.1:6379:6379 redis:latest --save 60 1

Set the key `gateway` to your router's IP address, e.g.

    redis-cli set gateway 192.168.1.1

### Without docker
Create a directory for the redis DB and start the `redis-server` process there:

    mkdir $SRC/redis
    cd $SRC/redis
    redis-server

Set the key `gateway` to your router's IP address, e.g.

    redis-cli set gateway 192.168.1.1

## Start backend

### IDE

Start class `eblocker/eblocker-icapserver/src/main/java/org/eblocker/server/app/EblockerServerApp.java` from your IDE. (A preconfigured runner is available in the project for IntelliJ)

### Maven
You can also start this class with Maven:

    cd $SRC/eblocker/eblocker-icapserver
    mvn exec:exec

Now you should be able to access the settings app at <http://localhost:3000/>.

# Miscellaneous

## Create a new device

Publish a new ARP_IN event:

    redis-cli  PUBLISH arp:in "2/a483e7511cef/172.20.10.8/a483e7511c88/172.20.10.5"

where the first IP is from your local subnet (the second IP does not matter). For even more devices, you need to change the MAC and the first IP.

## Update lists

Fork/clone eblocker-lists

    cd $SRC
    git clone https://github.com/eblocker/eblocker-lists.git

and run

    mvn package -Pupdate-lists -Dmaven.test.skip=true
    
After that, copy the result to your local installation:

    rm -r /opt/eblocker-lists/lists
    cp -r target/package/eblocker-lists/lists /opt/eblocker-lists/
    
## Create SSL errors

You can use the following script to simulate the detection of an SSL error:

    NOW=`date +"%Y/%m/%d %T"`
    if [ -z "$1" ]
      then
        STAMP=`date +%Y-%m-%d-%H-%M-%S`
        DOMAIN="$STAMP-autogen.com"
    else
        DOMAIN=$1
    fi
    /bin/echo "$NOW kid1| eblkr: 20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY log_addr: 172.20.10.6:57608 host: 169.50.46.74 sni: $DOMAIN cert: FOO" >> /var/log/squid/cache.log
    
Either pass a domain name as argument or let the script generate a name based on the current timestamp.

## Reset local development environment

To reset the local development environment up to the point where the activation wizard appears:
 
 1. Stop the icapserver and the Redis server
 1. Remove the Redis DB `$src/redis/dump.rdb`
 1. Remove the file `/opt/eblocker-icap/registration/registration.properties`



