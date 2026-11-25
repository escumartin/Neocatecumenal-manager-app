package io.github.escumartin.neocatecumenal.spaces.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record WeeklyRecurrence(LocalDate until) {

    public WeeklyRecurrence {
        if (until == null) {
            throw new SpacesFieldInvalid("recurrence.until", "REQUIRED");
        }
    }

    public List<LocalDate> dates(LocalDate firstDate) {
        if (until.isBefore(firstDate)) {
            throw new SpacesFieldInvalid("recurrence.until", "RECURRENCE_UNTIL_BEFORE_DATE");
        }
        if (until.isAfter(firstDate.plusYears(1))) {
            throw new SpacesFieldInvalid("recurrence.until", "RECURRENCE_TOO_LONG");
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(until); date = date.plusWeeks(1)) {
            dates.add(date);
        }
        return List.copyOf(dates);
    }
}
