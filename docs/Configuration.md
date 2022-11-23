spsauce.ini
=====

The configuration file is used for global configuration and searched in the following directories in order:

* Windows
  * %HOMEDRIVE%%HOMEPATH%/.spsauce/spsauce.ini
  * %LOCALAPPDATA%/SPSauce/spsauce.ini
* Unix (Linux/Mac)
  * $HOME/.spsauce/spsauce.ini
  * /usr/etc/.spsauce/spsauce.ini
  * /etc/.spsauce/spsauce.ini

Example config:

```ini
[Directories]
; This path will be used to cache and download SourceMod and dependencies.
; If you want to use a global cache for all your projects you can set this to an absolute directory.  
; By default it is .spcache, relative to PWD.
PluginCache=.spcache
```