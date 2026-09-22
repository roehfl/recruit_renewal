package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게이트웨이 1회 호출 단위 = 채널별로 내용이 완전히 같은 수신자 최대 10명(설계서 7.1.1).
 * 변수로 사람마다 내용이 달라지면 1명당 1단위가 된다. recipientIds·names·to 는 같은 순서다.
 */
public record DeliveryUnit(
        MessageChannel channel,
        String subject,
        String body,
        SmsKind smsKind,
        List<Long> recipientIds,
        List<String> names,
        List<String> to
) {

    public static final int MAX_RECIPIENTS = 10;

    /** 같은 내용끼리 묶어 10명씩 나눈다. 메일 단위를 먼저 두고, 같은 채널 안에서는 처음 나온 순서를 지킨다. */
    public static List<DeliveryUnit> group(List<DeliveryItem> items) {
        Map<ContentKey, List<DeliveryItem>> groups = new LinkedHashMap<>();
        for (DeliveryItem item : items) {
            ContentKey key = new ContentKey(item.channel(), item.subject(), item.body(), item.smsKind());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<DeliveryUnit> units = new ArrayList<>();
        groups.forEach((key, grouped) -> {
            for (int from = 0; from < grouped.size(); from += MAX_RECIPIENTS) {
                List<DeliveryItem> chunk = grouped.subList(from, Math.min(from + MAX_RECIPIENTS, grouped.size()));
                units.add(new DeliveryUnit(
                        key.channel(), key.subject(), key.body(), key.smsKind(),
                        chunk.stream().map(DeliveryItem::recipientId).toList(),
                        chunk.stream().map(DeliveryItem::name).toList(),
                        chunk.stream().map(DeliveryItem::to).toList()
                ));
            }
        });
        units.sort(Comparator.comparing(DeliveryUnit::channel));
        return units;
    }

    private record ContentKey(MessageChannel channel, String subject, String body, SmsKind smsKind) {
    }
}
