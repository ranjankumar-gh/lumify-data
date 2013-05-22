#!/bin/bash

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"

classpath=$(${DIR}/classpath.sh core)

java \
-Dfile.encoding=UTF-8 \
-classpath ${classpath} \
-Xmx1024M \
com.altamiracorp.reddawn.entityExtraction.EntityExtractionMR \
--zookeeperInstanceName=reddawn \
--zookeeperServerNames=192.168.33.10 \
--username=root \
--password=password \
--classname=com.altamiracorp.reddawn.entityExtraction.OpenNlpMaximumEntropyEntityExtractor \
--config=nlpConfPathPrefix=file://$(cd ${DIR}/.. && pwd)
