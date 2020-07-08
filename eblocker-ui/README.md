# eBlocker UI

## Development

Build the frontend for development:

    gulp build-dev

Gulp keeps running in the foreground and watches for changes in the
source files. The project is rebuilt automatically when changes are
detected.

If you built the project with Maven, you can run gulp with

    node/node node_modules/.bin/gulp build-dev

or set an alias in your shell:

    alias gulp='node/node node_modules/.bin/gulp'

If you run the Icapserver as described in the [eblocker README](../README.md),
you should be able to access the UI at [localhost:3000](http://localhost:3000/).
