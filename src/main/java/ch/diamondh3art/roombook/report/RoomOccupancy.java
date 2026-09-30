package ch.diamondh3art.roombook.report;

import java.math.BigDecimal;

// rate = bookedHours / availableHours, null if the period contains no business hours
public record RoomOccupancy(Long roomId, String roomName, boolean active, BigDecimal bookedHours,
                            BigDecimal availableHours, BigDecimal rate) {
}
