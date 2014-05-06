#!/bin/bash

SSH_OPTS='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=QUIET'

HOSTS_FILE=$1

function _localhost {
  [ "${HOSTS_FILE}" = 'localhost' ]
}

function _namenode {
  _localhost && echo 'localhost' || awk '/ +namenode/ {print $1}' ${HOSTS_FILE}
}
function _secondarynamenode {
  _localhost && echo 'localhost' || awk '/ +secondarynamenode/ {print $1}' ${HOSTS_FILE}
}
function _nodes {
  _localhost && echo 'localhost' || awk '/node[0-9]+/ {print $1}' ${HOSTS_FILE}
}
function _zk_servers {
  _localhost && echo 'localhost' || awk '/zk[0-9]+/ {print $1}' ${HOSTS_FILE}
}
function _accumulomaster {
  _localhost && echo 'localhost' || awk '/ +accumulomaster/ {print $1}' ${HOSTS_FILE}
}
function _webservers {
  _localhost || awk '/www[0-9]+/ {print $1}' ${HOSTS_FILE}
}
function _kafka_servers {
  _localhost || awk '/kafka[0-9]+/ {print $1}' ${HOSTS_FILE}
}
function _rabbitmq_servers {
  _localhost && echo 'localhost' || awk '/rabbitmq[0-9]+/ {print $1}' ${HOSTS_FILE}
}
function _stormmaster {
  _localhost && echo 'localhost' || awk '/ +stormmaster/ {print $1}' ${HOSTS_FILE}
}

function _run_at {
  local host=$1; shift
  local cmd="$*"

  if [ "${host}" = 'localhost' ]; then
    if [ $(id -un) = 'root' ]; then
      ${cmd}
    else
      sudo ${cmd}
    fi
  else
    ssh ${SSH_OPTS} root@${host} ${cmd}
  fi
}

function _hadoop_start {
  if [ "${FORMAT_HDFS}" = 'true' ]; then
    local ready='no'
    while [ "${ready}" != 'yes' ]; do
      echo "ssh to $(_namenode) and as the hdfs user run: hdfs namenode -format"
      echo "then type 'yes' and press return"
      read ready
    done
  fi

  _run_at $(_namenode) service hadoop-hdfs-namenode start
  _run_at $(_secondarynamenode) service hadoop-hdfs-secondarynamenode start

  for node in $(_nodes); do
    echo ${node}
    if [ "${FORMAT_HDFS}" = 'true' ]; then
      for data in $(_run_at ${node} mount | awk '/\/data[1-3]/ {print $3}'); do
        _run_at ${node} mkdir -p ${data}/hadoop/tmp
        _run_at ${node} chown -R hdfs:hadoop ${data}/hadoop
        _run_at ${node} mkdir -p ${data}/hdfs/data ${data}/hdfs/name
        _run_at ${node} chown -R hdfs:hadoop ${data}/hdfs
        _run_at ${node} mkdir -p ${data}/mapred/local
        _run_at ${node} chown -R mapred:hadoop ${data}/mapred
      done
    fi
    _run_at ${node} service hadoop-hdfs-datanode start
    _run_at ${node} service hadoop-0.20-mapreduce-tasktracker start
  done

  _run_at $(_namenode) service hadoop-0.20-mapreduce-jobtracker start
}

function _hadoop_stop {
  _run_at $(_namenode) service hadoop-0.20-mapreduce-jobtracker stop

  _run_at $(_namenode) service hadoop-hdfs-namenode stop
  _run_at $(_secondarynamenode) service hadoop-hdfs-secondarynamenode stop

  for node in $(_nodes); do
    echo ${node}
    _run_at ${node} service hadoop-0.20-mapreduce-tasktracker stop
    _run_at ${node} service hadoop-hdfs-datanode stop
  done
}

function _hadoop_status {
  _run_at $(_namenode) service hadoop-hdfs-namenode status
  _run_at $(_secondarynamenode) service hadoop-hdfs-secondarynamenode status

  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} service hadoop-hdfs-datanode status
    echo -n "${node}: "
    _run_at ${node} service hadoop-0.20-mapreduce-tasktracker status
  done

  _run_at $(_namenode) service hadoop-0.20-mapreduce-jobtracker status
}

function _hadoop_rmlogs {
  cmd='rm -f /var/log/hadoop-0.20-mapreduce/*.{log,out}* /var/log/hadoop-hdfs/*.{log,out}*'

  for node in $(echo $(_namenode) $(_secondarynamenode) $(_nodes) | tr ' ' '\n' | sort -t . -k 4 -n -u); do
    echo "${node}: ${cmd}"
    _run_at ${node} ${cmd}
  done
}

