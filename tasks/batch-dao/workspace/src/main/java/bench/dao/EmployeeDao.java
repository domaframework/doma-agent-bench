package bench.dao;

import bench.entity.Employee;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;

@Dao
public interface EmployeeDao {

  @Sql("select /*%expand*/* from employee where id = /* id */0")
  @Select
  Employee selectById(Integer id);

  @Sql("select /*%expand*/* from employee order by id")
  @Select
  List<Employee> selectAll();
}
