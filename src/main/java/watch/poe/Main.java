package poe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        App app = new App(args);

        try {
            app.run();
        } catch (Exception ex) {
            logger.error("Exception in app", ex);
        } finally {
            app.stop();
        }
    }
}
