package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

/** Listens to package-status-topic and hands every stage update to the AlertService. */
public class StageSubscriber implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StageSubscriber.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AlertService alerts;
    private final Connection connection;

    public StageSubscriber(AlertService alerts) throws JMSException {
        this.alerts = alerts;
        connection = new ActiveMQConnectionFactory(MqConfig.BROKER_URL).createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(session.createTopic(MqConfig.TOPIC));
        consumer.setMessageListener(this::onMessage);
        connection.start();
        LOG.info("Subscribed to {} at {}", MqConfig.TOPIC, MqConfig.BROKER_URL);
    }

    private void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                alerts.onStageUpdate(mapper.readValue(text.getText(), StageEvent.class));
            }
        } catch (Exception e) {
            LOG.warn("Ignoring unreadable stage message: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
