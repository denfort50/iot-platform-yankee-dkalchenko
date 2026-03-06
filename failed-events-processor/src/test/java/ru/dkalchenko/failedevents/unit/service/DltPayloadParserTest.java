package ru.dkalchenko.failedevents.unit.service;

import org.junit.jupiter.api.Test;
import ru.dkalchenko.failedevents.model.FailedDeviceJson;
import ru.dkalchenko.failedevents.model.FailedEventJson;
import ru.dkalchenko.failedevents.service.DltPayloadParser;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedDevice;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedEvent;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedUnknown;

import static org.assertj.core.api.Assertions.assertThat;

class DltPayloadParserTest {

	private final DltPayloadParser parser = new DltPayloadParser();

	@Test
	void whenParseDeviceEventThenReturnsParsedEvent() {
		String json = """
				{"eventId":"e1","deviceId":"d1","type":"TEMPERATURE","timestamp":1718893844555,"payload":"{}"}
				""";
		DltPayloadParser.ParsedPayload result = parser.parse(json, "events-collector-service", "DB write timeout", "", 1718893845777L);
		assertThat(result).isInstanceOf(ParsedEvent.class);
		ParsedEvent e = (ParsedEvent) result;
		assertThat(e.eventType()).isEqualTo("TEMPERATURE");
		FailedEventJson.FailedEvent ev = e.json().getFailedEvent();
		assertThat(ev.getEventId()).isEqualTo("e1");
		assertThat(ev.getDeviceId()).isEqualTo("d1");
		assertThat(ev.getType()).isEqualTo("TEMPERATURE");
		assertThat(ev.getTimestamp()).isEqualTo(1718893844555L);
		assertThat(e.json().getErrorMeta().getErrorSource()).isEqualTo("events-collector-service");
		assertThat(e.json().getErrorMeta().getReason()).isEqualTo("DB write timeout");
		assertThat(e.json().getReceivedAt()).isEqualTo(1718893845777L);
	}

	@Test
	void whenParseDeviceInfoThenReturnsParsedDevice() {
		String json = """
				{"operation":"CREATE","deviceId":"d2","deviceType":"sensor","createdAt":1718893844555,"meta":"{}"}
				""";
		DltPayloadParser.ParsedPayload result = parser.parse(json, "device-collector-service", "DB write timeout", "", 1718893845777L);
		assertThat(result).isInstanceOf(ParsedDevice.class);
		ParsedDevice d = (ParsedDevice) result;
		assertThat(d.eventType()).isEqualTo("CREATE");
		FailedDeviceJson.FailedDevice fd = d.json().getFailedDevice();
		assertThat(fd.getDeviceId()).isEqualTo("d2");
		assertThat(fd.getDeviceType()).isEqualTo("sensor");
		assertThat(fd.getCreatedAt()).isEqualTo(1718893844555L);
		assertThat(d.json().getErrorMeta().getErrorSource()).isEqualTo("device-collector-service");
	}

	@Test
	void whenParseUnknownThenReturnsParsedUnknown() {
		DltPayloadParser.ParsedPayload result = parser.parse("{\"foo\":\"bar\"}", "x", "err", "", 1L);
		assertThat(result).isInstanceOf(ParsedUnknown.class);
	}
}
