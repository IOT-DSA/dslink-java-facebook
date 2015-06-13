package facebook;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.GeoLocation;
import facebook4j.RawAPIResponse;
import facebook4j.auth.AccessToken;
import facebook4j.conf.ConfigurationBuilder;
import facebook4j.json.DataObjectFactory;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;

import java.io.*;

public class FacebookThing {
	private static final Logger LOGGER;
	
	static {
		LOGGER = LoggerFactory.getLogger(FacebookThing.class);
	}
	
	private Node node;
	private Node err;
	private Facebook facebook;
	private AccessToken accessToken;
	private String userPath;
	/* Add any necessary permissions here */
	private static final String PERMISSIONS = "publish_actions, read_stream"; 
	
	private FacebookThing(Node node) {
		this.node = node;
	}
	
	public static void start(Node parent) {
		Node node = parent.createChild("facebook").build();
		final FacebookThing face = new FacebookThing(node);
		face.init();
	}
	
	private void init() {
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		.setOAuthAppId("1625793730970939")
		.setOAuthAppSecret("023a35e161c4187af323eef166bc8e16")
		.setOAuthPermissions(PERMISSIONS)
		.setJSONStoreEnabled(true);
		err = node.createChild("errors").build();
		facebook = new FacebookFactory(cb.build()).getInstance();		
		Action act = new Action(Permission.READ, new LoginHandler());
		act.addParameter(new Parameter("username", ValueType.STRING));
		node.createChild("login").setAction(act).build();
	}
	
	
	private class LoginHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			
			String username = event.getParameter("username", ValueType.STRING).getString();
			File userFile = new File(System.getProperty("user.home"), "dgfacebot/userdata/"+username);
			userPath = userFile.getPath();

			if (!userFile.exists()) {
				userFile.setWritable(true);
				if (userFile.mkdirs()) {
					LOGGER.debug("made a user dir");
				}
			} else {
				File tokenFile = new File(userPath, "accessToken.ser");
				loadAccessToken(tokenFile);
			}
			NodeBuilder builder = node.createChild("logout");
			builder.setAction(new Action(Permission.READ, new LogoutHandler()));
			builder.build();
			builder = node.createChild("deleteUserAccount");
			builder.setAction(new Action(Permission.READ, new AccountDeleteHandler()));
			builder.build();
			
			node.removeChild("login");
			
			if (accessToken != null && accessToken.getExpires() > 0) {
				connect();
				return;
			}
			
