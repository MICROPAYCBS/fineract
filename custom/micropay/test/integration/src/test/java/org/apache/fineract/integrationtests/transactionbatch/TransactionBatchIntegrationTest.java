/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.integrationtests.transactionbatch;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Header, search and an empty submit. An empty batch is refused before any maker-checker decision.
 */
public class TransactionBatchIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/transactionbatches";

    private RequestSpecification requestSpec;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void createSearchAndRejectAnEmptySubmit() {
        final Response created = given().spec(this.requestSpec)
                .body("{\"description\":\"Petty cash\",\"recurring\":false,\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\"}").post(API);
        assertThat(created.statusCode()).isEqualTo(200);
        final long batchId = created.jsonPath().getLong("resourceId");

        final Response one = given().spec(this.requestSpec).get(API + "/" + batchId);
        assertThat(one.statusCode()).isEqualTo(200);
        assertThat(one.jsonPath().getString("status")).isEqualTo("INACTIVE");
        assertThat(one.jsonPath().getString("code")).startsWith("100");

        final Response search = given().spec(this.requestSpec).queryParam("fromDate", "2026-12-31").queryParam("toDate", "2026-01-01")
                .get(API);
        assertThat(search.statusCode()).isEqualTo(400);

        final Response submit = given().spec(this.requestSpec).queryParam("command", "submit").body("{}").post(API + "/" + batchId);
        assertThat(submit.statusCode()).isEqualTo(403);
        assertThat(submit.asString()).contains("at least one line");
    }

    @Test
    void templateAndSettingsAreReadable() {
        assertThat(given().spec(this.requestSpec).get(API + "/template").statusCode()).isEqualTo(200);
        assertThat(given().spec(this.requestSpec).get(API + "/settings").statusCode()).isEqualTo(200);
        assertThat(given().spec(this.requestSpec).queryParam("search", "0").get(API + "/accounts").statusCode()).isEqualTo(200);
    }
}
