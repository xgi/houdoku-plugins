# download/authorize google cloud sdk
curl -o gcloud-sdk.tar.gz https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-241.0.0-linux-x86_64.tar.gz 
tar zxvf gcloud-sdk.tar.gz gcloud-sdk

echo $GCLOUD_SERVICE_KEY | ./gcloud-sdk/bin/gcloud auth activate-service-account --key-file=-
./gcloud-sdk/bin/gcloud --quiet config set project ${GOOGLE_PROJECT_ID}
./gcloud-sdk/bin/gcloud --quiet config set compute/zone ${GOOGLE_COMPUTE_ZONE}

# upload plugin classfiles to bucket
./gcloud-sdk/bin/gsutil cp -r $PLUGINS_DIR/content gs://houdoku-plugins

# build/upload index
SOURCE_DIR=src/main/java/com/faltro/houdoku/plugins

for dir in $SOURCE_DIR/*/*; do
    dir=${dir%*/};
    cat $dir/metadata.json | jq -r
;done | jq -sr '[.[]]' > index.json

./gcloud-sdk/bin/gsutil cp -r index.json gs://houdoku-plugins