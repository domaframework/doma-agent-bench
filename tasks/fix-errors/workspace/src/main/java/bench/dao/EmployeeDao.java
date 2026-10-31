package bench.dao;

import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;

@Dao
public interface EmployeeDao {

  @Select
  List<Employee> selectByDepartmentName(String name);

  @Select
  List<Employee> selectBySalaryRange(BigDecimal min, BigDecimal max);

  @Select
  List<Employee> selectByIds(List<Integer> ids);
}
