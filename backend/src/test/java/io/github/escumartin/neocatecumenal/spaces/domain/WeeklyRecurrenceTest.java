package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyRecurrenceTest {

    private static final LocalDate FIRST = LocalDate.of(2030, 5, 14);

    @Test
    void theEndDateIsRequired() {
        assertThatThrownBy(() -> new WeeklyRecurrence(null))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, invalid -> {
                    assertThat(invalid.field()).isEqualTo("recurrence.until");
                    assertThat(invalid.code()).isEqualTo("REQUIRED");
                });
    }

    @Test
    void anEndDateBeforeTheFirstDateIsRejected() {
        WeeklyRecurrence recurrence = new WeeklyRecurrence(FIRST.minusDays(1));

        assertThatThrownBy(() -> recurrence.dates(FIRST)).isInstanceOfSatisfying(SpacesFieldInvalid.class, invalid -> {
            assertThat(invalid.field()).isEqualTo("recurrence.until");
            assertThat(invalid.code()).isEqualTo("RECURRENCE_UNTIL_BEFORE_DATE");
        });
    }

    @Test
    void anEndDateMoreThanAYearAfterTheFirstDateIsTooLong() {
        WeeklyRecurrence recurrence = new WeeklyRecurrence(FIRST.plusYears(1).plusDays(1));

        assertThatThrownBy(() -> recurrence.dates(FIRST)).isInstanceOfSatisfying(SpacesFieldInvalid.class, invalid -> {
            assertThat(invalid.field()).isEqualTo("recurrence.until");
            assertThat(invalid.code()).isEqualTo("RECURRENCE_TOO_LONG");
        });
    }

    @Test
    void anEndDateEqualToTheFirstDateGivesOnlyTheFirstDate() {
        assertThat(new WeeklyRecurrence(FIRST).dates(FIRST)).containsExactly(FIRST);
    }

    @Test
    void datesAreSevenDaysApartUpToTheEndDateInclusive() {
        List<LocalDate> dates = new WeeklyRecurrence(FIRST.plusDays(14)).dates(FIRST);

        assertThat(dates).containsExactly(FIRST, FIRST.plusDays(7), FIRST.plusDays(14));
    }

    @Test
    void anEndDateBetweenWeeksStopsAtTheLastWholeWeek() {
        List<LocalDate> dates = new WeeklyRecurrence(FIRST.plusDays(20)).dates(FIRST);

        assertThat(dates).containsExactly(FIRST, FIRST.plusDays(7), FIRST.plusDays(14));
    }

    @Test
    void exactlyOneYearGivesFiftyThreeDates() {
        List<LocalDate> dates = new WeeklyRecurrence(FIRST.plusYears(1)).dates(FIRST);

        assertThat(dates).hasSize(53);
        assertThat(dates.get(52)).isEqualTo(FIRST.plusWeeks(52));
    }
}