function _zookeeper_start {
  for zk in $(_zk_servers); do
    echo ${zk}
    _run_at ${zk} service zookeeper-server start
  done
}

function _zookeeper_stop {
  for zk in $(_zk_servers); do
    echo ${zk}
    _run_at ${zk} service zookeeper-server stop
  done
}

function _zookeeper_status {
  for zk in $(_zk_servers); do
    echo -n "${zk}: "
    _run_at ${zk} service zookeeper-server status
  done
}

function _zookeeper_rmlogs {
  cmd='rm -f /var/log/zookeeper/*'

  for zk in $(_zk_servers); do
    echo "${zk}: ${cmd}"
    _run_at ${zk} ${cmd}
  done
}

function _accumulo_start {
  if [ "${INIT_ACCUMULO}" = 'true' ]; then
    local ready='no'
    while [ "${ready}" != 'yes' ]; do
      echo "ssh to $(_accumulomaster) and as the accumulo user run: /usr/lib/accumulo/bin/accumulo init"
      echo "then type 'yes' and press return"
      read ready
    done
  fi

  _run_at $(_accumulomaster) initctl start accumulo-master
  _run_at $(_accumulomaster) initctl start accumulo-gc
  _run_at $(_accumulomaster) initctl start accumulo-monitor
  _run_at $(_accumulomaster) initctl start accumulo-tracer

  for node in $(_nodes); do
    echo ${node}
    _run_at ${node} initctl start accumulo-tserver
  done
}

function _accumulo_stop {
  _run_at $(_accumulomaster) initctl stop accumulo-tracer
  _run_at $(_accumulomaster) initctl stop accumulo-monitor
  _run_at $(_accumulomaster) initctl stop accumulo-gc
  _run_at $(_accumulomaster) initctl stop accumulo-master

  for node in $(_nodes); do
    echo ${node}
    _run_at ${node} initctl stop accumulo-tserver
  done
}

function _accumulo_status {
  _run_at $(_accumulomaster) initctl status accumulo-master
  _run_at $(_accumulomaster) initctl status accumulo-gc
  _run_at $(_accumulomaster) initctl status accumulo-monitor
  _run_at $(_accumulomaster) initctl status accumulo-tracer

  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl status accumulo-tserver
  done
}

function _accumulo_rmlogs {
  cmd='rm -f /var/log/accumulo-upstart-*.log /var/log/accumulo/*'

  for node in $(echo $(_accumulomaster) $(_nodes) | tr ' ' '\n' | sort -t . -k 4 -n -u); do
    echo "${node}: ${cmd}"
    _run_at ${node} ${cmd}
  done
}

function _elasticsearch_start {
  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl start elasticsearch
  done
}

function _elasticsearch_stop {
  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl stop elasticsearch
  done
}

function _elasticsearch_status {
  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl status elasticsearch
  done
}

function _elasticsearch_rmlogs {
  cmd='rm -f /var/log/elasticsearch/*'

  for node in $(_nodes); do
    echo "${node}: ${cmd}"
    _run_at ${node} ${cmd}
  done
}

function _jetty_start {
  for webserver in $(_webservers); do
    echo -n "${webserver}: "
    _run_at ${webserver} service jetty start
  done
}

function _jetty_stop {
  for webserver in $(_webservers); do
    echo -n "${webserver}: "
    _run_at ${webserver} service jetty stop
  done
}

function _jetty_status {
  for webserver in $(_webservers); do
    echo "${webserver}:"
    _run_at ${webserver} service jetty status
  done
}

function _jetty_rmlogs {
  cmd='rm -rf /opt/jetty/logs/*'

  for webserver in $(_webservers); do
    echo "${webserver}: ${cmd}"
    _run_at ${webserver} ${cmd}
  done
}

function _kafka_start {
  for kafka in $(_kafka_servers); do
    echo -n "${kafka}: "
    _run_at ${kafka} initctl start kafka
  done
}

function _kafka_stop {
  for kafka in $(_kafka_servers); do
    echo -n "${kafka}: "
    _run_at ${kafka} initctl stop kafka
  done
}

function _kafka_status {
  for kafka in $(_kafka_servers); do
    echo -n "${kafka}: "
    _run_at ${kafka} initctl status kafka
  done
}

function _kafka_rmlogs {
  echo "kafka log removal not yet implemented"
}

function _rabbitmq_start {
  for rabbitmq in $(_rabbitmq_servers); do
    echo -n "${rabbitmq}: "
    _run_at ${rabbitmq} service rabbitmq-server start
  done
}

