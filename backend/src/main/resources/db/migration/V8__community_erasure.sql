create table community_erasure (
    id        uuid        primary key,
    member_id uuid        not null,
    field     varchar(16) check (field in ('MOBILE', 'LANDLINE', 'EMAIL')),
    erased_at timestamptz not null
);

create index idx_community_erasure_erased_at on community_erasure (erased_at);
