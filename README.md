# eBlocker backend and UI

## Prerequisites

You need Java (≥ 8), Maven and Redis.

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
