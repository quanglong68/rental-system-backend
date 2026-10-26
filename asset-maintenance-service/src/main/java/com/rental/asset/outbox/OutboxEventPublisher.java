package com.rental.asset.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.asset.entity.MaintenanceSchedule;
import com.rental.asset.entity.OutboxEvent;
import com.rental.asset.repository.MaintenanceScheduleRepository;
import com.rental.asset.repository.OutboxEventRepository;
import com.rental.common.event.EventEnvelope;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MaintenanceScheduleRepository maintenanceScheduleRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MaintenanceScheduleRepository maintenanceScheduleRepository,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.maintenanceScheduleRepository = maintenanceScheduleRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${asset.outbox-poll-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent outboxEvent : outboxEventRepository.lockPendingEvents()) {
            try {
                kafkaTemplate.send(
                                "asset.events",
                                outboxEvent.getAggregateId(),
                                objectMapper.writeValueAsString(outboxEvent.getPayload()))
                        .get();
                outboxEvent.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
                outboxEventRepository.save(outboxEvent);
            } catch (Exception exception) {
                break;
            }
        }
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    @Transactional
    public void createDueScheduleEvents() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (MaintenanceSchedule schedule : maintenanceScheduleRepository
                .findByActiveTrueAndNextDateBetween(today, today.plusDays(1))) {
            createScheduleDueEvent(schedule);
        }
    }

    private void createScheduleDueEvent(MaintenanceSchedule schedule) {
        Map<String, Object> eventPayload = Map.of(
                "scheduleId", schedule.getId(),
                "buildingId", schedule.getBuildingId(),
                "staffId", schedule.getStaffId(),
                "title", schedule.getTitle(),
                "nextDate", schedule.getNextDate());
        EventEnvelope eventEnvelope = EventEnvelope.of("MaintenanceScheduleDue", eventPayload, null);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(eventEnvelope.getEventId());
        outboxEvent.setAggregateType("maintenance_schedule");
        outboxEvent.setAggregateId(schedule.getId().toString());
        outboxEvent.setEventType("MaintenanceScheduleDue");
        outboxEvent.setPayload(objectMapper.valueToTree(eventEnvelope));
        outboxEventRepository.save(outboxEvent);
    }
}
