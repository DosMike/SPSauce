# Example Setup for GitHub Actions

This example is triggered on push to the main branch, if it involves plugin files.
It will then build the plugin, patch the updater files in the `docs/` directory and create a new release.
The docs directory is used to the repository can set up a GitHub Page for the updater file to be delivered.
A `raw.githubusercontent.com`-URL is probably easier to set up and more reliable, but I wanted to try it this way.

Note: I'm not an expert on GitHub Actions, so this might be jank and has room for improvements, PRs welcome.

Note: This is not a drop-in solution, you will have to poke at the scripts and workflow to get it to work!

## Usage of this example

* Add SPSauce to your repository (wrapper and jar)
  * Make sure the ./sps script has execute permissions!    
    On Windows use `git update-index --chmod=+x sps`
* Add the example files to your repository
  * Modify the scripts to match your setup
* Push to your main branch to trigger