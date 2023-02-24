Tool Help
=====

Arguments
-----

`sps <Arguments> [--] [Build script]`
* If you do not specify a build script, it will default to `sp.sauce`
* `-x`|`--fulldeps`<br> Normally dependencies only extract .sp and .inc files as that's all that's requried for building, but you can let them exctract all files.
* `--offline`<br> Skips all `auth`, `sourcemod`, `dependency` and `clone` tasks.
* `-e`|`--no-exec`<br> Skips all `exec` tasks.
* `-s`|`--no-script`<br> Skips all `script` tasks.
* `-i` Interactive single mode. Will execute instructions from stdin every line, unless a batch is started.
* `-I` Interactive batch mode. Immediately start a batch in interactive mode. Can be used to pipe instructions in.
* `--<KEY> <VALUE>`<br> Passes values into the template system.
* `--stacktrace`<br> Used for debugging

Wrappers
-----
Since this application is written in java, invoking SPSauce can be a bit tedious with `java -jar <path to jar>`.
The release archive contains wrapper scripts for batch and bash that short this to `sps`. These wrappers require the
jar archive to be placed in ./spsauce/spsauce-version-all.jar.  
For non-technicall people it's best to pack the spsauce wrapper and script in your repository, and use the default
script name `sp.sauce`. This way you can tell your users to just execute `./sps` or `sps.bat` to build the plugin.