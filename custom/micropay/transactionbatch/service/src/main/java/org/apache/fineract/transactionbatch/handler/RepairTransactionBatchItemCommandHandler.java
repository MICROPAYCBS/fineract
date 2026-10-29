/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.handler;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.transactionbatch.service.TransactionBatchRepairService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@CommandType(entity = "TRANSACTIONBATCHITEM", action = "REPAIR")
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class RepairTransactionBatchItemCommandHandler implements NewCommandSourceHandler {

    private final TransactionBatchRepairService service;

    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        return this.service.repair(command);
    }
}
