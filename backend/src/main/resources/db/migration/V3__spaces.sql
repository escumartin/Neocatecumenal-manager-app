create extension if not exists btree_gist;

create table room (
    id         uuid        primary key,
    name       varchar(80) not null,
    active     boolean     not null default true,
    version    bigint      not null,
    created_at timestamptz not null
);

create unique index uk_room_active_name on room (lower(name)) where active;

create table booking_request (
    id               uuid         primary key,
    room_id          uuid         not null references room (id),
    community_id     uuid         not null,
    requested_by     uuid,
    event_id         uuid         unique,
    reason           varchar(500) not null,
    first_date       date         not null,
    last_date        date         not null,
    start_time       time         not null,
    end_time         time         not null,
    recurrence_until date,
    status           varchar(16)  not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    rejection_reason varchar(500),
    version          bigint       not null,
    created_at       timestamptz  not null,
    check (last_date >= first_date),
    check (end_time > start_time)
);

create index idx_booking_request_community_status on booking_request (community_id, status);
create index idx_booking_request_status_first_date on booking_request (status, first_date);
create index idx_booking_request_last_date on booking_request (last_date);
create index idx_booking_request_requested_by on booking_request (requested_by);

create table booking (
    id                 uuid        primary key,
    booking_request_id uuid        not null references booking_request (id) on delete cascade,
    room_id            uuid        not null references room (id),
    booking_date       date        not null,
    start_time         time        not null,
    end_time           time        not null,
    status             varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    period             tsrange     generated always as
        (tsrange(booking_date + start_time, booking_date + end_time, '[)')) stored,
    check (end_time > start_time),
    constraint ex_booking_room_period exclude using gist (room_id with =, period with &&)
        where (status in ('PENDING', 'APPROVED'))
);

create index idx_booking_booking_request on booking (booking_request_id);
create index idx_booking_room_date on booking (room_id, booking_date);

create table booking_status_change (
    id                 bigserial   primary key,
    booking_request_id uuid        not null references booking_request (id) on delete cascade,
    booking_id         uuid        references booking (id) on delete cascade,
    from_status        varchar(16) check (from_status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    to_status          varchar(16) not null check (to_status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    changed_by         uuid,
    changed_at         timestamptz not null
);

create index idx_booking_status_change_changed_by on booking_status_change (changed_by);
