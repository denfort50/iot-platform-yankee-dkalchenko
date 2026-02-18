package ru.dkalchenko.device.collector.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import ru.dkalchenko.device.collector.metrics.CollectorMetrics;
import ru.dkalchenko.device.collector.service.DeviceService;
import ru.dkalchenko.device.collector.tracing.KafkaTraceContextRunner;

/**
 * Kafka Consumer для обработки сообщений об устройствах из топика devices.v1.
 * trace_id берётся из заголовка traceparent того же сообщения (парсится в KafkaTraceContextRunner).
 * Обрабатывает ошибки и отправляет проблемные сообщения в Dead Letter Topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceListener {

	private static final String SPAN_NAME = "save-device-postgres";

	DeviceService deviceService;
	CollectorMetrics metrics;
	KafkaTraceContextRunner kafkaTraceContextRunner;

	@KafkaListener(
			topics = "${device-collector.listener.kafka-topic:devices.v1}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "kafkaListenerContainerFactory"
	)
	public void handleDeviceMessage(ConsumerRecord<String, GenericRecord> record, Acknowledgment acknowledgment) {
		String key = record.key() != null ? record.key() : "";

		try {
			kafkaTraceContextRunner.runInSpan(record.headers(), key, SPAN_NAME, () -> {
				log.trace("Received device message: key={}, partition={}, offset={}",
						key, record.partition(), record.offset());
				deviceService.upsertDevice(record.value());
				acknowledgment.acknowledge();
				metrics.incrementDevicesProcessed(key);
			});
			log.debug("Device processed successfully: key={}, partition={}, offset={}",
					key, record.partition(), record.offset());
		} catch (IllegalArgumentException e) {
			log.error("Invalid device data: key={}, partition={}, offset={}, error={}",
					key, record.partition(), record.offset(), e.getMessage());
			metrics.incrementDevicesFailed(key);
			throw e;
		} catch (Exception e) {
			log.error("Error processing device message: key={}, partition={}, offset={}",
					key, record.partition(), record.offset(), e);
			metrics.incrementDevicesFailed(key);
			throw new RuntimeException("Failed to process device message", e);
		}
	}
}
