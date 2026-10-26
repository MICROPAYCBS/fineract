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
package com.micropay.fraud.engine;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fraud scoring engine: reads Fineract's external events from Kafka, scores every transaction with rules and an anomaly
 * model, and keeps alerts for the fraud dashboard.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FraudEngineApplication {

    // Own config name, so this jar never picks up or shadows Fineract's application.properties
    public static final String CONFIG_NAME = "fraud-engine";

    protected FraudEngineApplication() {}

    public static void main(String[] args) {
        final SpringApplication application = new SpringApplication(FraudEngineApplication.class);
        application.setDefaultProperties(Map.of("spring.config.name", CONFIG_NAME));
        application.run(args);
    }
}
