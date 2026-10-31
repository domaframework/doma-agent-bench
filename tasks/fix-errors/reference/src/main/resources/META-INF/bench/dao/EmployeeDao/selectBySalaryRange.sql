select
  /*%expand*/*
from
  employee
where
/*%if min != null */
  salary >= /* min */0
/*%end*/
/*%if max != null */
  and salary <= /* max */0
/*%end*/
order by
  id
