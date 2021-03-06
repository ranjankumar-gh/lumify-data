class ffmpeg ($prefix="/usr/local", $tmpdir="/usr/local/src") {
  require buildtools
  require ffmpeg::x264
  require ffmpeg::libfdkaac
  require ffmpeg::libmp3lame
  require ffmpeg::libopus
  require ffmpeg::libogg
  require ffmpeg::libvorbis
  require ffmpeg::libvpx
  require ffmpeg::libtheora

  $srcdir = "${tmpdir}/ffmpeg-source"
  $bindir = "${prefix}/bin"

  macro::git::clone { "ffmpeg-clone":
    url     => "http://git.videolan.org/git/ffmpeg.git",
    path    => $srcdir,
    timeout => 600,
  }

  macro::git::checkout { 'ffmpeg-checkout':
    path    => $srcdir,
    branch  => "n2.0",
    require => Macro::Git::Clone["ffmpeg-clone"],
  }

  $configure  = "${srcdir}/configure --prefix='${prefix}' --extra-cflags='-I${prefix}/include' --extra-ldflags='-L${prefix}/lib' --bindir='${prefix}/bin' --extra-libs='-ldl' --enable-gpl --enable-nonfree --enable-libfdk-aac --enable-libmp3lame --enable-libopus --enable-libvorbis --enable-libvpx --enable-libx264 --enable-libtheora"
  $make       = "/usr/bin/make -j${processorcount}"
  $install    = "/usr/bin/make install"
  $distclean  = "/usr/bin/make distclean"
  $hash       = "hash -r"
  $cmd        = "${configure} && ${make} && ${install} && ${distclean} && ${hash}"

  exec { 'ffmpeg-build' :
    cwd         => $srcdir,
    command     => $cmd,
    environment => "PKG_CONFIG_PATH=${prefix}/lib/pkgconfig",
    creates     => "${prefix}/bin/ffmpeg",
    timeout     => 0,
    require     => Macro::Git::Checkout["ffmpeg-checkout"],
  }

  exec { 'ffmpeg-qt-fast-start-build' :
    cwd     => "${srcdir}/tools",
    command => "${make} qt-faststart && cp ${srcdir}/tools/qt-faststart ${prefix}/bin/qt-faststart",
    creates => "${prefix}/bin/qt-faststart",
    require => Exec['ffmpeg-build'],
  }

  file { "/etc/profile.d/usr_local_lib.sh":
    ensure  => file,
    content => "export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:${prefix}/lib",
    require => Exec['ffmpeg-build'],
  }
}
