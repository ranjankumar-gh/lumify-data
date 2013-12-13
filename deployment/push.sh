#!/bin/bash -eu

SSH_OPTS='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=QUIET'

elastic_ip=$1
HOSTS_FILE=$2

DATETIME=$(date +"%Y%m%dT%H%M")
MAVEN_STATUS=unknown
DEFAULT_PROFILE='storm-jar,web-war'

function git_archive {
  local prefix=$1
  local dir=$2
  set +u; local include_dirs=$3; set -u

  local githash=$(cd ${dir}; git log -n 1 --format='%h')
  prefix="${prefix}-${DATETIME}-${githash}"
  local tar="/tmp/${prefix}.tar"
  local tgz="/tmp/${prefix}.tgz"

  # export HEAD
  (cd ${dir}; git archive HEAD ${include_dirs} --format=tar --prefix="${prefix}/" --output=${tar})

  # add metadata
  (cd ${dir}; git log -n 1 --format='%H%n%an <%ae>%n%ad%n%s' > /tmp/GIT_INFO.txt)
  if [ "$(uname)" = 'Linux' ]; then
    (cd /tmp; tar rf ${tar} --transform "s|^|${prefix}/|" GIT_INFO.txt)
  else
    (cd /tmp; tar rf ${tar} -s "|^|${prefix}/|" GIT_INFO.txt)
  fi
  rm /tmp/GIT_INFO.txt

  # compress
  gzip ${tar} && mv ${tar}.gz ${tgz}

  echo ${tgz}
}

function run_maven {
  local profile=${DEFAULT_PROFILE}
  set +u
  if [ "$1" ]; then
    profile="$1"
  fi
  set -u

  if [ "${MAVEN_STATUS}" = 'ok' -a "${profile}" = "${DEFAULT_PROFILE}" ]; then
    echo 'maven ok.' >&2
  else
    echo 'running maven...' >&2
    local mvn_output="$(cd ..; mvn clean install -P ${profile} -DskipTests=true)"
    local mvn_exit=$?
    if [ ${mvn_exit} -ne 0 ]; then
      echo "${mvn_output}" >&2
      exit ${mvn_exit}
    else
      export MAVEN_STATUS=ok
      echo 'maven done.' >&2
    fi
  fi
}

function bundle_init {
  FILE_LIST="${FILE_LIST} aws/bin-ec2/setup_disks.sh update.sh run_puppet.sh init.sh setup_ssh.sh start_*.sh"
}

function bundle_puppet {
  local modules_tgz=$(git_archive modules .. puppet)
  local puppet_modules_tgz=$(git_archive puppet-modules ../puppet/puppet-modules)
  ./site_from_hosts.rb ${HOSTS_FILE} > /tmp/site-${DATETIME}.pp
  ./hiera_from_hosts.rb ${HOSTS_FILE} > /tmp/hiera-${DATETIME}.yaml

  FILE_LIST="${FILE_LIST} ${modules_tgz} ${puppet_modules_tgz} /tmp/site-${DATETIME}.pp /tmp/hiera-${DATETIME}.yaml"
}

function bundle_conf {
  local conf_tgz=$(git_archive conf .. 'conf/opencv conf/opennlp')

  FILE_LIST="${FILE_LIST} setup_conf.sh ${conf_tgz}"
}

function bundle_war {
  run_maven web-war
  local war_files=$(find .. -name '*.war')

  FILE_LIST="${FILE_LIST} lumify.xml ${war_files}"
}

function bundle_storm {
  run_maven storm-jar
  local jar_files=$(find .. -name '*-jar-with-dependencies.jar')

  FILE_LIST="${FILE_LIST} ${jar_files}"
}


FILE_LIST=${HOSTS_FILE}

set +u
[ "$3" ] && component=$3 || component=everything
set -u
case ${component} in
  puppet)
    bundle_puppet
    ;;
  www | war)
    bundle_war
    ;;
  storm)
    bundle_storm
    ;;
  everything)
    bundle_init
    bundle_puppet
    bundle_conf
    FILE_LIST="${FILE_LIST} setup_geonames.sh setup_import.sh"
    run_maven
    bundle_war
    bundle_storm
    ;;
  *)
    echo "invalid component: ${component}"
    exit -1
    ;;
esac

scp ${SSH_OPTS} ${FILE_LIST} root@${elastic_ip}:

for file in ${FILE_LIST}; do
  case ${file} in
    /tmp/*.tgz)
      rm ${file}
      ;;
  esac
done
