package facebook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.GeoLocation;
import facebook4j.RawAPIResponse;
import facebook4j.auth.AccessToken;
import facebook4j.conf.ConfigurationBuilder;
import facebook4j.json.DataObjectFactory;

public class FacebookConn {
    private static final Logger LOGGER;

    static {
        LOGGER = LoggerFactory.getLogger(FacebookConn.class);
    }

    private final FacebookLink link;
    private final Node node;

    private Facebook facebook;
    private AccessToken accessToken;
    /* Add any necessary permissions here */

    FacebookConn(FacebookLink link, Node node) {
        this.link = link;
        this.node = node;

        Action act = new Action(Permission.READ, new Handler<ActionResult>() {
            public void handle(ActionResult event) {
                remove();
            }
        });
        node.createChild("remove").setAction(act).build().setSerializable(false);
    }

    void init() {

        String appId = node.getAttribute("app id").getString();
        String appSecret = node.getAttribute("app secret").getString();
        String permissions = node.getAttribute("permissions").getString();

        Action act = new Action(Permission.READ, new EditHandler());
        act.addParameter(new Parameter("name", ValueType.STRING, new Value(node.getName())));
        act.addParameter(new Parameter("app id", ValueType.STRING, new Value(appId)));
        act.addParameter(new Parameter("app secret", ValueType.STRING, new Value(appSecret)));
        act.addParameter(new Parameter("permissions", ValueType.STRING, new Value(permissions)));
        Node anode = node.getChild("edit");
        if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
        else anode.setAction(act);

        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthAppId(appId)
                .setOAuthAppSecret(appSecret)
                .setOAuthPermissions(permissions)
                .setJSONStoreEnabled(true);
        facebook = new FacebookFactory(cb.build()).getInstance();

        Value token = node.getAttribute("access token");
        Value expires = node.getAttribute("access token expires");
        if (token != null && expires != null)
            accessToken = new AccessToken(token.getString(), expires.getNumber().longValue());
        else accessToken = null;

        if (accessToken != null && accessToken.getExpires() < System.currentTimeMillis()) {
            connect();
            return;
        }

        String authurl = facebook.getOAuthAuthorizationURL("https://www.facebook.com/connect/login_success.html");
        authurl = authurl + "&response_type=token";
        node.createChild("Authorization URL").setValueType(ValueType.STRING).setValue(new Value(authurl)).build();

        act = new Action(Permission.READ, new AuthHandler());
        act.addParameter(new Parameter("redirect url", ValueType.STRING));
        node.createChild("Authorize").setAction(act).build().setSerializable(false);

    }

