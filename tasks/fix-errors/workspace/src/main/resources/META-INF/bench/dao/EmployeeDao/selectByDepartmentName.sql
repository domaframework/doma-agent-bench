select
  /*%expand*/*
from
  employee e
  inner join department d on e.department_id = d.id
where
  d.name = /* departmentName */'Sales'
order by
  e.id
