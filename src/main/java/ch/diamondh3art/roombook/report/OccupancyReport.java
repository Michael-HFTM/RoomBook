package ch.diamondh3art.roombook.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A7: totals for the selected location (all locations if null) and the rooms ranked by occupancy
public record OccupancyReport(LocalDate from, LocalDate to, Long locationId, BigDecimal bookedHours,
                              BigDecimal availableHours, BigDecimal rate, List<RoomOccupancy> rooms) {
}
