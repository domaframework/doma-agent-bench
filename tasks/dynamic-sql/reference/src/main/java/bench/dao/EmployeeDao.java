package bench.dao;

import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;

@Dao
public interface EmployeeDao {

  @Sql("select /*%expand*/* from employee where id = /* id */0")
  @Select
  Employee selectById(Integer id);

  @Select
  List<Employee> selectByCondition(
      String namePrefix, BigDecimal minSalary, List<Integer> departmentIds);
}
