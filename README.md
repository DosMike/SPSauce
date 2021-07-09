SPSauce
======

This is intended as dependency loader to help automated build tools build sourcemod plugins with a lot of dependencies.
It will not help you set up a sourcemod server!

Why might this necessary? Plugins (especially in TF2) can quickly require a lot of convenience libraries like smlib,
morecolors, stocksoup, tf2items and many more. Packing these libraries into your own repository might not always be 
permitted by the projects license so pulling them at compile time might be required for public projects.

On the other hand lot of plugins and libraries are hosted on the allied modders forums, where they are most accessible
to users, but buildtools usually have a hard time accessing resources from the forums. This tool fetches attachments
from plugin topics and patch comments and tries to place them into the correct directory within the build cache.

How it works
-----

SPsauce pulls an instance of sourcemod and all declared dependencies into a cache directory within the project.
The compiler will be located in ./spcache/addons/sourcemod/scripting/ and dependencies will be placed around it.
A built in `spcomp`-Task automatically add some common include directories, and can run in paralell with other compiles.

Additionally you can call other applications from withing the build-script, if the provided functionallity is not enough.
Keep in mind that all file targets are limited to subdirectories wherever possible.

Execution is done in three phases: Parse, dependency resolution, task execution. The second phase  will already 

Build Script
-----
There are the platform prefixes, @windows, @linux and @mac that allow you to perform actions for a single platform.
Lines with that start with // or # are comments. Spaces are trimmed from each line, so you can indent as you please.

### Commands
| Name | Arguments | Description |
|-|-|-|
| auth | 'github' \<PAT> [login] | Log in to the GitHub API with a personal access token. The login parameter is for organisations and increasing the request limit even more. GitHub auth is required for `dependency github` only. |
| sourcemod | \<branch> (\<build\>\|['latest']) | Download the specified sourcemod branch, with the given build number or the latest build |
| dependency | ('am'\|'forum'\|'forums') \<plugin thread id> | Download the files from a plugin posted on the forums. Directories will be guessed by file extension |
| dependency | ('am'\|'forum'\|'forums') 'patch' \<post id> | Download the files from a single forum post id. Directories will be guessed again, and the file has to be replaced |
| dependency | 'github' \<project slug> \<tag name> [archive name] | Looks up the release tag in the repo. Will download the sources if no other archive was specified |
| dependency | 'github' \<project slug> 'latest' | Download the archive for the main branch |
| dependency | 'github' \<project slug> \<branch name>'-SNAPSHOT' | Download the archive for the specified branch. The -SNAPSHOT suffix for the branch name is just to distinguish between release tags |
| dependency | 'limetech' \<project id> (\<version>\|\<build>) | Download a version or build of one of asherkins plugins hosted on limetech.org. Take the project id from the url in the version list |
| dependency | 'raw' \<url> | Has to point to an archive or known file type to be placed into the cache folder |
| clone | \<url> [branch] 'into' \<dir> | Requires git to be installed, clones the specified branch into specified directory within spcache |
| compilepool | \<size> | When encountering `spcomp` tasks, collect all consecutive `spcomp` tasks and execute the specified amount of tasks at the same time. Defaults to the amount of CPUs you have |
| spcomp | ... | Args are the same as for the sp compiler. Automatically adds the include directories spcache/addons/sourcemod/scripting/include/; addons/sourcemod/scripting/include/; scripting/include/; include/ |
| exec | \<commandline> | Run a command, script cancelles on exit value != 0 |
| echo | \<message> | Write a message to std out during dependency resolution phase |
| die | \<message> | Writes a message to std out during dependency resolution phase and exits with error-level |
| mkdir | \<path> | Creates a directory within cwd |
| delete/erase/remove | \<path> | Delete a file or directory recursively within cwd |
| move | \<from> ':' \<to> | Move a directory within cwd |

Not all arguments, but most support replacements from environment variables or applications arguments. This is mainly inteded to not leak you auth tokens.
${NAME} is replaced with an environment variable, %{NAME} is replaced with the value from the argument `--<NAME> <VALUE>`. There is one exception however:
${CWD} and %{CWD} are replaced with the path of the loaded build-script.

Arguments
-----

`sps <Arguments> [--] [Build script]`
* If you do not specify a build script, it will default to `sp.sauce`
* `-x`|`--fulldeps`<br> Normally dependencies only extract .sp and .inc files as that's all that's requried for building, but you can let them exctract all files.
* `--offline`<br> Skips all `auth`, `sourcemod`, `dependency` and `clone` tasks.
* `-s`|`--no-exec`<br> Skips all `exec` tasks.
* `--<KEY> <VALUE>`<br> Passes values into the template system.
* `--stacktrace`<br> Used for debugging

Wrappers
-----
Since this application is written in java, invoking SPSauce can be a bit tedious with `java -jar <path to jar>`.
The release archive contains wrapper scripts for batch and bash that short this to `sps`. These wrappers require the
jar archive to be placed in ./.spsauce/spsauce-version-all.jar. 