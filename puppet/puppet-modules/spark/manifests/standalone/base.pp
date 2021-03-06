# https://spark.apache.org/docs/latest/spark-standalone.html

class spark::standalone::base (
  $major_version = 'spark-1.1.0',
  $version = 'spark-1.1.0-bin-cdh4',
  $install_dir = '/opt',
  $user = 'spark',
  $group = 'hadoop'
) {
  require macro

  ensure_resource('group', $group, {'ensure' => 'present'})

  user { $user :
    ensure => present,
    groups => $group,
    home => "${install_dir}/spark",
  }

  $url = "http://www.us.apache.org/dist/spark/${major_version}/${version}.tgz"
  $tgz = "/tmp/${version}.tgz"
  macro::download { $url :
    path => $tgz,
  } -> macro::extract { $tgz :
    path    => $install_dir,
    creates => "${install_dir}/${version}",
  } -> file { "${install_dir}/${version}" :
    ensure => directory,
    owner => $user,
    group => $group,
    recurse => true,
    require => [ User[$user], Group[$group] ],
  } -> file { "${install_dir}/spark" :
    ensure => link,
    target => "${install_dir}/${version}",
    owner => $user,
    group => $group,
    require => [ User[$user], Group[$group] ],
  }

  define setup_data_directory (
    $user = 'spark',
    $group = 'hadoop'
  ) {
    ensure_resource('file', $name, {'ensure' => 'directory'})

    file { [ "${name}/spark", "${name}/spark/local" ] :
      ensure  => directory,
      owner   => $user,
      group   => $group,
      mode    => 'ug=rwx,o=',
      require =>  [ File["${name}"], User[$user], Group[$group] ],
    }
  }

  $data_dir_list = split($data_directories, ',')
  setup_data_directory { $data_dir_list : }

  $namenode_hostname = hiera('namenode_hostname', 'namenode')
  $spark_master = hiera('spark_master', 'sparkmaster')
  $spark_master_url = "spark://${spark_master}:7077"
  $spark_driver_memory = hiera('spark_driver_memory', '5g')
  $spark_worker_port = hiera('spark_worker_port', '7078')
  $spark_workers = hiera_array('spark_workers')

  file { "${install_dir}/spark/conf/slaves" :
    ensure => file,
    content => template('spark/slaves.erb'),
    owner => $user,
    group => $group,
    require => [ File["${install_dir}/spark"], User[$user], Group[$group] ],
  }

  file { "${install_dir}/spark/conf/spark-defaults.conf" :
    ensure => file,
    content => template('spark/spark-defaults.conf.erb'),
    owner => $user,
    group => $group,
    require => [ File["${install_dir}/spark"], User[$user], Group[$group] ],
  }

  file { "${install_dir}/spark/conf/spark-env.sh" :
    ensure => file,
    content => template('spark/spark-env.sh.erb'),
    owner => $user,
    group => $group,
    mode => 'a+x',
    require => [ File["${install_dir}/spark"], User[$user], Group[$group] ],
  }
}
