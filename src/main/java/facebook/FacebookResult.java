package facebook;

import java.util.Map;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.vertx.java.core.Handler;

public class FacebookResult {
	
	private Node node;
//	private FacebookConn conn;
	
	FacebookResult(FacebookConn conn, Node rnode, Map<String, Value> params) {
//		this.conn = conn;
		this.node = rnode;
		
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				node.clearChildren();
				node.getParent().removeChild(node);
			}
		});
		node.createChild("remove").setAction(act).build().setSerializable(false);
		
		node.createChild("result type").setValueType(ValueType.STRING).setValue(node.getAttribute("result type")).build();
		
		if (params != null && params.size() > 0) {
			Node pnode = node.createChild("parameters").build();
			for (Map.Entry<String, Value> entry: params.entrySet()) {
				Value val = entry.getValue();
				ValueType vt = ValueType.STRING;
				if (val != null) vt = val.getType();
				pnode.createChild(entry.getKey()).setValueType(vt).setValue(val).build();
			}
		}
		
	}
	
}
