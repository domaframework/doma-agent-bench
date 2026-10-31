insert into department (id, name) values (1, 'Sales');
insert into department (id, name) values (2, 'Engineering');
insert into department (id, name) values (3, 'Legal');

insert into employee (id, name, salary, department_id, version) values (1, 'Alice', 5000.00, 1, 1);
insert into employee (id, name, salary, department_id, version) values (2, 'Bob', 6000.00, 2, 1);
insert into employee (id, name, salary, department_id, version) values (3, 'Carol', 7000.00, 2, 1);
insert into employee (id, name, salary, department_id, version) values (4, 'A%x', 3000.00, 1, 1);
insert into employee (id, name, salary, department_id, version) values (5, 'Andrew', 4500.00, 2, 1);
insert into employee (id, name, salary, department_id, version) values (6, 'Dave', 5000.00, 1, 1);
alter table employee alter column id restart with 100;
