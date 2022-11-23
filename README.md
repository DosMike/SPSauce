SPSauce
======

This is intended as dependency loader to help build-automation tools build sourcemod plugins with a lot of dependencies.
It will not help you set up a sourcemod server!

Why might this be necessary? There are a lot of consecutive libraries like smlib, morecolors, stocksoup, tf2items and many more
that can quickly increase the number of dependencies. Packing these libraries into your own repository might not always be 
permitted by the projects license. On the other hand packing old versions of libraries that may be buggy is also not a good 
idea and additionally convolutes your repository with dependency files.

On the other hand lot of plugins and libraries are hosted on the allied modders forums, where they are most accessible
to users, but buildtools usually have a hard time accessing resources from the forums. This tool fetches attachments
from plugin topics and patch comments and tries to place them into the correct directory within the build cache.

[Documentation](docs/Readme.md)
-----