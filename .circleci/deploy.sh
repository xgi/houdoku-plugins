git config --global user.email "jake+xgibot@faltro.com"
git config --global user.name "xgi-bot"

git checkout -b repo

mkdir ../repo

mv ./build/classes/java/main/com/faltro/houdoku/plugins/content ../repo/
mv .git ../repo/
mv .circleci ../repo

cd ../repo

git add -A
git commit --allow-empty -m "[auto] circleci deploy"
git push origin repo --force