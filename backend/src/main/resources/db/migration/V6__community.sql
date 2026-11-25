create table community (
    id                    uuid         primary key,
    number                integer      not null
        constraint uk_community_number unique
        check (number > 0),
    name                  varchar(100) check (name is null or length(btrim(name)) > 0),
    status                varchar(16)  not null check (status in ('ACTIVE', 'DEACTIVATED')),
    responsible_member_id uuid,
    version               bigint       not null
);

create table community_member (
    id           uuid         primary key,
    community_id uuid         not null references community (id),
    given_name   varchar(100) not null check (length(btrim(given_name)) > 0),
    surnames     varchar(100) check (surnames is null or length(btrim(surnames)) > 0),
    mobile       varchar(16)  check (mobile is null or mobile ~ '^([6-9][0-9]{8}|\+[1-9][0-9]{1,14})$'),
    landline     varchar(16)  check (landline is null or landline ~ '^([6-9][0-9]{8}|\+[1-9][0-9]{1,14})$'),
    email        varchar(254),
    user_id      uuid
        constraint uk_community_member_user unique,
    version      bigint       not null,
    constraint uk_community_member_id_community unique (id, community_id)
);

create index idx_community_member_community on community_member (community_id);

alter table community add constraint fk_community_responsible
    foreign key (responsible_member_id, id) references community_member (id, community_id);

create table community_contact_consent (
    id                  uuid        primary key,
    member_id           uuid        not null references community_member (id) on delete cascade,
    field               varchar(16) not null check (field in ('MOBILE', 'LANDLINE', 'EMAIL')),
    decision            varchar(16) not null check (decision in ('GRANTED', 'WITHDRAWN')),
    channel             varchar(16) not null check (channel in ('APP', 'VERBAL', 'WRITTEN')),
    recorded_by_user_id uuid        not null,
    recorder_role       varchar(16) not null check (recorder_role in ('MEMBER', 'MODERATOR', 'ADMINISTRATOR')),
    notice_given        boolean     not null,
    recorded_at         timestamptz not null,
    check ((channel = 'APP') = (recorder_role = 'MEMBER')),
    check (channel = 'APP' or notice_given)
);

create index idx_community_contact_consent_member_field on community_contact_consent (member_id, field, recorded_at);

insert into community (id, number, status, version)
select id, number, 'ACTIVE', 0 from identity_community;
