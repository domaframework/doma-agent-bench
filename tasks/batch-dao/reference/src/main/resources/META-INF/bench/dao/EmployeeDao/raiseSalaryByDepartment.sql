update employee
set
  salary = salary + /* amount */100,
  version = version + 1
where
  department_id = /* departmentId */1
