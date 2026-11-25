create table event (
    id                 uuid         primary key,
    community_id       uuid         not null,
    type               varchar(16)  not null check (type in ('WORD', 'CONVIVENCE')),
    title              varchar(120) not null,
    event_date         date         not null,
    start_time         time         not null,
    end_time           time         not null,
    room_id            uuid         not null,
    booking_request_id uuid         not null unique,
    created_by         uuid,
    created_at         timestamptz  not null,
    check (end_time > start_time)
);

create index idx_event_event_date on event (event_date);
create index idx_event_created_by on event (created_by);
