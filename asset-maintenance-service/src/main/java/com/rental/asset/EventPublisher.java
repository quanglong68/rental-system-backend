package com.rental.asset;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EventPublisher {
 private final OutboxRepository outbox; private final KafkaTemplate<String,String> kafka; private final ScheduleRepository schedules; private final ObjectMapper json;
 EventPublisher(OutboxRepository outbox,KafkaTemplate<String,String> kafka,ScheduleRepository schedules,ObjectMapper json){this.outbox=outbox;this.kafka=kafka;this.schedules=schedules;this.json=json;}
 @Scheduled(fixedDelayString="${asset.outbox-poll-ms:1000}") @Transactional
 void publish(){for(var event:outbox.lockPending()){try{kafka.send("asset.events",event.getAggregateId(),json.writeValueAsString(event.getPayload())).get();event.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));outbox.save(event);}catch(Exception ignored){break;}}}
 @Scheduled(cron="0 0 6 * * *",zone="UTC") @Transactional
 void dueSchedules(){LocalDate today=LocalDate.now(ZoneOffset.UTC);for(var s:schedules.findByActiveTrueAndNextDateBetween(today,today.plusDays(1))){try{var payload=Map.of("scheduleId",s.getId(),"buildingId",s.getBuildingId(),"staffId",s.getStaffId(),"title",s.getTitle(),"nextDate",s.getNextDate());var envelope=com.rental.common.event.EventEnvelope.of("MaintenanceScheduleDue",payload,null);OutboxEvent e=new OutboxEvent();e.setId(envelope.getEventId());e.setAggregateType("maintenance_schedule");e.setAggregateId(s.getId().toString());e.setEventType("MaintenanceScheduleDue");e.setPayload(json.valueToTree(envelope));outbox.save(e);}catch(Exception ex){throw new IllegalStateException(ex);}}}
}
