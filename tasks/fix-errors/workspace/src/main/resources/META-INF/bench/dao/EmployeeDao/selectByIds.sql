select
  /*%expand*/*
from
  employee
where
/*%if !ids.isEmpty() */
  id in /* ids */(1, 2)
order by
  id
