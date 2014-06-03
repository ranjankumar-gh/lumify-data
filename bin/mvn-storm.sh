#!/bin/bash

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"

if [ "$1" ]; then
  FILTER="$1"
else
  FILTER='.'
  OTHER_MODULES='
    lumify-public/lumify-storm
    lumify-public/lumify-ontology-dev
  '
fi

(
  cd ${DIR}/..

  ENTERPRISE_MODULES=$(find lumify-enterprise -name '*PropertyWorker.java' \
                                           -o -name '*TermMentionFilter.java' \
                                           -o -name '*Translator.java' \
                         | cut -d / -f 1-2 \
                         | grep ${FILTER} \
                         | sort -u)
  MODULES=$(echo ${ENTERPRISE_MODULES} ${OTHER_MODULES} | sed -e 's/ /,/g')

  set -x
  mvn install -pl lumify-root -am -DskipTests -Dsource.skip=true
  mvn package -pl ${MODULES} -am -DskipTests -Dsource.skip=true
  set +x

  for module in $(echo ${MODULES} | sed -e 's/,/ /g'); do
    simple_module_name=$(echo ${module} | cut -d / -f 2)
    if [ -f ${module}/target/${simple_module_name}-*-jar-with-dependencies.jar ]; then
      file=$(ls ${module}/target/${simple_module_name}-*-jar-with-dependencies.jar)
      files="${files} ${DIR}/../${file}"
    else
      warnings="${warnings} ${module}"
    fi
  done

  if [ "${warnings}" != '' ]; then
    echo -n 'WARNING: the following module(s) did not create an uber-jar:' >&2
    echo "${warnings}" | sed -e 's/ /\n  /g' >&2
  fi

  echo -n 'hadoop fs -put'
  echo -n "${files}" | sed -e 's/ / \\\n  /g'
  echo ' \'
  echo '  /lumify/libcache'
)