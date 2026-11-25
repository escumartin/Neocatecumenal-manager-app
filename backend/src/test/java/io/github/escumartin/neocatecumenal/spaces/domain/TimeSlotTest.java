package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeSlotTest {

    @Test
    void keepsAnEndAfterTheStart() {
        TimeSlot slot = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));

        assertThat(slot.start()).isEqualTo(LocalTime.of(18, 0));
        assertThat(slot.end()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void acceptsAOneMinuteSlot() {
        assertThat(new TimeSlot(LocalTime.of(18, 0), LocalTime.of(18, 1)).end()).isEqualTo(LocalTime.of(18, 1));
    }

    @Test
    void acceptsASlotEndingAtTheLastMinuteOfTheDay() {
        assertThat(new TimeSlot(LocalTime.of(23, 0), LocalTime.of(23, 59)).end()).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    void rejectsAnEndEqualToTheStart() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(18, 0), LocalTime.of(18, 0)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("endTime");
                    assertThat(failure.code()).isEqualTo("END_NOT_AFTER_START");
                });
    }

    @Test
    void rejectsAnEndBeforeTheStart() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(20, 0), LocalTime.of(18, 0)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("END_NOT_AFTER_START"));
    }

    @Test
    void rejectsAnEndAtMidnightBecauseTheSlotMustStayInTheSameDay() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(22, 0), LocalTime.MIDNIGHT))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("END_NOT_AFTER_START"));
    }

    @Test
    void rejectsSecondsInTheStart() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(18, 0, 30), LocalTime.of(20, 0)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("startTime");
                    assertThat(failure.code()).isEqualTo("INVALID_FORMAT");
                });
    }

    @Test
    void rejectsFractionsOfASecondInTheEnd() {
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0, 0, 1)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("endTime");
                    assertThat(failure.code()).isEqualTo("INVALID_FORMAT");
                });
    }

    @Test
    void rejectsMissingTimes() {
        assertThatThrownBy(() -> new TimeSlot(null, LocalTime.of(20, 0))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimeSlot(LocalTime.of(18, 0), null)).isInstanceOf(NullPointerException.class);
    }
}
