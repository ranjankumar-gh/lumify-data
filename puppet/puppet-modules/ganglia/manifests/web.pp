class ganglia::web inherits ganglia {

  ensure_resource('package', 'httpd', {'ensure' => 'present' })

  package { 'ganglia-web':
    ensure => present,
    require => Package['httpd'],
  }

  file { "httpd-ganglia-conf":
    path    => "/etc/httpd/conf.d/ganglia.conf",
    ensure  => file,
    content => template("ganglia/httpd-ganglia.conf.erb"),
    require => Package['ganglia-web'],
  }

  class { 'ganglia::web::diskstat' :
    require => Package['ganglia-gmond-python'],
  }
}
