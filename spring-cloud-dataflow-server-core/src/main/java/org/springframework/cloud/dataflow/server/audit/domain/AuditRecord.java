/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.audit.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents an audit entry. Used to record audit trail information.
 *
 * @author Gunnar Hillert
 */
@Entity
@Table(name = "AUDIT_RECORDS")
@EntityListeners(AuditingEntityListener.class)
public class AuditRecord {

	public static final String UNKNOW_SERVER_HOST = "unknown";

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@Column(name = "created_by")
	@CreatedBy
	private String createdBy;

	@Column(name = "server_host")
	private String serverHost;

	@Column(name = "correlation_id")
	private String correlationId;

	@Lob
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "audit_data")
	private String auditData;

	@CreatedDate
	private Instant createdOn;

	@NotNull
	@Convert(converter = AuditActionTypeConverter.class)
	private AuditActionType auditAction;

	@NotNull
	@Convert(converter = AuditOperationTypeConverter.class)
	private AuditOperationType auditOperation;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreatedDateTime() {
		return createdOn;
	}

	public AuditActionType getAuditAction() {
		return auditAction;
	}

	public void setAuditAction(AuditActionType auditAction) {
		this.auditAction = auditAction;
	}

	public AuditOperationType getAuditOperation() {
		return auditOperation;
	}

	public void setAuditOperation(AuditOperationType auditOperation) {
		this.auditOperation = auditOperation;
	}

	public String getServerHost() {
		return serverHost;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}

	public String getAuditData() {
		return auditData;
	}

	public void setAuditData(String data) {
		this.auditData = data;
	}

	public Instant getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(Instant createdOn) {
		this.createdOn = createdOn;
	}

	/**
	 * Automatically populate the server host for auditing purposes.
	 */
	@PrePersist
	@PreUpdate
	@PreRemove
	public void populateServerHost() {
		try {
			this.serverHost = InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e) {
			this.serverHost = UNKNOW_SERVER_HOST;
		}
	}
}
