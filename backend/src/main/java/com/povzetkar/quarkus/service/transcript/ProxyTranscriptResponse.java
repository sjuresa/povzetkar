package com.povzetkar.quarkus.service.transcript;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ProxyTranscriptResponse(@JsonProperty("video_id") String videoId, List<ProxySegment> segments) {
}
