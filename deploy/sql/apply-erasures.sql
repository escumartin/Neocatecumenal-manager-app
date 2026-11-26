\set ON_ERROR_STOP on

begin;

create temp table neocat_erasure_report (
    position bigserial primary key,
    line     text not null
) on commit drop;

create temp table neocat_pending_identity on commit drop as
select id, user_id, erased_at
from neocat_source_identity
where erased_at >= :'backup_at'::timestamptz;

create temp table neocat_pending_community on commit drop as
select id, member_id, field, erased_at
from neocat_source_community
where erased_at >= :'backup_at'::timestamptz;

do $$
declare
    erasure record;
    affected integer;
    references_cleared integer;
begin
    for erasure in select user_id from neocat_pending_identity order by erased_at, id loop
        update community_member set user_id = null where user_id = erasure.user_id;
        update event set created_by = null where created_by = erasure.user_id;
        get diagnostics affected = row_count;
        references_cleared := affected;
        update booking_request set requested_by = null where requested_by = erasure.user_id;
        get diagnostics affected = row_count;
        references_cleared := references_cleared + affected;
        update booking_status_change set changed_by = null where changed_by = erasure.user_id;
        get diagnostics affected = row_count;
        references_cleared := references_cleared + affected;
        delete from identity_user where id = erasure.user_id;
        get diagnostics affected = row_count;
        insert into neocat_erasure_report (line) values (
            'supresión usuario ' || erasure.user_id || ': '
            || case when affected > 0 then 'borrado' else 'sin efecto, no existía en la copia' end);
        insert into neocat_erasure_report (line) values (
            'supresión usuario ' || erasure.user_id || ': referencias anonimizadas: ' || references_cleared);
    end loop;

    for erasure in select member_id, field from neocat_pending_community order by erased_at, id loop
        if erasure.field is null then
            begin
                delete from community_member where id = erasure.member_id;
                get diagnostics affected = row_count;
                insert into neocat_erasure_report (line) values (
                    'supresión hermano ' || erasure.member_id || ': '
                    || case when affected > 0 then 'borrado' else 'sin efecto, no existía en la copia' end);
            exception when foreign_key_violation then
                update community_member
                set given_name = 'Suprimido', surnames = null, mobile = null, landline = null, email = null
                where id = erasure.member_id;
                insert into neocat_erasure_report (line) values (
                    'supresión hermano ' || erasure.member_id || ': anonimizado, sigue referenciado');
            end;
        else
            execute format('update community_member set %I = null where id = $1', lower(erasure.field))
                using erasure.member_id;
            get diagnostics affected = row_count;
            insert into neocat_erasure_report (line) values (
                'supresión hermano ' || erasure.member_id || ' campo ' || erasure.field || ': '
                || case when affected > 0 then 'vaciado' else 'sin efecto, no existía en la copia' end);
        end if;
    end loop;
end
$$;

insert into identity_erasure (id, user_id, erased_at)
select id, user_id, erased_at from neocat_pending_identity
on conflict (id) do nothing;

insert into community_erasure (id, member_id, field, erased_at)
select id, member_id, field, erased_at from neocat_pending_community
on conflict (id) do nothing;

select line from neocat_erasure_report order by position;

select 'supresiones aplicadas: ' || (
    (select count(*) from neocat_pending_identity) + (select count(*) from neocat_pending_community));

commit;