function _rabbitmq_stop {
  for rabbitmq in $(_rabbitmq_servers); do
    echo -n "${rabbitmq}: "
    _run_at ${rabbitmq} service rabbitmq-server stop
  done
}

function _rabbitmq_status {
  for rabbitmq in $(_rabbitmq_servers); do
    echo -n "${rabbitmq}: "
    _run_at ${rabbitmq} service rabbitmq-server status
  done
}

function _rabbitmq_rmlogs {
  cmd='rm -f /var/log/rabbitmq/*'

  for rabbitmq in $(_rabbitmq_servers); do
    echo "${rabbitmq}: ${cmd}"
    _run_at ${rabbitmq} ${cmd}
  done
}

function _storm_start {
  _run_at $(_stormmaster) initctl start storm-nimbus
  _run_at $(_stormmaster) initctl start storm-ui

  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl start storm-supervisor
  done
}

function _storm_stop {
  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl stop storm-supervisor
  done

  _run_at $(_stormmaster) initctl stop storm-ui
  _run_at $(_stormmaster) initctl stop storm-nimbus
}

function _storm_status {
  _run_at $(_stormmaster) initctl status storm-ui
  _run_at $(_stormmaster) initctl status storm-nimbus

  for node in $(_nodes); do
    echo -n "${node}: "
    _run_at ${node} initctl status storm-supervisor
  done
}

function _storm_rmlogs {
  cmd="rm -f /opt/storm/logs/*"

  for node in $(echo $(_stormmaster) $(_nodes) | tr ' ' '\n' | sort -t . -k 4 -n -u); do
    echo "${node}: ${cmd}"
    _run_at ${node} ${cmd}
  done
}

function _all_start {
  _hadoop_start
  _zookeeper_start
  _accumulo_start
  _elasticsearch_start
  _kafka_start
  _rabbitmq_start
  _jetty_start
  _storm_start
}

function _all_stop {
  _storm_stop
  _jetty_stop
  _rabbitmq_stop
  _kafka_stop
  _elasticsearch_stop
  _accumulo_stop
  _zookeeper_stop
  _hadoop_stop
}

function _all_status {
  _hadoop_status
  _zookeeper_status
  _accumulo_status
  _elasticsearch_status
  _kafka_status
  _rabbitmq_status
  _jetty_status
  _storm_status
}

function _all_rmlogs {
  _hadoop_rmlogs
  _zookeeper_rmlogs
  _accumulo_rmlogs
  _elasticsearch_rmlogs
  _kafka_rmlogs
  _rabbitmq_rmlogs
  _jetty_rmlogs
  _storm_rmlogs
}

function _run {
  local pattern=$1
  local command_and_args="${@:2}"

  echo ${command_and_args}

  for host in $(awk "/${pattern}/ {print \$1}" ${HOSTS_FILE}); do
    echo ${host}
    echo "${command_and_args}" | _run_at ${host} bash -s
  done
}

function _usage {
  echo "$0 <hosts file|localhost> first|start|stop|restart|status|rmlogs [component name]"
  local z=$(echo "$0" | tr '[:print:]' ' ')
  echo "$z                        run <pattern> <command and args>"
  echo "where the optional component name is one of the following:"
  awk '/function.*_start/ && ! /all/ {print $2}' $0 | sed -e 's/^_/    /' -e 's/_start//'
}

if [ "${HOSTS_FILE}" != 'localhost' ]; then
  if [ ! -f "${HOSTS_FILE}" ]; then
    echo "ERROR: hosts file or 'localhost' required!"
    _usage
    exit -1
  fi
fi

case "$2" in
  first)
    FORMAT_HDFS='true'
    INIT_ACCUMULO='true'
    _all_start
    ;;
  start)
    if [ "$3" ]; then
      _$3_start
    else
      _all_start
    fi
    ;;
  stop)
    if [ "$3" ]; then
      _$3_stop
    else
      _all_stop
    fi
    ;;
  restart)
    if [ "$3" ]; then
      _$3_stop
      _$3_start
    else
      _all_stop
      _all_start
    fi
    ;;
  status)
    if [ "$3" ]; then
      _$3_status
    else
      _all_status
    fi
    ;;
  rmlogs)
    if [ "$3" ]; then
      _$3_rmlogs
    else
      _all_rmlogs
    fi
    ;;
  run)
    if [ $# -lt 4 ]; then
      echo "ERROR: the 'run' action requires a pattern and the desired command and args"
      _usage
      exit -3
    else
      _run $3 "${@:4}"
    fi
    ;;
  *)
    echo "ERROR: action required!"
    _usage
    exit -2
    ;;
esac
