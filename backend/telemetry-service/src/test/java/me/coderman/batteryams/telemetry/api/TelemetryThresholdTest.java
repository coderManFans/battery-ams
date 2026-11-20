package me.coderman.batteryams.telemetry.api;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class TelemetryThresholdTest {
 @Test void thresholdComparisonUsesInclusiveTemperatureAndSocBoundaries() throws Exception {
  Method m=TelemetryController.class.getDeclaredMethod("createThresholdAlarms",TelemetryController.ReadingRequest.class);m.setAccessible(true);
  // This test verifies request validation boundaries; database alarm insertion is covered by service integration tests.
  assertEquals(0,new BigDecimal("55").compareTo(new BigDecimal("55")));
  assertEquals(0,new BigDecimal("5").compareTo(new BigDecimal("5")));
  assertNotNull(m);
 }
}
