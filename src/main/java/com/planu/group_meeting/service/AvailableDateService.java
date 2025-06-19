package com.planu.group_meeting.service;

import com.planu.group_meeting.dao.AvailableDateDAO;
import com.planu.group_meeting.dto.AvailableDateDto.AvailableDatesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvailableDateService {

    private final AvailableDateDAO availableDateDAO;

    @Transactional
    public void createAvailableDates(Long userId, AvailableDatesRequest availableDatesDto) {
        List<LocalDate> requestDates = availableDatesDto.getAvailableDates();
        if (!requestDates.isEmpty()) {
            availableDateDAO.deleteAllAvailableDates(userId);
            availableDateDAO.insertAvailableDates(userId, requestDates);
        }
        else{
            availableDateDAO.deleteAllAvailableDates(userId);
        }
    }


    public List<LocalDate> findAvailableDates(Long userId, LocalDate startDate, LocalDate endDate) {
        return availableDateDAO.findAvailableDatesByUserIdInRange(userId, startDate, endDate);
    }

}
