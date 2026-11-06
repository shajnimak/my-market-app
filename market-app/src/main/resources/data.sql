-- Демонстрационные покупатели. Пароли хранятся только в виде BCrypt-хешей; открытый пароль обоих — password.
-- Скрипт выполняется при каждом старте, поэтому вставка идёт, только если такого логина ещё нет.
insert into users (username, password)
select 'alice', '{bcrypt}$2y$10$LSZg0uII/FyWRc0Sh7ezjOyVNPAdeuqNmELwbGFWt06wGH70K5x3C'
where not exists (select 1 from users where username = 'alice');

insert into users (username, password)
select 'bob', '{bcrypt}$2y$10$kTS0Yk8hVmURbCObRYeEYesdg/KBwlnhqfHKIShLf8k78pX5osl9W'
where not exists (select 1 from users where username = 'bob');
