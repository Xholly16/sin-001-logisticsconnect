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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subscribes to package-status-topic and remembers the latest stage per hub,
 * so an ETA request is answered from memory with no call to delay-stage-service.
 */
public class MqStageSource implements StageSource, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MqStageSource.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Integer> latestStages = new ConcurrentHashMap<>();
    private final Connection connection;

    public MqStageSource() throws JMSException {
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
                StageEvent event = mapper.readValue(text.getText(), StageEvent.class);
                latestStages.put(event.hubId().trim().toUpperCase(), event.stage());
                LOG.info("Received stage {} for {}", event.stage(), event.hubId());
            }
        } catch (Exception e) {
            LOG.warn("Ignoring unreadable stage message: {}", e.getMessage());
        }
    }

    /** A hub we have not heard about yet is treated as stage 0 (no delay). */
    @Override
    public int stageFor(String hubId) {
        return latestStages.getOrDefault(hubId, 0);
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
