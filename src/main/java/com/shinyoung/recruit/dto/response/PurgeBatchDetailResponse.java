package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.entity.PurgeJobItem;

import java.util.List;

public record PurgeBatchDetailResponse(
        PurgeBatchResponse batch,
        List<PurgeJobItemResponse> items
) {
    public static PurgeBatchDetailResponse of(PurgeBatch batch, List<PurgeJobItem> items) {
        return new PurgeBatchDetailResponse(
                PurgeBatchResponse.from(batch),
                items.stream().map(PurgeJobItemResponse::from).toList()
        );
    }
}
