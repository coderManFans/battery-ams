package me.coderman.batteryams.preservation.api;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
class PreservationControllerTest {
 @Test void pagingArgumentHelperAppendsParametersInOrder() throws Exception {
  Method m=PreservationController.class.getDeclaredMethod("append",Object[].class,Object[].class);m.setAccessible(true);
  Object[] result=(Object[])m.invoke(new PreservationController(null),new Object[]{new Object[]{1L,"OPEN"},new Object[]{20,0}});
  assertArrayEquals(new Object[]{1L,"OPEN",20,0},result);
 }
}
