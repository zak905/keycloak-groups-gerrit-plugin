# Still work in progress

gerrit plugin to import keycloak groups into gerrit. This plugin complements the [gerrit-oauth-provider](https://github.com/davido/gerrit-oauth-provider) that handles the authentication part. The plugin does not do any authentication, and thus cannot work alone.  

The usage of the plugin requires a token mapper that maps the users group into a property called `group_membership` 