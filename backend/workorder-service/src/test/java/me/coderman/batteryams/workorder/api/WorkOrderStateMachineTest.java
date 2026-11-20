package me.coderman.batteryams.workorder.api;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class WorkOrderStateMachineTest {
  @Test void rejectsIllegalTerminalTransitions() throws Exception {
    Method method=WorkOrderController.class.getDeclaredMethod("allowed",String.class,String.class);
    method.setAccessible(true);
    WorkOrderController controller=new WorkOrderController(null);
    assertFalse((boolean)method.invoke(controller,"COMPLETED","IN_PROGRESS"));
    assertFalse((boolean)method.invoke(controller,"CANCELLED","ASSIGNED"));
    assertTrue((boolean)method.invoke(controller,"IN_PROGRESS","WAITING_REVIEW"));
    assertTrue((boolean)method.invoke(controller,"WAITING_REVIEW","COMPLETED"));
  }
}
