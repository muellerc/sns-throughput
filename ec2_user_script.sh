#!/bin/bash
set -euo pipefail

curl -4 -L https://cdn.azul.com/zulu/bin/zulu11.45.27-ca-jdk11.0.10-linux_x64.tar.gz -o zulu11.45.27
tar -zxvf zulu11.45.27
rm zulu11.45.27
echo 'export JAVA_HOME=$HOME/zulu11.45.27-ca-jdk11.0.10-linux_x64' >>$HOME/.bash_profile
echo 'export PATH=$JAVA_HOME/bin:$PATH' >>$HOME/.bash_profile
source $HOME/.bash_profile

aws s3 cp --region us-east-1 s3://[YOUR S3 BUCKET NAME]/app.jar .

java -XX:+UseG1GC -Xms2G -Xmx2G -jar app.jar 170 [YOUR SNS TOPIC ARN] > stdout.txt 2>&1 &
