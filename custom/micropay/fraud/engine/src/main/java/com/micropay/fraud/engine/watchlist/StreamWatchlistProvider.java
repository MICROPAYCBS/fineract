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
package com.micropay.fraud.engine.watchlist;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads a list from an https URL or a local file and parses it with one {@link ListParser}. The snapshot version is the
 * SHA-256 of the bytes read, so an unchanged list is recognised without comparing entries.
 */
public final class StreamWatchlistProvider implements WatchlistProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    private final String source;
    private final String location;
    private final ListParser parser;
    private final HttpClient httpClient;

    public StreamWatchlistProvider(String source, String location, ListParser parser, HttpClient httpClient) {
        this.source = source;
        this.location = location;
        this.parser = parser;
        this.httpClient = httpClient;
    }

    public static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public String source() {
        return source;
    }

    public String location() {
        return location;
    }

    @Override
    public WatchlistSnapshot fetch() throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
        try (InputStream raw = open(); DigestInputStream in = new DigestInputStream(raw, digest)) {
            // Parsers may close what they read; the digest needs the stream to stay open until it is drained
            final List<WatchlistEntry> entries = parser.parse(source, new FilterInputStream(in) {

                @Override
                public void close() {
                    // Closed by the try-with-resources above
                }
            });
            // Drain anything the parser did not read so the digest covers the whole file
            in.transferTo(OutputStream.nullOutputStream());
            return new WatchlistSnapshot(source, HexFormat.of().formatHex(digest.digest()), entries);
        }
    }

    private InputStream open() throws IOException {
        if (!location.startsWith("https://") && !location.startsWith("http://")) {
            return Files.newInputStream(Path.of(location.startsWith("file:") ? URI.create(location).getPath() : location));
        }
        final HttpRequest request = HttpRequest.newBuilder(URI.create(location)).timeout(READ_TIMEOUT).GET().build();
        try {
            final HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Downloading " + source + " from " + location + " returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + source, e);
        }
    }
}
