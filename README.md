# eBlocker backend and UI

## Prerequisites

You need Java 11, Maven and Redis.

In the following sections it is assumed that you install everything below a source directory, e.g.

    export SRC=/home/user/src

However, some files must be installed unter `/opt`.

## Dependencies

Build projects:

* eblocker-top
* eblocker-crypto
* eblocker-registration-api
* netty-icap

Clone each of the above projects from https://github.com/eblocker.

    cd $SRC
    git clone https://github.com/eblocker/eblocker-top.git
    git clone https://github.com/eblocker/eblocker-crypto.git
    git clone https://github.com/eblocker/eblocker-registration-api.git
    git clone https://github.com/eblocker/netty-icap.git

In each project:

    mvn install

## Build and test backend and frontend

    cd $SRC
    git clone https://github.com/eblocker/eblocker.git
    cd eblocker
    mvn test

## Configure backend

Create these directories and make sure you can write to them:

    mkdir /opt/eblocker-icap
    mkdir /opt/eblocker-icap/conf
    mkdir /opt/eblocker-icap/keys
    mkdir /opt/eblocker-icap/network
    mkdir /opt/eblocker-icap/registration
    mkdir /opt/eblocker-icap/tmp
    mkdir /opt/eblocker-network
    mkdir /opt/eblocker-network/bin
    mkdir /opt/eblocker-lists
    mkdir /opt/eblocker-lists/lists

Create the following configuration file:

    /opt/eblocker-icap/conf/configuration.properties

Content:

    documentRoot=/home/user/src/eblocker/eblocker-ui/build
    redis.snapshot.directory=/home/user/src/redis

    # only if your network interface is not eth0:
    network.interface.name=ethXYZ

    # only necessary on macOS:
    system.path.cpu.info=/home/user/src/eblocker/eblocker-icapserver/src/test/resources/test-data/cpuinfo.txt

Create the following shell script and make it executable:

    /opt/eblocker-network/bin/script_wrapper

Content:

    #!/bin/sh

    exit 0


## Start Redis DB

Create a directory for the redis DB and start the `redis-server` process there:

    mkdir $SRC/redis
    cd $SRC/redis
    redis-server

Set the key `gateway` to your router's IP address, e.g.

    redis-cli set gateway 192.168.1.1

## Start backend

Start class `eblocker/eblocker-icapserver/src/main/java/org/eblocker/server/app/EblockerServerApp.java` from your IDE.

You can also start this class with Maven:

    cd $SRC/eblocker/eblocker-icapserver
    mvn exec:exec

Now you should be able to access the settings app at <http://localhost:3000/>.

# Miscellaneous

## Create a new device

Publish a new ARP_IN event:

    redis-cli  PUBLISH arp:in "2/a4:83:e7:51:1c:ef/172.20.10.8/a4:83:e7:51:1c:88/172.20.10.5"

where the first IP is from your local subnet (the second IP does not matter). For even more devices, you need to change the MAC and the first IP.

## Update lists

Fork/clone eblocker-lists and run

    mvn package -Pupdate-lists
    
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



