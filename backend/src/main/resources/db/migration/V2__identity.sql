create table identity_community (
    id     uuid    primary key,
    number integer not null unique check (number > 0)
);

create table identity_user (
    id                         uuid         primary key,
    access_id                  integer      not null
        constraint uk_identity_user_access_id unique
        check (access_id between 100000 and 999999),
    name                       varchar(100) not null,
    role                       varchar(16)  not null check (role in ('REGISTERED', 'MODERATOR', 'ADMINISTRATOR')),
    community_id               uuid         not null references identity_community (id),
    password_hash              varchar(255) not null,
    password_change_required   boolean      not null,
    privacy_notice_version     varchar(20),
    privacy_notice_accepted_at timestamptz,
    failed_password_changes    smallint     not null default 0,
    version                    bigint       not null,
    check ((privacy_notice_version is null) = (privacy_notice_accepted_at is null))
);

create index idx_identity_user_community_role on identity_user (community_id, role);
create index idx_identity_user_role on identity_user (role);

create table identity_session (
    id           uuid        primary key,
    user_id      uuid        not null references identity_user (id) on delete cascade,
    token_hash   bytea       not null unique,
    created_at   timestamptz not null,
    last_used_at timestamptz not null
);

create index idx_identity_session_user on identity_session (user_id);

create table identity_login_pair_throttle (
    access_id            integer     not null,
    ip                   inet        not null,
    consecutive_failures smallint    not null,
    last_failure_at      timestamptz not null,
    blocked_until        timestamptz,
    primary key (access_id, ip)
);

create table identity_login_ip_throttle (
    ip              inet          primary key,
    recent_failures timestamptz[] not null check (cardinality(recent_failures) <= 20),
    blocked_until   timestamptz
);
