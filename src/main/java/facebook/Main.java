package facebook;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkFactory;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.serializer.Deserializer;
import org.dsa.iot.dslink.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main extends DSLinkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        DSLinkFactory.start(args, new Main());
    }

    @Override
    public boolean isResponder() {
        return true;
    }

    @Override
    public void onResponderInitialized(DSLink link) {
        LOGGER.info("Initialized");

        NodeManager manager = link.getNodeManager();
        Node superRoot = manager.getNode("/").getNode();
        Serializer copyser = new Serializer(manager);
        Deserializer copydeser = new Deserializer(manager);
        FacebookLink.start(superRoot, copyser, copydeser);
    }

    public void onResponderConnected(DSLink link) {
        LOGGER.info("Connected");
    }

}
