package com.povzetkar.quarkus.service;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import jakarta.inject.Singleton;
import jakarta.enterprise.inject.Produces;

import java.net.HttpURLConnection;

public class YouTubeServiceProducer {

    private static final String APPLICATION_NAME = "PovzetkarQuarkusApp";

    @Produces
    @Singleton
    public YouTube youtube() {
        return new YouTube.Builder(
                new NetHttpTransport.Builder()
                        .setConnectionFactory(url -> {
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(20_000); // 20 seconds
                            connection.setReadTimeout(20_000); // 20 seconds
                            return connection;
                        }).build(),
                new GsonFactory(), null).setApplicationName(APPLICATION_NAME).build();
    }
}