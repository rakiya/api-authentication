#!/bin/bash

# このファイルの絶対パス
# shellcheck disable=SC2046
SCRIPT_DIR=$(
  cd $(dirname "$0") || exit
  pwd
)

if [ "$1" == "dev" ]; then

  printf "\e[1;32m開発環境を構築します\n\e[0m"

  printf "\e[1;32mデータベースを作成します\n\e[0m"
  docker-compose -f "$SCRIPT_DIR"/../docker-compose.dev.yml up -d

elif [ "$1" == "local" ]; then

  printf "\e[1;32mローカル環境を構築します\n\e[0m"
  # shellcheck disable=SC2164
  cd "$SCRIPT_DIR"/..

  printf "\e[1;32mコンパイルします\n\e[0m"
  gradle shadowJar

  printf "\e[1;32mDockerイメージを作成します\n\e[0m"
  docker build -t api-authentication:latest .

  printf "\e[1;32mDockerコンテナを作成します\n\e[0m"
  docker-compose -f docker-compose.local.yml up -d

else

  # shellcheck disable=SC2059
  printf "\e[1;31m引数$1は正しくありません\n\e[0m"
  printf "Usage: sh build-env.sh [dev|local]\n"
  exit 1

fi
