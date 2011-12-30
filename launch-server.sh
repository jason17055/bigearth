#!/bin/sh -e

perl -MJSON          -e 1 || sudo yum install -y perl-JSON
perl -M"Time::HiRes" -e 1 || sudo yum install -y perl-Time-HiRes
perl -MDBI           -e 1 || sudo yum install -y perl-DBI perl-DBD-MySQL

rm -rf trains.tar.gz server html
wget http://jason.long.name/trains/trains.tar.gz
tar -xzvf trains.tar.gz
cd server
./ser.pl
