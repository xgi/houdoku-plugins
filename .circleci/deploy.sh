git config --global user.email "jake+xgibot@faltro.com"
git config --global user.name "xgi-bot"

git checkout -b repo
git pull origin repo

mv ./build/classes/java/main/com/faltro/houdoku/plugins/content ./

git add -fA
git commit --allow-empty -m "[auto] circleci deploy"
git push origin repo