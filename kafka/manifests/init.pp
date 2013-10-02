class kafka(
  $version = "0.7.2",
  $user = "kafka",
  $group = "kafka",
  $installdir = "/opt",
  $home = "/opt/kafka",
  $logdir = "/opt/kafka/logs",
  $tmpdir = '/tmp'
) {
  include macro
  require java
  require zookeeper

  $zookeeper_nodes = hiera_hash('zookeeper_nodes')
  $kafka_consumer_group_id = hiera('kafka_consumer_group_id')
  $kafka_host_ipaddress = hiera('kafka_host_ipaddress')

  $downloadpath = "${tmpdir}/kafka-${version}-incubating-src.tgz"
  $configdir = "${home}/config"
  $extractdir = "${installdir}/kafka-${version}-incubating-src"

  group { $group :
    ensure => present,
  }

  user { $user :
    ensure  => present,
    gid     => $group,
    home    => $home,
    require => Group[$group],
  }

  macro::download { "http://apache.claz.org/incubator/kafka/kafka-${version}-incubating/kafka-${version}-incubating-src.tgz":
    path    => $downloadpath,
    require => User[$user],
  } -> macro::extract { $downloadpath:
    path    => $installdir,
    creates => $extractdir,
  }

  file { $extractdir:
    owner   => $user,
    group   => $group,
    recurse => true,
  }

  file { $home:
    ensure  => link,
    target  => $extractdir,
    require => File[$extractdir],
  }

  file { $logdir:
    ensure => directory,
    owner  => $user,
    group  => $group,
    require => File[$home]
  }

  file { "${configdir}/consumer.properties":
    ensure   => file,
    content  => template('kafka/consumer.properties.erb'),
    require  => File[$home],
  }

  file { "${configdir}/log4j.properties":
    ensure   => file,
    content  => template('kafka/log4j.properties.erb'),
    require  => File[$home],
  }

  file { "${configdir}/producer.properties":
    ensure   => file,
    content  => template('kafka/producer.properties.erb'),
    require  => File[$home],
  }

  file { "${configdir}/server.properties":
    ensure   => file,
    content  => template('kafka/server.properties.erb'),
    require  => File[$home],
  }

  file { "${configdir}/zookeeper.properties":
    ensure   => file,
    content  => template('kafka/zookeeper.properties.erb'),
    require  => File[$home],
  }

  exec { "sbt update" :
    command => "${home}/sbt update",
    user    => $user,
    group   => $group,
    cwd     => $home,
#    unless  => "/usr/bin/test -f ${home}/lib_managed",
    require => [
      File["${configdir}/consumer.properties"],
      File["${configdir}/log4j.properties"],
      File["${configdir}/producer.properties"],
      File["${configdir}/server.properties"],
      File["${configdir}/zookeeper.properties"]
    ],
  }

  exec { "sbt package" :
    command => "${home}/sbt update",
    user    => $user,
    group   => $group,
    cwd     => $home,
    #    unless  => "/usr/bin/test -f ${home}/lib_managed",
    require => Exec["sbt update"],
  }
}
