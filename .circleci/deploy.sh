# download google cloud sdk
curl -o gcloud-sdk.tar.gz https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-241.0.0-linux-x86_64.tar.gz 
tar zxvf gcloud-sdk.tar.gz google-cloud-sdk

# authorize gcloud sdk
echo $GCLOUD_SERVICE_KEY | ./google-cloud-sdk/bin/gcloud auth activate-service-account --key-file=-
./google-cloud-sdk/bin/gcloud --quiet config set project ${GOOGLE_PROJECT_ID}
./google-cloud-sdk/bin/gcloud --quiet config set compute/zone ${GOOGLE_COMPUTE_ZONE}

# upload plugin classfiles to bucket
./google-cloud-sdk/bin/gsutil cp -r build/classes/java/main/com/faltro/houdoku/plugins/content gs://houdoku-plugins