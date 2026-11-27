insert into identity_community (id, number)
values ('00000000-0000-4000-8000-000000000005', 5)
on conflict (number) do nothing;

insert into community (id, number, name, status, version)
select id, number, 'Quinta comunidad', 'ACTIVE', 0
from identity_community
where number = 5
on conflict (number) do nothing;

insert into community_member (id, community_id, given_name, surnames, version)
select md5('quinta-comunidad-member-' || v.n)::uuid, c.id, v.given_name, v.surnames, 0
from community c,
     (values
        (1, 'Antonio', 'García Ruiz'),
        (2, 'Carmen', 'López Medina'),
        (3, 'José', 'Martínez Cano'),
        (4, 'María', 'Sánchez Ortega'),
        (5, 'Manuel', 'Fernández Gil'),
        (6, 'Pilar', 'Navarro Soler'),
        (7, 'Francisco', 'Ruiz Alarcón'),
        (8, 'Dolores', 'Moreno Vidal'),
        (9, 'Juan', 'Hernández Pardo'),
        (10, 'Josefa', 'Jiménez Campos'),
        (11, 'Pedro', 'Díaz Marín'),
        (12, 'Isabel', 'Romero Lara'),
        (13, 'Miguel', 'Gómez Tomás'),
        (14, 'Rosa', 'Alonso Cuesta'),
        (15, 'Ángel', 'Serrano Peña'),
        (16, 'Teresa', 'Molina Ferrer'),
        (17, 'Luis', 'Muñoz Bernal'),
        (18, 'Lucía', 'Castillo Rubio'),
        (19, 'Javier', 'Ortiz Palacios'),
        (20, 'Concepción', 'Gutiérrez Mora'),
        (21, 'Rafael', 'Blanco Esteban'),
        (22, 'Amparo', 'Ramos Núñez'),
        (23, 'Carlos', 'Vega Cabrera'),
        (24, 'Encarnación', 'Iglesias Prieto'),
        (25, 'Diego', 'Cortés Lozano'),
        (26, 'Inmaculada', 'Herrera Santos'),
        (27, 'Ana', 'Delgado Pascual'),
        (28, 'Sergio', 'Vázquez Arroyo'),
        (29, 'Elena', 'Flores Calvo'),
        (30, 'Pablo', 'Reyes Montero'),
        (31, 'Marta', 'Crespo Guerrero'),
        (32, 'Alberto', 'Pastor Aguilar'),
        (33, 'Raquel', 'Santana Bravo')
     ) as v (n, given_name, surnames)
where c.number = 5
on conflict (id) do nothing;
