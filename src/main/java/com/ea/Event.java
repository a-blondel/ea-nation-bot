package com.ea;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class Event implements Comparable<Event> {
    private long id;
    private LocalDateTime time;
    private String message;

    @Override
    public int compareTo(Event other) {
        int timeComparison = this.time.compareTo(other.time);
        if (timeComparison != 0) {
            return timeComparison;
        }
        int disconnectedComparison = Boolean.compare(this.message.contains("disconnected"), other.message.contains("disconnected"));
        if (disconnectedComparison != 0) {
            return disconnectedComparison;
        }
        return Long.compare(this.id, other.id);
    }
}
