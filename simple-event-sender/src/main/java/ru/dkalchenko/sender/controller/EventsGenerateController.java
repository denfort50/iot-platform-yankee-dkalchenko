package ru.dkalchenko.sender.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.dkalchenko.sender.model.GenerateRequest;
import ru.dkalchenko.sender.service.EventGeneratorService;

/**
 * REST API для генерации событий в Kafka.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/events")
public class EventsGenerateController {

    private final EventGeneratorService eventGeneratorService;

    /**
     * Запускает генерацию и отправку в Kafka указанного количества рандомных событий в фоне.
     * Ответ возвращается сразу, без ожидания завершения отправки.
     *
     * @param request тело запроса с полем eventsAmt
     * @return сообщение о том, сколько сообщений будет сгенерировано
     */
    @PostMapping(path = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GenerateResponse> generate(@Valid @RequestBody GenerateRequest request) {
        int eventsAmt = request.eventsAmt();
        eventGeneratorService
                .generateAndSend(eventsAmt)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        return Mono.just(new GenerateResponse(eventsAmt + " messages will be created. Check logs"));
    }

    /**
     * Запускает генерацию событий с дубликатами по deviceId: каждое 5-е сообщение
     * (5-е, 10-е, 15-е и т.д.) содержит deviceId, который уже встречался ранее.
     */
    @PostMapping(path = "/generate-with-duplicates", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GenerateResponse> generateWithDuplicates(@Valid @RequestBody GenerateRequest request) {
        int eventsAmt = request.eventsAmt();
        eventGeneratorService
                .generateAndSendWithDuplicates(eventsAmt)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        return Mono.just(new GenerateResponse(eventsAmt + " messages (with duplicate deviceIds every 5th) will be created. Check logs"));
    }

    public record GenerateResponse(String result) {
    }
}
