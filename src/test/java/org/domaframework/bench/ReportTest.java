package org.domaframework.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTest {

  @Test
  void rateRoundsHalfToEvenLikePython() {
    // 1/8 = 12.5%, which Python formats as "12%"
    assertEquals("12%", Report.rate(List.of(true, false, false, false, false, false, false, false)));
    assertEquals("80%", Report.rate(List.of(true, true, true, true, false)));
    assertEquals("67%", Report.rate(List.of(true, true, false)));
  }

  @Test
  void rateIgnoresMissingValues() {
    assertEquals("100%", Report.rate(Arrays.asList(true, null, null)));
    assertEquals("-", Report.rate(Arrays.asList(null, null)));
  }

  @Test
  void meanFormatsLikePython() {
    assertEquals("0.4", Report.mean(List.of(0, 0, 1, 1, 0), 1));
    // 0.125 is exact in binary, so Python's "{:.2f}" rounds it half-even to "0.12"
    assertEquals("0.12", Report.mean(List.of(0.125), 2));
    assertEquals("-", Report.mean(Arrays.asList((Object) null), 2));
  }
}
