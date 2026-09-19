package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryUnitTest {

    @Test
    void 같은_내용은_10명씩_묶는다() {
        List<DeliveryItem> items = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            items.add(mail(id, "공지", "같은 본문"));
        }

        List<DeliveryUnit> units = DeliveryUnit.group(items);

        assertThat(units).hasSize(2);
        assertThat(units.get(0).recipientIds()).hasSize(10);
        assertThat(units.get(1).recipientIds()).containsExactly(11L);
        assertThat(units.get(0).to()).hasSize(10).allMatch(address -> address.endsWith("@example.com"));
    }

    @Test
    void 내용이_다르면_1명씩_보낸다() {
        List<DeliveryUnit> units = DeliveryUnit.group(List.of(
                mail(1L, "공지", "김민준님 안녕하세요"),
                mail(2L, "공지", "이서연님 안녕하세요")
        ));

        assertThat(units).extracting(DeliveryUnit::recipientIds)
                .containsExactly(List.of(1L), List.of(2L));
    }

    @Test
    void 메일_단위를_SMS_단위보다_먼저_둔다() {
        List<DeliveryUnit> units = DeliveryUnit.group(List.of(
                new DeliveryItem(1L, MessageChannel.SMS, "01000000001", null, "문자", SmsKind.SMS),
                mail(1L, "공지", "본문")
        ));

        assertThat(units).extracting(DeliveryUnit::channel)
                .containsExactly(MessageChannel.MAIL, MessageChannel.SMS);
    }

    private DeliveryItem mail(Long recipientId, String subject, String body) {
        return new DeliveryItem(recipientId, MessageChannel.MAIL, "user" + recipientId + "@example.com", subject, body, null);
    }
}
