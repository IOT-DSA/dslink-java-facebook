# dslink-java-facebook

## License

Apache

## Usage

To add a  connection, you will need an app id and app secret. Go to https://developers.facebook.com/apps/ and
add a new app to get an app id and secret. 

After adding a connection, go to the url specified by "Authentication URL", login with facebook, and grant the
app the permissions it requests. You should be redirected to a url that looks something like 
"https://www.facebook.com/connect/login_success.html#access_token=CAAXGppnQBTsBALhZAipi6A84JcAB66ZCWH0vHEWVYdgRESsTAK7u7dWZCUumoKJq1lggTJ9JVZBbthXxlwZAYo5YAOiD4YppjsNCxs02TK337wehhIZBkAIZA75YapqGxuy12o7FCPIqnDo8af3Ym4UQCXD2pDyzxTHcvuPFLXLL3reENkcrWcqum6accsqhUBrq3hiRZCLHNQZDZD&expires_in=5175595".
Copy this url, and Invoke the "Authorize" action with it, and the connection should create actions for
interacting with facebook's Graph API (Search, Update Status, etc.)
 
 Notes:
 
 The RawAPICall Action allows you to make any calls to facebook's Graph API, as outlined here: 
 https://developers.facebook.com/docs/graph-api/using-graph-api/v2.3
 The 'type' parameter should be either 'GET', 'POST', or 'DELETE', and the 'query' parameter should
 be the actual API call. Most API calls, however, will require specific permissions. 
 Permissions are outlined here: https://developers.facebook.com/docs/facebook-login/permissions/v2.3
 The "permissions" parameter of the actions for adding/editing connections allows you to select which permissions
 the app asks for during authentication.
 
