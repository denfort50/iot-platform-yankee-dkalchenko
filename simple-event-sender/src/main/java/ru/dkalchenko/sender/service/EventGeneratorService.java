package ru.dkalchenko.sender.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Генерирует рандомные события DeviceEvent и отправляет их в Kafka реактивно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventGeneratorService {

    static SecureRandom RANDOM = new SecureRandom();
    static List<String> EVENT_TYPES = List.of("temperature", "voltage", "battery", "impedance", "speed");
    static int DEVICE_ID_SUFFIX_MAX = 100_000_000;

    KafkaSender<String, GenericRecord> kafkaSender;
    String eventsTopic;
    Schema deviceEventSchema;
    AtomicLong counter = new AtomicLong(0);

    /**
     * Генерирует {@code eventsAmt} рандомных событий и отправляет их в топик events.v1.
     *
     * @param eventsAmt количество сообщений для отправки
     * @return количество успешно отправленных сообщений
     */
    public Flux<Long> generateAndSend(int eventsAmt) {
        if (eventsAmt <= 0) {
            return Flux.just(0L);
        }

        return Flux.range(0, eventsAmt)
                .parallel()
                .runOn(Schedulers.parallel())
                .map(_ -> createRandomEvent())
                .map(record -> SenderRecord.create(
                        eventsTopic,
                        null,
                        null,
                        record.get("deviceId").toString(),
                        record,
                        counter.incrementAndGet()))
                .sequential()
                .as(kafkaSender::send)
                .doOnNext(result -> {
                    if (result.exception() != null) {
                        warn(result);
                    }
                })
                .filter(result -> result.exception() == null)
                .count()
                .doOnSuccess(sent -> log.info("Sent {} events to topic {}", sent, eventsTopic))
                .doOnError(e -> log.error("Error sending events", e))
                .flux();
    }

    /**
     * Генерирует {@code eventsAmt} событий и отправляет их в топик events.v1.
     * Каждое 5-е сообщение (5-е, 10-е, 15-е и т.д.) содержит deviceId, который уже встречался ранее.
     *
     * @param eventsAmt количество сообщений для отправки
     * @return количество успешно отправленных сообщений
     */
    public Flux<Long> generateAndSendWithDuplicates(int eventsAmt) {
        if (eventsAmt <= 0) {
            return Flux.just(0L);
        }

        List<String> previousDeviceIds = new ArrayList<>();

        return Flux.range(0, eventsAmt)
                .map(i -> createEventWithOptionalDuplicate(i, previousDeviceIds))
                .map(record -> SenderRecord.create(
                        eventsTopic,
                        null,
                        null,
                        record.get("deviceId").toString(),
                        record,
                        counter.incrementAndGet()))
                .as(kafkaSender::send)
                .doOnNext(result -> {
                    if (result.exception() != null) {
                        warn(result);
                    }
                })
                .filter(result -> result.exception() == null)
                .count()
                .doOnSuccess(sent -> log.info("Sent {} events (with duplicates) to topic {}", sent, eventsTopic))
                .doOnError(e -> log.error("Error sending events", e))
                .flux();
    }

    private GenericRecord createEventWithOptionalDuplicate(int index, List<String> previousDeviceIds) {
        String deviceId;
        if ((index + 1) % 5 == 0 && !previousDeviceIds.isEmpty()) {
            deviceId = previousDeviceIds.get(RANDOM.nextInt(previousDeviceIds.size()));
        } else {
            deviceId = "device-" + (RANDOM.nextInt(DEVICE_ID_SUFFIX_MAX) + 1);
            previousDeviceIds.add(deviceId);
        }

        GenericRecord record = new GenericData.Record(deviceEventSchema);
        record.put("eventId", UUID.randomUUID().toString());
        record.put("deviceId", deviceId);
        record.put("timestamp", System.currentTimeMillis());
        record.put("type", EVENT_TYPES.get(RANDOM.nextInt(EVENT_TYPES.size())));
        record.put("payload", "{\"value\":" + (RANDOM.nextInt(1000) / 10.0) + "}");

        return record;
    }

    private GenericRecord createRandomEvent() {
        GenericRecord record = new GenericData.Record(deviceEventSchema);
        String eventId = UUID.randomUUID().toString();
        String deviceId = "device-" + (RANDOM.nextInt(DEVICE_ID_SUFFIX_MAX) + 1);
        long timestamp = System.currentTimeMillis();
        String type = EVENT_TYPES.get(RANDOM.nextInt(EVENT_TYPES.size()));
        String payload = "{\"value\":" + (RANDOM.nextInt(1000) / 10.0) + "}";

        record.put("eventId", eventId);
        record.put("deviceId", deviceId);
        record.put("timestamp", timestamp);
        record.put("type", type);
        record.put("payload", payload);

        return record;
    }

    private static void warn(SenderResult<Long> result) {
        log.warn("Send failed for record {}: {}", result.correlationMetadata(), result.exception().getMessage());
    }
}
