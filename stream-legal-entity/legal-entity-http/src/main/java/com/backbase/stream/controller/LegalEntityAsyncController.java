package com.backbase.stream.controller;

import com.backbase.stream.LegalEntityTask;
import com.backbase.stream.LegalEntityUnitOfWorkExecutor;
import com.backbase.stream.legalentity.api.AsyncApi;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.legalentity.model.LegalEntityResponse;
import com.backbase.stream.mapper.UnitOfWorkMapper;
import com.backbase.stream.worker.model.UnitOfWork;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
@Slf4j
public class LegalEntityAsyncController implements AsyncApi {

    private final LegalEntityUnitOfWorkExecutor legalEntityUnitOfWorkExecutor;
    private final UnitOfWorkMapper unitOfWorkMapper = Mappers.getMapper(UnitOfWorkMapper.class);

    @Override
    public Mono<ResponseEntity<LegalEntityResponse>> getUnitOfWork(String unitOfWorkId, ServerWebExchange exchange) {
        return legalEntityUnitOfWorkExecutor.retrieve(unitOfWorkId)
            .map(unitOfWorkMapper::convertToLegalEntityResponse)
            .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Flux<LegalEntityResponse>>> processLegalEntitiesAsync(Flux<LegalEntity> legalEntity, ServerWebExchange exchange) {
        Flux<LegalEntityResponse> map = legalEntity.bufferTimeout(10, Duration.ofMillis(100))
            .map(this::createUnitOfWork)
            .flatMap(legalEntityUnitOfWorkExecutor::register)
            .map(unitOfWorkMapper::convertToLegalEntityResponse);
        return  Mono.just(ResponseEntity.ok(map));
    }

    private UnitOfWork<LegalEntityTask> createUnitOfWork(List<LegalEntity> legalEntities) {
        List<LegalEntityTask> tasks = legalEntities.stream()
            .map(LegalEntityTask::new)
            .collect(Collectors.toList());
        return UnitOfWork.from("http-" + System.currentTimeMillis(), tasks);
    }

}
