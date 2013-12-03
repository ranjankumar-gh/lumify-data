#!/bin/bash

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"

classpath=$(${DIR}/classpath.sh lumify-public/storm-lumify)
if [ $? -ne 0 ]; then
  echo "${classpath}"
  exit
fi

java \
-Xmx512m \
-Djava.awt.headless=true \
-Dfile.encoding=UTF-8 \
-Djava.library.path=$LD_LIBRARY_PATH \
-classpath ${classpath} \
com.altamiracorp.lumify.storm.StormRunner \
--local
