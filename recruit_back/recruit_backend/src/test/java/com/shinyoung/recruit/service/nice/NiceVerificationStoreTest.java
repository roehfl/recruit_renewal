package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceVerificationStoreTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private final NiceVerificationStore store = new NiceVerificationStore();

    @Test
    void savedRecordIsFoundByReqSeq() {
        store.save(NiceVerificationRecord.pending("req-1", NiceVerificationPurpose.SIGNUP, "sess-1", NOW));

        Optional<NiceVerificationRecord> found = store.find("req-1");

        assertTrue(found.isPresent());
        assertEquals("sess-1", found.get().sessionId());
    }

    @Test
    void unknownReqSeqIsEmpty() {
        assertTrue(store.find("nope").isEmpty());
    }

    @Test
    void findByResultTokenLocatesRecord() {
        NiceVerificationRecord pending =
                NiceVerificationRecord.pending("req-2", NiceVerificationPurpose.SIGNUP, "sess-1", NOW);
        store.save(pending.verified("tok-2", NOW, "홍길동", "01012345678", "CI-VALUE"));

        assertEquals("req-2", store.findByResultToken("tok-2").orElseThrow().reqSeq());
    }

    @Test
    void removeDeletesRecord() {
        store.save(NiceVerificationRecord.pending("req-3", NiceVerificationPurpose.SIGNUP, "sess-1", NOW));

        store.remove("req-3");

        assertTrue(store.find("req-3").isEmpty());
    }

    @Test
    void purgeExpiredRemovesOnlyRecordsOlderThanTtl() {
        store.save(NiceVerificationRecord.pending("old", NiceVerificationPurpose.SIGNUP, "sess-1", NOW));
        store.save(NiceVerificationRecord.pending(
                "fresh", NiceVerificationPurpose.SIGNUP, "sess-1", NOW.plus(Duration.ofMinutes(9))));

        Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(11)), ZoneOffset.UTC);
        store.purgeExpired(later.instant(), Duration.ofMinutes(10));

        assertTrue(store.find("old").isEmpty());
        assertTrue(store.find("fresh").isPresent());
    }

    @Test
    void saveIfAbsentDoesNotOverwriteExistingRecord() {
        assertTrue(store.saveIfAbsent(
                NiceVerificationRecord.pending("req-4", NiceVerificationPurpose.SIGNUP, "sess-1", NOW)));

        // 같은 REQ_SEQ 로 다른 사람이 들어온 상황. 앞사람의 PENDING 레코드가 사라지면 안 된다.
        boolean saved = store.saveIfAbsent(
                NiceVerificationRecord.pending("req-4", NiceVerificationPurpose.SIGNUP, "sess-2", NOW));

        assertFalse(saved);
        assertEquals("sess-1", store.find("req-4").orElseThrow().sessionId());
    }

    @Test
    void compareAndSetFailsWhenCurrentDiffersFromExpected() {
        NiceVerificationRecord pending =
                NiceVerificationRecord.pending("req-5", NiceVerificationPurpose.SIGNUP, "sess-1", NOW);
        store.save(pending);
        // 다른 요청이 먼저 처리해 값이 바뀐 상황.
        store.save(pending.verified("tok-A", NOW, "홍길동", "01012345678", "CI-VALUE"));

        boolean replaced = store.compareAndSet(
                pending, pending.verified("tok-B", NOW, "홍길동", "01012345678", "CI-VALUE"));

        assertFalse(replaced);
        assertEquals("tok-A", store.find("req-5").orElseThrow().resultToken());
    }

    @Test
    void compareAndSetReplacesWhenCurrentEqualsExpected() {
        NiceVerificationRecord pending =
                NiceVerificationRecord.pending("req-6", NiceVerificationPurpose.SIGNUP, "sess-1", NOW);
        store.save(pending);

        boolean replaced = store.compareAndSet(
                pending, pending.verified("tok-6", NOW, "홍길동", "01012345678", "CI-VALUE"));

        assertTrue(replaced);
        assertEquals("tok-6", store.find("req-6").orElseThrow().resultToken());
    }

    @Test
    void takeByResultTokenHandsOutRecordOnlyOnce() {
        NiceVerificationRecord pending =
                NiceVerificationRecord.pending("req-7", NiceVerificationPurpose.SIGNUP, "sess-1", NOW);
        store.save(pending.verified("tok-7", NOW, "홍길동", "01012345678", "CI-VALUE"));

        Optional<NiceVerificationRecord> first = store.takeByResultToken("tok-7");
        Optional<NiceVerificationRecord> second = store.takeByResultToken("tok-7");

        assertEquals("req-7", first.orElseThrow().reqSeq());
        assertTrue(second.isEmpty());
        assertTrue(store.find("req-7").isEmpty());
    }
}
