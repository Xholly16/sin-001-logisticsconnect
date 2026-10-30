package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.time.Instant;

/**
 * Publishes stage changes to the ActiveMQ topic. The connection is opened lazily on the
 * first publish, so delay-stage-service can still start (and serve REST) if the broker is down.
 */
public class StagePublisher {

    private final ObjectMapper mapper = new ObjectMapper();
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    // synchronized: a JMS Session must not be used by two threads at once,
    // and Javalin handles requests on several threads.
    public synchronized void publish(String hubId, int stage) throws JMSException, JsonProcessingException {
        String json = mapper.writeValueAsString(new StageEvent(hubId, stage, Instant.now().toString()));
        try {
            connect();
            producer.send(session.createTextMessage(json));
        } catch (JMSException e) {
            close(); // throw the broken connection away so the next publish reconnects
            throw e;
        }
    }

    private void connect() throws JMSException {
        if (connection != null) {
            return;
        }
        connection = new ActiveMQConnectionFactory(MqConfig.BROKER_URL).createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        producer = session.createProducer(session.createTopic(MqConfig.TOPIC));
    }

    public synchronized void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException ignored) {
            // nothing useful to do while closing
        } finally {
            connection = null;
            session = null;
            producer = null;
        }
    }
}
