create table identity_erasure (
    id        uuid        primary key,
    user_id   uuid        not null,
    erased_at timestamptz not null
);

create index idx_identity_erasure_erased_at on identity_erasure (erased_at);