    private class AuthHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String urlstring = event.getParameter("redirect url", ValueType.STRING).getString();
            String accessTokenString = urlstring.split("access_token=")[1].split("[/?&;]")[0];
            long expires = Long.parseLong(urlstring.split("expires_in=")[1].split("[/?&;]")[0]);
            accessToken = new AccessToken(accessTokenString, expires);
            node.removeChild("Authorize");
            node.removeChild("Authorization URL");
            connect();
        }
    }

    private class EditHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String name = event.getParameter("name", ValueType.STRING).getString();
            String appId = event.getParameter("app id", ValueType.STRING).getString();
            String appSecret = event.getParameter("app secret", ValueType.STRING).getString();
            String permissions = event.getParameter("permissions", new Value("")).getString();

            node.setAttribute("app id", new Value(appId));
            node.setAttribute("app secret", new Value(appSecret));
            node.setAttribute("permissions", new Value(permissions));

            if (name != null && !name.equals(node.getName())) {
                rename(name);
            } else {
                init();
            }
        }
    }

    private void rename(String name) {
        JsonObject jobj = link.copySerializer.serialize();
        JsonObject nodeobj = jobj.get(node.getName());
        jobj.put(name, nodeobj);
        link.copyDeserializer.deserialize(jobj);
        Node newnode = node.getParent().getChild(name);
        FacebookConn fc = new FacebookConn(link, newnode);
        remove();
        fc.restoreLastSession();
    }

    private void connect() {
        node.setAttribute("access token", new Value(accessToken.getToken()));
        node.setAttribute("access token expires", new Value(accessToken.getExpires()));
        facebook.setOAuthAccessToken(accessToken);
        Action act = new Action(Permission.READ, new NewsFeedHandler());
        act.addParameter(new Parameter("result name", ValueType.STRING));
        node.createChild("get newsfeed").setAction(act).build().setSerializable(false);
        act = new Action(Permission.READ, new BasicInfoHandler());
        act.addParameter(new Parameter("result name", ValueType.STRING));
        node.createChild("get basic info").setAction(act).build().setSerializable(false);
        act = new Action(Permission.READ, new PostHandler());
        act.addParameter(new Parameter("text", ValueType.STRING));
        node.createChild("post status").setAction(act).build().setSerializable(false);
        act = new Action(Permission.READ, new SearchHandler());
        act.addParameter(new Parameter("result name", ValueType.STRING));
        Set<String> enums = new HashSet<String>();
        for (SearchType st : SearchType.values()) enums.add(st.toString());
        act.addParameter(new Parameter("type", ValueType.makeEnum(enums)));
        act.addParameter(new Parameter("query", ValueType.STRING));
        act.addParameter(new Parameter("center latitude", ValueType.NUMBER));
        act.addParameter(new Parameter("center longitude", ValueType.NUMBER));
        act.addParameter(new Parameter("distance", ValueType.NUMBER));
        node.createChild("search").setAction(act).build().setSerializable(false);
        act = new Action(Permission.READ, new RawSearchHandler());
        act.addParameter(new Parameter("result name", ValueType.STRING));
        act.addParameter(new Parameter("query", ValueType.STRING));
        node.createChild("raw search").setAction(act).build().setSerializable(false);
        act = new Action(Permission.READ, new RawCallHandler());
        act.addParameter(new Parameter("result name", ValueType.STRING));
        enums = new HashSet<String>();
        for (ApiType at : ApiType.values()) enums.add(at.toString());
        act.addParameter(new Parameter("type", ValueType.makeEnum(enums)));
        act.addParameter(new Parameter("query", ValueType.STRING));
        node.createChild("raw API call").setAction(act).build().setSerializable(false);

        act = new Action(Permission.READ, new Handler<ActionResult>() {
            public void handle(ActionResult event) {
                stop();
                init();
            }
        });
        node.createChild("disconnect").setAction(act).build().setSerializable(false);
    }

    private void stop() {
        accessToken = null;
        node.removeAttribute("access token");
        node.removeAttribute("access token expires");

        node.removeChild("get newsfeed");
        node.removeChild("get basic info");
        node.removeChild("post status");
        node.removeChild("search");
        node.removeChild("raw search");
        node.removeChild("raw API call");
        node.removeChild("disconnect");
    }

    private void remove() {
        stop();
        node.clearChildren();
        node.getParent().removeChild(node);
    }

    private class PostHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String statusText = event.getParameter("text", ValueType.STRING).getString();
            try {
                facebook.postStatusMessage(statusText);
            } catch (FacebookException e) {
                LOGGER.debug("error", e);
//				accountDelete();
//				NodeBuilder builder = err.createChild("fb error message");
//				builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
//				builder.build();
            }

        }
    }

    private class NewsFeedHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String name = event.getParameter("result name", ValueType.STRING).getString();
            try {
                String feed = DataObjectFactory.getRawJSON(facebook.getHome());
                Node n = node.createChild(name).setValueType(ValueType.STRING).setValue(new Value(feed)).build();
                n.setAttribute("result type", new Value("news feed"));
                new FacebookResult(getMe(), n, null);
            } catch (FacebookException e) {
                LOGGER.debug("error", e);
//				accountDelete();
//				NodeBuilder builder = err.createChild("fb error message");
//				builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
//				builder.build();
            }
        }
    }

    private class BasicInfoHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String name = event.getParameter("result name", ValueType.STRING).getString();
            try {
                String me = DataObjectFactory.getRawJSON(facebook.getMe());

                Node n = node.createChild(name).setValueType(ValueType.STRING).setValue(new Value(me)).build();
                n.setAttribute("result type", new Value("basic info"));
                new FacebookResult(getMe(), n, null);
            } catch (FacebookException e) {
                LOGGER.debug("error", e);
//				accountDelete();
//				NodeBuilder builder = err.createChild("fb error message");
//				builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
//				builder.build();
            }
        }
    }


    private enum SearchType {PAGE, USER, EVENT, GROUP, PLACE}

    ;

    private String doSearch(SearchType type, String query, GeoLocation center, Integer distance) {
        String raw = null;
        try {
            switch (type) {
                case USER:
                    raw = DataObjectFactory.getRawJSON(facebook.searchUsers(query));
                    break;
                case PAGE:
                    raw = DataObjectFactory.getRawJSON(facebook.searchPages(query));
                    break;
                case EVENT:
                    raw = DataObjectFactory.getRawJSON(facebook.searchEvents(query));
                    break;
                case GROUP:
                    raw = DataObjectFactory.getRawJSON(facebook.searchGroups(query));
                    break;
                case PLACE:
                    if (center == null || distance == null) {
                        raw = DataObjectFactory.getRawJSON(facebook.searchPlaces(query));
                    } else {
                        raw = DataObjectFactory.getRawJSON(facebook.searchPlaces(query, center, distance));
                    }
                    break;
            }
        } catch (FacebookException e) {
            LOGGER.debug("error", e);
//			accountDelete();
//			NodeBuilder builder = err.createChild("fb error message");
//			builder.setValue(new Value("Facebook error has occured. Logging out and deleting user data. Please login and reauthorize"));
//			builder.build();
        }
        JsonObject j;
        boolean correctFormat = true;
        try {
            j = new JsonObject(raw);
            if (!j.contains("data")) correctFormat = false;
        } catch (Exception e) {
            correctFormat = false;
        }
        if (correctFormat) return raw;
        else return "{ \"data\": " + raw + "}";
    }

    private enum ApiType {GET, POST, DELETE}

    ;

    private String makeRawAPICall(ApiType type, String call) {
        String result = null;
        try {
            RawAPIResponse res;
            switch (type) {
                case GET:
                    res = facebook.callGetAPI(call);
                    break;
                case POST:
                    res = facebook.callPostAPI(call);
                    break;
                case DELETE:
                    res = facebook.callDeleteAPI(call);
                    break;
                default:
                    res = null;
                    break;
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
            LOGGER.debug("error", e);
            ;
            if (e.getErrorType().equals("OAuthException")) {
//				accountDelete();
//				NodeBuilder builder = err.createChild("oauth error message");
//				builder.setValue(new Value("OAuth error has occured. Logging out and deleting user data. Please login and reauthorize"));
//				builder.build();
            } else {
//				NodeBuilder builder = err.createChild("facebook api error message");
//				builder.setValue(new Value("Invalid Query"));
//				builder.build();
            }
        }

        return result;
    }

    private class RawSearchHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String name = event.getParameter("result name", ValueType.STRING).getString();
            Value query = event.getParameter("query", ValueType.STRING);
            Map<String, Value> params = new HashMap<String, Value>();
            params.put("query", query);
            String result = makeRawAPICall(ApiType.GET, "search?" + query.getString());
            Node n = node.createChild(name).setValueType(ValueType.STRING).setValue(new Value(result)).build();
            n.setAttribute("result type", new Value("raw search"));
            new FacebookResult(getMe(), n, params);
        }
    }

    private class RawCallHandler implements Handler<ActionResult> {
        public void handle(ActionResult event) {
            String name = event.getParameter("result name", ValueType.STRING).getString();
            Value typeval = event.getParameter("type");
            Value queryval = event.getParameter("query", ValueType.STRING);
            Map<String, Value> params = new HashMap<String, Value>();
            params.put("type", typeval);
            params.put("query", queryval);
            ApiType type = ApiType.valueOf(typeval.getString());
            String query = queryval.getString();
            String result = makeRawAPICall(type, query);
            Node n = node.createChild(name).setValueType(ValueType.STRING).setValue(new Value(result)).build();
            n.setAttribute("result type", new Value("raw api call"));
            new FacebookResult(getMe(), n, params);
        }
    }

    private class SearchHandler implements Handler<ActionResult> {

        public void handle(ActionResult event) {
            String name = event.getParameter("result name", ValueType.STRING).getString();

            Value typeval = event.getParameter("type");
            Value queryval = event.getParameter("query", ValueType.STRING);
            Value lati = event.getParameter("center latitude");
            Value longi = event.getParameter("center longitude");
            Value dist = event.getParameter("distance");

            Map<String, Value> params = new HashMap<String, Value>();
            params.put("type", typeval);
            params.put("query", queryval);
            params.put("center latitude", lati);
            params.put("center longitude", longi);
            params.put("distance", dist);

            SearchType type = SearchType.valueOf(typeval.getString());
            String query = queryval.getString();
            Integer distance = null;
            GeoLocation center = null;
            if (type == SearchType.PLACE) {
                if (lati != null && longi != null) {
                    center = new GeoLocation(lati.getNumber().doubleValue(), longi.getNumber().doubleValue());
                }
                if (dist != null) distance = dist.getNumber().intValue();
            }
            String raw = doSearch(type, query, center, distance);

            Node n = node.createChild(name).setValueType(ValueType.STRING).setValue(new Value(raw)).build();
            n.setAttribute("result type", new Value("search"));
            new FacebookResult(getMe(), n, params);
        }
    }

    void restoreLastSession() {
        init();
        if (node.getChildren() == null) return;
        for (Node child : node.getChildren().values()) {
            if (child.getAttribute("result type") != null) {
                final Node resNode = child;
                Action act = new Action(Permission.READ, new Handler<ActionResult>() {
                    public void handle(ActionResult event) {
                        resNode.clearChildren();
                        node.removeChild(resNode);
                    }
                });
                resNode.createChild("remove").setAction(act).build().setSerializable(false);
            } else if (child.getAction() == null && !"Authorization URL".equals(child.getName())) {
                node.removeChild(child);
            }
        }
    }

    private FacebookConn getMe() {
        return this;
    }

}
