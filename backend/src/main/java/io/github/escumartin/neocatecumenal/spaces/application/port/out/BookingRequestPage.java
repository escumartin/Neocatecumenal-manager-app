package io.github.escumartin.neocatecumenal.spaces.application.port.out;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import java.util.List;

public record BookingRequestPage(List<BookingRequest> items, int page, int size, long totalItems) {

    public BookingRequestPage {
        items = List.copyOf(items);
    }
}
