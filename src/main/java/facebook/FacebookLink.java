package facebook;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.serializer.Deserializer;
import org.dsa.iot.dslink.serializer.Serializer;
import org.vertx.java.core.Handler;

public class FacebookLink {
//	private static final Logger LOGGER;
//	static {
//		LOGGER = LoggerFactory.getLogger(FacebookLink.class);
//	}
	
	private Node node;
	final Serializer copySerializer;
	final Deserializer copyDeserializer;
	
//	private String userPath;
	 
	
	private FacebookLink(Node node, Serializer ser, Deserializer deser) {
		this.node = node;
		this.copySerializer = ser;
		this.copyDeserializer = deser;
	}
	
	public static void start(Node parent, Serializer copyser, Deserializer copydeser) {
		Node node = parent;
		final FacebookLink face = new FacebookLink(node, copyser, copydeser);
		face.init();
	}
	
	private void init() {
		restoreLastSession();
		
		Action act = new Action(Permission.READ, new ConnectHandler());
		act.addParameter(new Parameter("name", ValueType.STRING));
		act.addParameter(new Parameter("app id", ValueType.STRING, new Value("1625793730970939")));
		act.addParameter(new Parameter("app secret", ValueType.STRING, new Value("023a35e161c4187af323eef166bc8e16")));
		act.addParameter(new Parameter("permissions", ValueType.STRING, new Value("publish_actions, read_stream")));
		node.createChild("add connection").setAction(act).build().setSerializable(false);;		
	}
	
	
	private void restoreLastSession() {
		if (node.getChildren() == null) return;
		for (Node child: node.getChildren().values()) {
			Value appId = child.getAttribute("app id");
			Value appSecret = child.getAttribute("app secret");
			Value permissions = child.getAttribute("permissions");
			if (appId!=null && appSecret!=null && permissions!=null) {
				FacebookConn fc = new FacebookConn(getMe(), child);
				fc.restoreLastSession();
			} else {
				node.removeChild(child);
			}
		}
	}


	private class ConnectHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			
			String name = event.getParameter("name", ValueType.STRING).getString();
			String appId = event.getParameter("app id", ValueType.STRING).getString();
			String appSecret = event.getParameter("app secret", ValueType.STRING).getString();
			String permissions = event.getParameter("permissions", new Value("")).getString();
			
			Node child = node.createChild(name).build();
			child.setAttribute("app id", new Value(appId));
			child.setAttribute("app secret", new Value(appSecret));
			child.setAttribute("permissions", new Value(permissions));
			
			FacebookConn fc = new FacebookConn(getMe(), child);
			fc.init();
			
		}
	}
	
	public FacebookLink getMe() {
		return this;
	}

}
