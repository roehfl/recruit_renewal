package com.shinyoung.recruit.service;

import org.springframework.core.io.Resource;

public record PostingImageResource(Resource resource, String contentType, long contentLength) {
}