			String authurl = facebook.getOAuthAuthorizationURL("https://www.facebook.com/connect/login_success.html");
			authurl = authurl + "&response_type=token";
			builder = node.createChild("Authorization URL");
			builder.setValue(new Value(authurl));
			builder.build();
			Action act = new Action(Permission.READ, new AuthHandler());
			act.addParameter(new Parameter("redirectUrl", ValueType.STRING));
			node.createChild("Authorize").setAction(act).build();
		}
	}
	
	private class AuthHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String urlstring = event.getParameter("redirectUrl", ValueType.STRING).getString();
			String accessTokenString = urlstring.split("access_token=")[1].split("[/?&;]")[0];
			long expires = Long.parseLong(urlstring.split("expires_in=")[1].split("[/?&;]")[0]);
			accessToken = new AccessToken(accessTokenString, expires);
			saveAccessToken(new File(userPath, "accessToken.ser"));
			node.removeChild("Authorize");
			connect();	
		}
	}
	
	private void connect() {
		facebook.setOAuthAccessToken(accessToken);
		clearErrorMsgs();
		Action act = new Action(Permission.READ, new NewsFeedHandler());
		node.createChild("GetNewsFeed").setAction(act).build();
		act = new Action(Permission.READ, new BasicInfoHandler());
		node.createChild("GetBasicInfo").setAction(act).build();
		act = new Action(Permission.READ, new PostHandler());
		act.addParameter(new Parameter("text", ValueType.STRING));
		node.createChild("post status").setAction(act).build();
		act = new Action(Permission.READ, new SearchHandler());
		act.addParameter(new Parameter("type", ValueType.STRING));
		act.addParameter(new Parameter("query", ValueType.STRING));
		act.addParameter(new Parameter("center latitude", ValueType.NUMBER));
		act.addParameter(new Parameter("center longitude", ValueType.NUMBER));
		act.addParameter(new Parameter("distance", ValueType.NUMBER));
		node.createChild("Search").setAction(act).build();
		act = new Action(Permission.READ, new RawSearchHandler());
		act.addParameter(new Parameter("query", ValueType.STRING));
		node.createChild("RawSearch").setAction(act).build();
		act = new Action(Permission.READ, new RawCallHandler());
		act.addParameter(new Parameter("type", ValueType.STRING));
		act.addParameter(new Parameter("query", ValueType.STRING));
		node.createChild("RawAPICall").setAction(act).build();
	}
	
	private void clearErrorMsgs() {
		if (err.getChildren() == null) return;
		for (Node child: err.getChildren().values()) {
			err.removeChild(child);
		}
	}
	
	
	private class PostHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String statusText = event.getParameter("text", ValueType.STRING).getString();
			try {
				facebook.postStatusMessage(statusText);
			} catch (FacebookException e) {
				LOGGER.debug("error", e);
				accountDelete();
				NodeBuilder builder = err.createChild("fb error message");
				builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
				builder.build();
			}

		}
	}
	
	private class NewsFeedHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			try {
				String feed = DataObjectFactory.getRawJSON(facebook.getHome());
				NodeBuilder builder = node.createChild("NewsFeed");
				builder.setValue(new Value(feed));
				builder.build();
				
			} catch (FacebookException e) {
				LOGGER.debug("error", e);
				accountDelete();
				NodeBuilder builder = err.createChild("fb error message");
				builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
				builder.build();
			}
		}
	}
	
	private class BasicInfoHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			try {
				String me = DataObjectFactory.getRawJSON(facebook.getMe());
				
				NodeBuilder builder = node.createChild("basicInfo");
				builder.setValue(new Value(me));
				builder.build();	
				
			} catch (FacebookException e) {
				LOGGER.debug("error", e);
				accountDelete();
				NodeBuilder builder = err.createChild("fb error message");
				builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
				builder.build();
			}
		}
	}

	
	private enum SearchType { PAGE, USER, EVENT, GROUP, PLACE };
	
	private String doSearch(SearchType type, String query, GeoLocation center, Integer distance) {
		String raw = null;
		try {
			switch (type) {
			case USER: raw = DataObjectFactory.getRawJSON(facebook.searchUsers(query)); break;
			case PAGE: raw = DataObjectFactory.getRawJSON(facebook.searchPages(query)); break;
			case EVENT: raw = DataObjectFactory.getRawJSON(facebook.searchEvents(query)); break;
			case GROUP: raw = DataObjectFactory.getRawJSON(facebook.searchGroups(query)); break;
			case PLACE: if (center == null || distance == null ) {
							raw = DataObjectFactory.getRawJSON(facebook.searchPlaces(query));
						} else {
							raw = DataObjectFactory.getRawJSON(facebook.searchPlaces(query, center, distance));
						}
						break;
			}
		} catch (FacebookException e) {
			LOGGER.debug("error", e);
			accountDelete();
			NodeBuilder builder = err.createChild("fb error message");
			builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
			builder.build();
		}
		JsonObject j;
		boolean correctFormat = true;
		try {
			j = new JsonObject(raw);
			if (!j.containsField("data")) correctFormat = false;
		} catch (DecodeException e) {
			correctFormat = false;
		}
		if (correctFormat) return raw;
		else return "{ \"data\": "+ raw + "}";
	}
		
	private enum ApiType {GET, POST, DELETE};
	
	private String makeRawAPICall(ApiType type, String call) {
		String result = null;
		try {
			RawAPIResponse res;
			switch (type) {
			case GET: res = facebook.callGetAPI(call); break;
			case POST: res = facebook.callPostAPI(call); break;
			case DELETE: res = facebook.callDeleteAPI(call); break;
			default: res = null; break;
			}
			if (res == null) {
			} else if (res.isJSONArray()) {
				result = res.asJSONArray().toString();
			} else if (res.isJSONObject()) {
				result = res.asJSONObject().toString();
			} else if (res.isBoolean()) {
				result = String.valueOf(res.asBoolean());
			} else {
				result = res.asString();
			}
		} catch (FacebookException e) {
			LOGGER.debug("error", e);;
			if (e.getErrorType().equals("OAuthException")) {
				accountDelete();
				NodeBuilder builder = err.createChild("oauth error message");
				builder.setValue(new Value("OAuth error has occured. Logging out and deleting user data. Please login and reauthorize"));
				builder.build();
			} else {
				NodeBuilder builder = err.createChild("facebook api error message");
				builder.setValue(new Value("Invalid Query"));
				builder.build();
			}
		}
		
		return result;
	}
	
	private class RawSearchHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			ValueType vt = ValueType.STRING;
			String query = event.getParameter("query", vt).getString();
			String result = makeRawAPICall(ApiType.GET, "search?"+query);
			Node sr = node.getChild("SearchResults");
			if (sr == null) {
				NodeBuilder builder = node.createChild("SearchResults");
				builder.setValue(new Value(result));
				builder.build();
			} else {
				sr.setValue(new Value(result));
			}
			
		}
	}
	
	private class RawCallHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			ValueType vt = ValueType.STRING;
			ApiType type = ApiType.valueOf(event.getParameter("type", vt).getString());
			String query = event.getParameter("query", vt).getString();
			String result = makeRawAPICall(type, query);
			Node res = node.getChild("RawResponse");
			if (res == null) {
				NodeBuilder builder = node.createChild("RawResponse");
				builder.setValue(new Value(result));
				builder.build();
			} else {
				res.setValue(new Value(result));
			}
		}
	}
	
	private class SearchHandler implements Handler<ActionResult> {
		
		public void handle(ActionResult event) {
			ValueType vt = ValueType.STRING;
			SearchType type = SearchType.valueOf(event.getParameter("type", vt).getString());
			String query = event.getParameter("query", vt).getString();
			Integer distance = null;
			GeoLocation center = null;
			if (type == SearchType.PLACE) {
				Value lati = event.getParameter("center latitude");
				Value longi = event.getParameter("center longitude");
				if (lati != null && longi != null) {
					center = new GeoLocation(lati.getNumber().doubleValue(), longi.getNumber().doubleValue());
				}
				Value dist = event.getParameter("distance");
				if (dist != null) distance = dist.getNumber().intValue();
			}
			String raw = doSearch(type, query, center, distance);
			Node sr = node.getChild("SearchResults");
			if (sr == null) {
				NodeBuilder builder = node.createChild("SearchResults");
				builder.setValue(new Value(raw));
				builder.build();
			} else {
				sr.setValue(new Value(raw));
			}
		}
	}
	

	
	private class LogoutHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			logout();
		}
	}
	
	private void logout() {
		facebook = null;
		accessToken = null;
		userPath = null;
		if (node.getChildren() != null) {
			for (Node child: node.getChildren().values()) {
				node.removeChild(child);
			}
		}
		init();
	}
	
	private class AccountDeleteHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			accountDelete();
		}
	}
	
	private void accountDelete() {
		File toDelete = new File(userPath);
		boolean failed = false;
		for (File f: toDelete.listFiles()) {
			if (!f.delete()) {
				failed = true;
			}
		}
		if (failed || !toDelete.delete()) {
			logout();
			NodeBuilder builder = err.createChild("deletion error message");
			builder.setValue(new Value("Account deletion failed. logging out."));
			builder.build();
		} else {
			logout();
		}
	}
	
	private void saveAccessToken(File file) {
		if (accessToken == null) {
            return;
        }
        try {
            FileOutputStream fileOut = new FileOutputStream(file);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(accessToken);
            objectOut.close();
        } catch (IOException e) {
            String msg = "IOException while saving accessToken.";
            LOGGER.error(msg);
        }
	}
	
	private void loadAccessToken(File file) {
        if (file.exists()) {
            try {
                FileInputStream fileIn = new FileInputStream(file);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                accessToken = (AccessToken) objectIn.readObject();
                objectIn.close();
            } catch (IOException e) {
                String msg = "IOException while loading accessToken.";
                LOGGER.error(msg);
            } catch (ClassNotFoundException e) {
                String msg = "ClassNotFoundException while loading accessToken.";
                LOGGER.error(msg);
            }
        }
	}

}
