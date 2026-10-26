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

import com.micropay.fraud.engine.event.FineractEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.AvroRuntimeException;
import org.apache.fineract.avro.BulkMessageItemV1;
import org.apache.fineract.avro.BulkMessagePayloadV1;
import org.apache.fineract.avro.MessageV1;
import org.springframework.stereotype.Component;

/**
 * Turns a Kafka record from Fineract into the events the engine acts on. A record is an Avro {@link MessageV1} whose
 * {@code data} holds the payload named by {@code dataschema}; a bulk event holds several payloads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FineractMessageDecoder {

    static final String BULK_EVENT_TYPE = "BulkBusinessEvent";

    private final FineractEventMapper mapper;
    private final Map<String, Method> decoders = new ConcurrentHashMap<>();

    public List<FineractEvent> decode(byte[] record) throws IOException {
        try {
            return decodeMessage(MessageV1.fromByteBuffer(ByteBuffer.wrap(record)));
        } catch (AvroRuntimeException | ClassCastException e) {
            throw new IOException("Not a Fineract MessageV1 record: " + e.getMessage(), e);
        }
    }

    private List<FineractEvent> decodeMessage(MessageV1 message) throws IOException {
        final String tenantId = message.getTenantId();
        final List<FineractEvent> events = new ArrayList<>();
        if (BULK_EVENT_TYPE.equals(message.getType())) {
            final BulkMessagePayloadV1 bulk = (BulkMessagePayloadV1) decodePayload(message.getDataschema(), message.getData());
            for (BulkMessageItemV1 item : bulk.getDatas()) {
                // Each item gets its own key so the parts of one bulk event are not mistaken for duplicates
                final String key = message.getIdempotencyKey() + "#" + item.getId();
                mapper.map(tenantId, key, item.getType(), decodePayload(item.getDataschema(), item.getData()), message.getCreatedAt(),
                        message.getBusinessDate()).ifPresent(events::add);
            }
        } else {
            final Object payload = decodePayload(message.getDataschema(), message.getData());
            mapper.map(tenantId, message.getIdempotencyKey(), message.getType(), payload, message.getCreatedAt(), message.getBusinessDate())
                    .ifPresent(events::add);
        }
        return events;
    }

    private Object decodePayload(String schema, ByteBuffer data) throws IOException {
        final Method fromByteBuffer = decoderFor(schema).orElseThrow(() -> new IOException("Unknown Avro payload schema " + schema));
        try {
            return fromByteBuffer.invoke(null, data);
        } catch (IllegalAccessException | InvocationTargetException | AvroRuntimeException e) {
            throw new IOException("Cannot decode payload of schema " + schema, e);
        }
    }

    private Optional<Method> decoderFor(String schema) {
        if (schema == null || !schema.startsWith("org.apache.fineract.avro.")) {
            return Optional.empty();
        }
        return Optional.ofNullable(decoders.computeIfAbsent(schema, s -> {
            try {
                return Class.forName(s).getMethod("fromByteBuffer", ByteBuffer.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log.warn("No Avro class for payload schema {}", s);
                return null;
            }
        }));
    }
}
