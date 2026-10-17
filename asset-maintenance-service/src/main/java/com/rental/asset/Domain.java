package com.rental.asset;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;

final class Domain { private Domain() {} }

@Entity @Table(name="appliance") @Getter @Setter @NoArgsConstructor
class Appliance {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="room_id",nullable=false) Long roomId; @Column(name="building_id",nullable=false) Long buildingId;
 @Column(nullable=false) String name; @Column(nullable=false) String status="GOOD";
 @Column(name="warranty_provider") String warrantyProvider; @Column(name="warranty_start_date") LocalDate warrantyStartDate; @Column(name="warranty_end_date") LocalDate warrantyEndDate;
 @Column(name="created_at",insertable=false,updatable=false) OffsetDateTime createdAt;
 public boolean isWarrantyActive(){return warrantyStartDate!=null&&warrantyEndDate!=null&&!LocalDate.now(ZoneOffset.UTC).isBefore(warrantyStartDate)&&!LocalDate.now(ZoneOffset.UTC).isAfter(warrantyEndDate);}
}
@Entity @Table(name="maintenance_report") @Getter @Setter @NoArgsConstructor
class MaintenanceReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="customer_id",nullable=false) Long customerId; @Column(name="room_id",nullable=false) Long roomId; @Column(name="building_id",nullable=false) Long buildingId;
 @Column(nullable=false) String title; @Column(nullable=false,columnDefinition="text") String description; @Column(nullable=false) String status="PENDING"; @Column(name="reject_reason",columnDefinition="text") String rejectReason; @Column(name="created_at",insertable=false,updatable=false) OffsetDateTime createdAt;
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="report_image",joinColumns=@JoinColumn(name="report_id")) @Column(name="image_url",nullable=false) List<String> imageUrls=new ArrayList<>();
}
@Entity @Table(name="maintenance_request") @Getter @Setter @NoArgsConstructor
class MaintenanceRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="report_id",nullable=false,unique=true) Long reportId; @Column(name="appliance_id") Long applianceId; @Column(name="room_id",nullable=false) Long roomId; @Column(name="building_id",nullable=false) Long buildingId; @Column(name="staff_id",nullable=false) Long staffId;
 @Column(nullable=false) String priority; @Column(nullable=false) String status="ASSIGNED"; @Column(name="scheduled_at",nullable=false) OffsetDateTime scheduledAt; @Column(name="created_at",insertable=false,updatable=false) OffsetDateTime createdAt;
 @OneToOne(mappedBy="request",fetch=FetchType.EAGER) MaintenanceRecord record;
}
@Entity @Table(name="maintenance_record") @Getter @Setter @NoArgsConstructor
class MaintenanceRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="maintenance_request_id",nullable=false,unique=true) Long maintenanceRequestId; @Column(name="action_taken",nullable=false,columnDefinition="text") String actionTaken; @Column(nullable=false) BigDecimal cost; @Column(nullable=false) OffsetDateTime date=OffsetDateTime.now(ZoneOffset.UTC); @Column(name="completed_by",nullable=false) Long completedBy;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="maintenance_request_id",referencedColumnName="id",insertable=false,updatable=false) @JsonIgnore MaintenanceRequest request;
}
@Entity @Table(name="maintenance_schedule") @Getter @Setter @NoArgsConstructor
class MaintenanceSchedule {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="building_id",nullable=false) Long buildingId; @Column(name="staff_id",nullable=false) Long staffId; @Column(nullable=false) String title; @Column(columnDefinition="text") String description; @Column(name="interval_months",nullable=false) Integer intervalMonths; @Column(name="next_date",nullable=false) LocalDate nextDate; @Column(name="last_done_date") LocalDate lastDoneDate; boolean active=true;
}
@Entity @Table(name="outbox_event") @Getter @Setter @NoArgsConstructor
class OutboxEvent {
 @Id UUID id; @Column(name="aggregate_type",nullable=false) String aggregateType; @Column(name="aggregate_id",nullable=false) String aggregateId; @Column(name="event_type",nullable=false) String eventType; @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") JsonNode payload; @Column(name="occurred_at",insertable=false,updatable=false) OffsetDateTime occurredAt; @Column(name="published_at") OffsetDateTime publishedAt;
}
