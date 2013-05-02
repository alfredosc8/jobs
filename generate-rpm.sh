#!/bin/bash

VERSION=$1

## Init.
CURRENT_VERSION=$(echo ${VERSION} | sed 's/\"//g' | sed 's/\-//g' | sed 's/\SNAPSHOT//g')

#cp git/shit/jobs-executor -> jobexec_rpm

mkdir -p jobexec_rpm/jobs-executor
cp jobs-executor/poser jobexec_rpm/jobs-executor
cp jobs-executor/*.sh jobexec_rpm/jobs-executor
cp jobs-executor/README.txt jobexec_rpm/jobs-executor
cp jobs-executor/jobmonitor*.py jobexec_rpm/jobs-executor
cp jobs-executor/lru.py jobexec_rpm/jobs-executor
cp jobs-executor/auto_stub.py jobexec_rpm/jobs-executor
cp jobs-executor/samples/* jobexec_rpm/jobs-executor
cp jobs-executor/templates/* jobexec_rpm/jobs-executor
cp jobs-executor/tests/* jobexec_rpm/jobs-executor
cp jobs-executor/version.py jobexec_rpm/jobs-executor


cd jobexec_rpm

ls

fpm --rpm-user jobexec --rpm-group users --prefix /var/opt -v ${CURRENT_VERSION} -s dir -t rpm --directories jobs-executor -n lhotse-jobexec .
rpm -qlp lhotse-jobexec-${CURRENT_VERSION}-1.x86_64.rpm	

#########################################################

# wget -O - --no-check-certificate http://repo.lhotse.ov.otto.de/apache-tomcat-$JOB_EXEC_VERSION.tar.gz |tar zxf - 
# fpm --rpm-user tomcat --rpm-group tomcat --prefix /var/opt -v $JOB_EXEC_VERSION -s dir -t rpm --directories apache-tomcat-$JOB_EXEC_VERSION -n "lhotse-tomcat" .
# rpm -qlp lhotse-tomcat-$TOMCAT_VERSION-1.x86_64.rpm | grep "/var/opt/apache-tomcat-$TOMCAT_VERSION/lib/catalina.jar"

