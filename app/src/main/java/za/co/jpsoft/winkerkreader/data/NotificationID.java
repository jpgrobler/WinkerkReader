package za.co.jpsoft.winkerkreader.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Pieter Grobler on 29/08/2017.
 */

class NotificationID {
    private final static AtomicInteger c = new AtomicInteger(0);
    public static int getID() {
        return c.incrementAndGet();
    }
}