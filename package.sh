#!/bin/bash
set -euo pipefail

./mvnw clean package

aws s3 cp --region us-east-1 target/app.jar s3://[YOUR S3 BUCKET NAME]/app.jar