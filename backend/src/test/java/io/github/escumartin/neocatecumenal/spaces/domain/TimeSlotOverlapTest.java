package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeSlotOverlapTest {

    private static final TimeSlot EXISTING = slot(18, 0, 20, 0);

    private static TimeSlot slot(int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeSlot(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }

    @Test
    void overlapsWhenTheStartIsInsideTheOther() {
        assertThat(slot(19, 0, 21, 0).overlaps(EXISTING)).isTrue();
    }

    @Test
    void overlapsWhenTheEndIsInsideTheOther() {
        assertThat(slot(17, 0, 18, 30).overlaps(EXISTING)).isTrue();
    }

    @Test
    void overlapsWhenItEnvelopsTheOther() {
        assertThat(slot(17, 0, 21, 0).overlaps(EXISTING)).isTrue();
    }

    @Test
    void overlapsWhenItIsInsideTheOther() {
        assertThat(slot(18, 30, 19, 0).overlaps(EXISTING)).isTrue();
    }

    @Test
    void overlapsWhenItIsEqualToTheOther() {
        assertThat(slot(18, 0, 20, 0).overlaps(EXISTING)).isTrue();
    }

    @Test
    void doesNotOverlapWhenItStartsWhereTheOtherEnds() {
        assertThat(slot(20, 0, 21, 0).overlaps(EXISTING)).isFalse();
    }

    @Test
    void doesNotOverlapWhenItEndsWhereTheOtherStarts() {
        assertThat(slot(16, 0, 18, 0).overlaps(EXISTING)).isFalse();
    }

    @Test
    void doesNotOverlapWhenItIsFarAway() {
        assertThat(slot(8, 0, 9, 0).overlaps(EXISTING)).isFalse();
    }

    @Test
    void overlapIsSymmetric() {
        assertThat(EXISTING.overlaps(slot(19, 0, 21, 0))).isTrue();
        assertThat(EXISTING.overlaps(slot(20, 0, 21, 0))).isFalse();
    }

    @Test
    void aConflictKeepsItsDateAndSlot() {
        Conflict conflict = new Conflict(LocalDate.of(2030, 5, 11), EXISTING);

        assertThat(conflict.date()).isEqualTo(LocalDate.of(2030, 5, 11));
        assertThat(conflict.slot()).isEqualTo(EXISTING);
    }
}
