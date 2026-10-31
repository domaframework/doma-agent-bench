package bench.dao;

import bench.entity.Employee;
import java.math.BigDecimal;
import java.util.List;
import org.seasar.doma.BatchInsert;
import org.seasar.doma.BatchUpdate;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.Update;

@Dao
public interface EmployeeDao {

  @Sql("select /*%expand*/* from employee where id = /* id */0")
  @Select
  Employee selectById(Integer id);

  @Sql("select /*%expand*/* from employee order by id")
  @Select
  List<Employee> selectAll();

  @BatchInsert
  int[] insertAll(List<Employee> employees);

  @BatchUpdate
  int[] updateAll(List<Employee> employees);

  @Update(sqlFile = true)
  int raiseSalaryByDepartment(Integer departmentId, BigDecimal amount);
}
