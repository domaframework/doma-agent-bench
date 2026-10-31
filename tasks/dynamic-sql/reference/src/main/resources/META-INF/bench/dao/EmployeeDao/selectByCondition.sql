select
  /*%expand*/*
from
  employee
where
/*%if namePrefix != null */
  name like /* @prefix(namePrefix) */'A%' escape '$'
/*%end*/
/*%if minSalary != null */
  and salary >= /* minSalary */0
/*%end*/
/*%if !departmentIds.isEmpty() */
  and department_id in /* departmentIds */(1, 2)
/*%end*/
order by
  id
