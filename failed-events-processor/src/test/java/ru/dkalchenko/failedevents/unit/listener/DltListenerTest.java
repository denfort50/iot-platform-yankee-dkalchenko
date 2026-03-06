package ru.dkalchenko.failedevents.unit.listener;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import ru.dkalchenko.failedevents.listener.DltListener;
import ru.dkalchenko.failedevents.metrics.FailedEventsMetrics;
import ru.dkalchenko.failedevents.model.FailedDeviceJson;
import ru.dkalchenko.failedevents.model.FailedEventJson;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;
import ru.dkalchenko.failedevents.service.DltPayloadParser;
import ru.dkalchenko.failedevents.service.MinioStorageService;
import ru.dkalchenko.failedevents.tracing.DltTraceContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DltListenerTest {

	@Mock
	MinioStorageService minioStorageService;
	@Mock
	FailedEventsMetrics metrics;
	@Mock
	DltTraceContextRunner traceContextRunner;

	DltListener listener;

	@BeforeEach
	void setUp() {
		FailedEventsProcessorProperties properties = new FailedEventsProcessorProperties();
		DltPayloadParser payloadParser = new DltPayloadParser();
		listener = new DltListener(properties, payloadParser, minioStorageService, metrics, traceContextRunner);

		lenient().doAnswer(invocation -> {
			Runnable action = invocation.getArgument(1);
			action.run();
			return null;
		}).when(traceContextRunner).runInSpan(any(), any());
	}

	@Test
	void whenValueIsNullThenInvalidMetricAndAck() {
		ConsumerRecord<String, GenericRecord> record =
				new ConsumerRecord<>("dlt.v1", 0, 0L, "key", null);
		Acknowledgment ack = Mockito.mock(Acknowledgment.class);

		listener.handleDltMessage(record, ack);

		verify(metrics).incrementDltReceivedInvalid();
		verify(ack).acknowledge();
	}

	@Test
	void whenParsedEventThenSavedToEventsBucketAndMetricIncremented() {
		GenericRecord value = Mockito.mock(GenericRecord.class);
		when(value.get("originalValue")).thenReturn("{\"eventId\":\"e1\",\"type\":\"TEMPERATURE\"}");
		when(value.get("service")).thenReturn("svc");
		when(value.get("errorMessage")).thenReturn("err");
		when(value.get("stackTrace")).thenReturn("");
		when(value.get("traceId")).thenReturn("0123456789abcdef0123456789abcdef");

		ConsumerRecord<String, GenericRecord> record =
				new ConsumerRecord<>("dlt.v1", 0, 0L, "key", value);
		Acknowledgment ack = Mockito.mock(Acknowledgment.class);

		listener.handleDltMessage(record, ack);

		verify(minioStorageService).saveFailedEvent(any(FailedEventJson.class), eq("TEMPERATURE"));
		verify(metrics).incrementFailedEventSaved();
		verify(ack).acknowledge();
	}

	@Test
	void whenParsedDeviceThenSavedToDevicesBucketAndMetricIncremented() {
		GenericRecord value = Mockito.mock(GenericRecord.class);
		when(value.get("originalValue")).thenReturn("{\"operation\":\"CREATE\"}");
		when(value.get("service")).thenReturn("svc");
		when(value.get("errorMessage")).thenReturn("err");
		when(value.get("stackTrace")).thenReturn("");
		when(value.get("traceId")).thenReturn(null);

		ConsumerRecord<String, GenericRecord> record =
				new ConsumerRecord<>("dlt.v1", 0, 0L, "key", value);
		Acknowledgment ack = Mockito.mock(Acknowledgment.class);

		listener.handleDltMessage(record, ack);

		verify(minioStorageService).saveFailedDevice(any(FailedDeviceJson.class), eq("CREATE"));
		verify(metrics).incrementFailedDeviceSaved();
		verify(ack).acknowledge();
	}

	@Test
	void whenParsedUnknownThenUnknownMetricIncrementedAndAck() {
		GenericRecord value = Mockito.mock(GenericRecord.class);
		when(value.get("originalValue")).thenReturn("{\"foo\":\"bar\"}");
		when(value.get("service")).thenReturn("svc");
		when(value.get("errorMessage")).thenReturn("err");
		when(value.get("stackTrace")).thenReturn("");
		when(value.get("traceId")).thenReturn(null);

		ConsumerRecord<String, GenericRecord> record =
				new ConsumerRecord<>("dlt.v1", 0, 0L, "key", value);
		Acknowledgment ack = Mockito.mock(Acknowledgment.class);

		listener.handleDltMessage(record, ack);

		verify(metrics).incrementDltReceivedUnknown();
		verify(ack).acknowledge();
	}

	@Test
	void whenProcessingErrorThenProcessingErrorMetricIncrementedAndExceptionThrown() {
		GenericRecord value = Mockito.mock(GenericRecord.class);
		when(value.get("originalValue")).thenReturn("{\"eventId\":\"e1\"}");
		when(value.get("service")).thenReturn("svc");
		when(value.get("errorMessage")).thenReturn("err");
		when(value.get("stackTrace")).thenReturn("");
		when(value.get("traceId")).thenReturn("0123456789abcdef0123456789abcdef");

		doThrow(new RuntimeException("MinIO failure"))
				.when(minioStorageService).saveFailedEvent(any(), any());

		ConsumerRecord<String, GenericRecord> record =
				new ConsumerRecord<>("dlt.v1", 0, 0L, "key", value);
		Acknowledgment ack = Mockito.mock(Acknowledgment.class);

		assertThatThrownBy(() -> listener.handleDltMessage(record, ack))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Failed to process DLT message");

		verify(metrics).incrementProcessingError();
	}

	@Test
	void getDltTopicReturnsFromProperties() {
		String topic = listener.getDltTopic();
		assertThat(topic).isEqualTo("dlt.v1");
	}
}

