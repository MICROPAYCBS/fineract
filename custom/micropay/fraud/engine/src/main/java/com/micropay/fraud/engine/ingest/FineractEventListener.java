/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.micropay.fraud.engine.ingest;

import com.micropay.fraud.engine.scoring.TransactionProcessor;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Fineract's external events topic. A record that cannot be decoded is logged and skipped so one bad message
 * never blocks the stream; a scoring failure is rethrown so Kafka redelivers it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FineractEventListener {

    private final FineractMessageDecoder decoder;
    private final TransactionProcessor processor;

    @KafkaListener(topics = "${fraud.kafka.topic:external-events}", groupId = "${fraud.kafka.group-id:fraud-engine}", autoStartup = "${fraud.kafka.enabled:true}")
    public void onMessage(ConsumerRecord<Long, byte[]> record) {
        try {
            // One transaction per event, so a duplicate inside a bulk message never undoes its siblings
            decoder.decode(record.value()).forEach(processor::process);
        } catch (IOException e) {
            log.error("Skipping undecodable record at {}-{} offset {}: {}", record.topic(), record.partition(), record.offset(),
                    e.getMessage());
        }
    }
}
